package com.example.unit_calc

import kotlin.math.pow
import kotlin.math.sqrt

class UnitEngine {
    enum class Dim { LENGTH, VOLUME }

    data class UnitDef(
        val symbol: String,
        val dim: Dim,
        val factorToBase: Double
    )

    data class Quantity(
        val valueInBase: Double,
        val dims: Map<Dim, Int>,
        val preferredUnits: Map<Dim, String> = emptyMap()
    )

    data class EvalResult(val quantity: Quantity, val display: String)

    private val units = mapOf(
        "mm" to UnitDef("mm", Dim.LENGTH, 1.0),
        "cm" to UnitDef("cm", Dim.LENGTH, 10.0),
        "m" to UnitDef("m", Dim.LENGTH, 1000.0),
        "km" to UnitDef("km", Dim.LENGTH, 1_000_000.0),
        "ml" to UnitDef("mL", Dim.VOLUME, 1.0),
        "mL" to UnitDef("mL", Dim.VOLUME, 1.0),
        "l" to UnitDef("L", Dim.VOLUME, 1000.0),
        "L" to UnitDef("L", Dim.VOLUME, 1000.0)
    )

    fun evaluate(expression: String): EvalResult {
        val parser = Parser(tokenize(expression))
        val quantity = parser.parseExpression()
        parser.expect(TokenType.EOF)
        return EvalResult(quantity, formatQuantity(quantity))
    }

    fun convert(quantity: Quantity, targetUnitSymbol: String): String {
        if (quantity.dims.size != 1) throw IllegalArgumentException("Only single-dimension results can be converted")
        val dim = quantity.dims.keys.first()
        val exp = quantity.dims.values.first()
        val unitDef = units[targetUnitSymbol] ?: throw IllegalArgumentException("Unknown target unit")
        require(unitDef.dim == dim) { "Invalid target unit type" }

        val normalized = quantity.valueInBase / unitDef.factorToBase.pow(exp)
        val pref = quantity.preferredUnits.toMutableMap()
        pref[dim] = unitDef.symbol
        return formatQuantity(Quantity(normalized * unitDef.factorToBase.pow(exp), quantity.dims, pref))
    }

    fun availableUnitsFor(quantity: Quantity): List<String> {
        if (quantity.dims.size != 1) return emptyList()
        val dim = quantity.dims.keys.first()
        return units.values.filter { it.dim == dim }.map { it.symbol }.distinct()
    }

    private fun formatQuantity(quantity: Quantity): String {
        val dims = quantity.dims.filterValues { it != 0 }
        if (dims.isEmpty()) return formatNumber(quantity.valueInBase)

        var numeric = quantity.valueInBase
        val unitParts = mutableListOf<String>()
        for ((dim, exp) in dims) {
            val preferred = quantity.preferredUnits[dim]
            val unitDef = if (preferred != null) units[preferred] else defaultUnitForDim(dim)
            val factor = unitDef!!.factorToBase.pow(exp)
            numeric /= factor
            unitParts += if (exp == 1) unitDef.symbol else "${unitDef.symbol}^$exp"
        }
        return "${formatNumber(numeric)} ${unitParts.joinToString("*")}"
    }

    private fun defaultUnitForDim(dim: Dim): UnitDef? = when (dim) {
        Dim.LENGTH -> units["mm"]
        Dim.VOLUME -> units["mL"]
    }

    private fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) throw IllegalArgumentException("Invalid numeric result")
        val asLong = value.toLong()
        return if (value == asLong.toDouble()) asLong.toString() else "%.8f".format(value).trimEnd('0').trimEnd('.')
    }

    private enum class TokenType { NUMBER, UNIT, PLUS, MINUS, MUL, DIV, POW, LPAREN, RPAREN, SQRT, EOF }
    private data class Token(val type: TokenType, val text: String)

    private fun tokenize(input: String): List<Token> {
        val raw = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    i++
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++
                    raw += Token(TokenType.NUMBER, input.substring(start, i))
                }
                c.isLetter() -> {
                    val start = i
                    i++
                    while (i < input.length && input[i].isLetter()) i++
                    val word = input.substring(start, i)
                    if (word.equals("sqrt", true)) raw += Token(TokenType.SQRT, word)
                    else raw += Token(TokenType.UNIT, word)
                }
                c == '+' -> { raw += Token(TokenType.PLUS, "+"); i++ }
                c == '-' -> { raw += Token(TokenType.MINUS, "-"); i++ }
                c == '*' -> { raw += Token(TokenType.MUL, "*"); i++ }
                c == '/' -> { raw += Token(TokenType.DIV, "/"); i++ }
                c == '^' -> { raw += Token(TokenType.POW, "^"); i++ }
                c == '(' -> { raw += Token(TokenType.LPAREN, "("); i++ }
                c == ')' -> { raw += Token(TokenType.RPAREN, ")"); i++ }
                else -> throw IllegalArgumentException("Unexpected character: $c")
            }
        }

        val final = mutableListOf<Token>()
        for (idx in raw.indices) {
            val curr = raw[idx]
            final += curr
            if (idx == raw.lastIndex) continue
            val next = raw[idx + 1]
            if (needsImplicitMultiply(curr.type, next.type)) final += Token(TokenType.MUL, "*")
        }
        final += Token(TokenType.EOF, "")
        return final
    }

    private fun needsImplicitMultiply(a: TokenType, b: TokenType): Boolean {
        val left = a == TokenType.NUMBER || a == TokenType.UNIT || a == TokenType.RPAREN
        val right = b == TokenType.NUMBER || b == TokenType.UNIT || b == TokenType.LPAREN || b == TokenType.SQRT
        return left && right
    }

    private inner class Parser(private val tokens: List<Token>) {
        private var pos = 0
        private fun peek(): Token = tokens[pos]
        private fun consume(): Token = tokens[pos++]

        fun expect(type: TokenType) {
            if (peek().type != type) throw IllegalArgumentException("Expected $type but got ${peek().type}")
            consume()
        }

        fun parseExpression(): Quantity {
            var left = parseTerm()
            while (peek().type == TokenType.PLUS || peek().type == TokenType.MINUS) {
                val op = consume().type
                val right = parseTerm()
                if (left.dims != right.dims) throw IllegalArgumentException("Cannot add/subtract different unit types")
                left = if (op == TokenType.PLUS) left.copy(valueInBase = left.valueInBase + right.valueInBase)
                else left.copy(valueInBase = left.valueInBase - right.valueInBase)
            }
            return left
        }

        fun parseTerm(): Quantity {
            var left = parsePower()
            while (peek().type == TokenType.MUL || peek().type == TokenType.DIV) {
                val op = consume().type
                val right = parsePower()
                left = if (op == TokenType.MUL) multiply(left, right) else divide(left, right)
            }
            return left
        }

        fun parsePower(): Quantity {
            var base = parseUnary()
            if (peek().type == TokenType.POW) {
                consume()
                val exponentToken = consume()
                if (exponentToken.type != TokenType.NUMBER) throw IllegalArgumentException("Exponent must be a number")
                val exp = exponentToken.text.toIntOrNull() ?: throw IllegalArgumentException("Exponent must be integer")
                base = power(base, exp)
            }
            return base
        }

        fun parseUnary(): Quantity = when (peek().type) {
            TokenType.MINUS -> {
                consume()
                val q = parseUnary()
                q.copy(valueInBase = -q.valueInBase)
            }

            TokenType.SQRT -> {
                consume()
                expect(TokenType.LPAREN)
                val q = parseExpression()
                expect(TokenType.RPAREN)
                sqrtQuantity(q)
            }

            else -> parsePrimary()
        }

        fun parsePrimary(): Quantity {
            val t = consume()
            return when (t.type) {
                TokenType.NUMBER -> Quantity(t.text.toDouble(), emptyMap())
                TokenType.UNIT -> parseUnit(t.text)
                TokenType.LPAREN -> {
                    val q = parseExpression()
                    expect(TokenType.RPAREN)
                    q
                }

                else -> throw IllegalArgumentException("Unexpected token: ${t.type}")
            }
        }

        private fun parseUnit(text: String): Quantity {
            val unit = units[text] ?: throw IllegalArgumentException("Unknown unit: $text")
            return Quantity(unit.factorToBase, mapOf(unit.dim to 1), mapOf(unit.dim to unit.symbol))
        }

        private fun multiply(a: Quantity, b: Quantity): Quantity {
            val dims = mergeDims(a.dims, b.dims, 1)
            return Quantity(a.valueInBase * b.valueInBase, dims, a.preferredUnits + b.preferredUnits)
        }

        private fun divide(a: Quantity, b: Quantity): Quantity {
            if (b.valueInBase == 0.0) throw IllegalArgumentException("Division by zero")
            val dims = mergeDims(a.dims, b.dims, -1)
            return Quantity(a.valueInBase / b.valueInBase, dims, a.preferredUnits)
        }

        private fun power(a: Quantity, exp: Int): Quantity {
            val dims = a.dims.mapValues { it.value * exp }.filterValues { it != 0 }
            return Quantity(a.valueInBase.pow(exp), dims, a.preferredUnits)
        }

        private fun sqrtQuantity(a: Quantity): Quantity {
            val dims = mutableMapOf<Dim, Int>()
            for ((d, p) in a.dims) {
                if (p % 2 != 0) throw IllegalArgumentException("sqrt requires even unit powers")
                dims[d] = p / 2
            }
            return Quantity(sqrt(a.valueInBase), dims.filterValues { it != 0 }, a.preferredUnits)
        }

        private fun mergeDims(left: Map<Dim, Int>, right: Map<Dim, Int>, sign: Int): Map<Dim, Int> {
            val out = left.toMutableMap()
            for ((d, p) in right) {
                out[d] = (out[d] ?: 0) + sign * p
                if (out[d] == 0) out.remove(d)
            }
            return out
        }
    }
}
