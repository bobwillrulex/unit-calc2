package com.example.unit_calc

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.tabs.TabLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val engine = UnitEngine()
    private val history = mutableListOf<String>()
    private val recentUnits = ArrayDeque<String>()
    private var lastQuantity: UnitEngine.Quantity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val formulaInput = findViewById<EditText>(R.id.formulaInput)
        val resultText = findViewById<TextView>(R.id.resultText)
        val historyButton = findViewById<ImageButton>(R.id.historyButton)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)

        formulaInput.showSoftInputOnFocus = false
        formulaInput.isCursorVisible = false
        formulaInput.setTextIsSelectable(false)

        val pages = listOf(
            findViewById<LinearLayout>(R.id.pageCalculator),
            findViewById<LinearLayout>(R.id.pageLength),
            findViewById<LinearLayout>(R.id.pageVolume)
        )

        tabLayout.removeAllTabs()
        listOf("Calculator", "Length units", "Volume units").forEach { title ->
            tabLayout.addTab(tabLayout.newTab().setText(title))
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val selectedIndex = tab.position.coerceIn(0, pages.lastIndex)
                pages.forEachIndexed { index, view ->
                    view.visibility = if (index == selectedIndex) android.view.View.VISIBLE else android.view.View.GONE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        tabLayout.getTabAt(0)?.select()

        historyButton.setOnClickListener { showHistoryDialog(formulaInput) }

        setupCalculatorButtons(formulaInput, resultText)
        setupUnitButtons(formulaInput)
        refreshRecentUnits()
    }

    private fun setupCalculatorButtons(formulaInput: EditText, resultText: TextView) {
        val ids = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5,
            R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnDot,
            R.id.btnPlus, R.id.btnMinus, R.id.btnMul, R.id.btnDiv,
            R.id.btnLParen, R.id.btnRParen, R.id.btnPow, R.id.btnSqrt, R.id.btnFrac
        )
        ids.forEach { id ->
            findViewById<Button>(id).setOnClickListener {
                val text = (it as Button).text.toString()
                val insertion = when (text) {
                    "√" -> "sqrt("
                    "×" -> "*"
                    "÷" -> "/"
                    "−" -> "-"
                    "a/b" -> "/"
                    else -> text
                }
                insertAtCursor(formulaInput, insertion)
            }
        }

        findViewById<Button>(R.id.btnAns).setOnClickListener {
            val last = resultText.text.toString().trim()
            if (last.isNotEmpty() && !last.startsWith("Error")) insertAtCursor(formulaInput, "($last)")
        }

        findViewById<Button>(R.id.btnEq).setOnClickListener {
            try {
                val eval = engine.evaluate(formulaInput.text.toString())
                lastQuantity = eval.quantity
                resultText.text = eval.display
                history.add(0, "${formulaInput.text} = ${eval.display}")
                if (history.size > 30) history.removeLast()
                updateConversionButton(eval.quantity, resultText)
            } catch (e: Exception) {
                resultText.text = "Error: ${e.message}"
            }
        }

        findViewById<Button>(R.id.btnAc).setOnClickListener {
            formulaInput.setText("")
            resultText.text = ""
            lastQuantity = null
        }

        findViewById<Button>(R.id.btnDel).setOnClickListener {
            deleteSmart(formulaInput)
        }
    }

    private fun updateConversionButton(quantity: UnitEngine.Quantity, resultText: TextView) {
        findViewById<Button>(R.id.btnConvert).setOnClickListener {
            val options = engine.availableUnitsFor(quantity)
            if (options.isEmpty()) {
                Toast.makeText(this, "Nothing to convert", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("Convert to")
                .setItems(options.toTypedArray()) { _, which ->
                    try {
                        resultText.text = engine.convert(quantity, options[which])
                    } catch (e: Exception) {
                        resultText.text = "Error: ${e.message}"
                    }
                }
                .show()
        }
    }

    private fun setupUnitButtons(formulaInput: EditText) {
        val lengthUnits = listOf("mm", "cm", "m", "km", "^2", "^3")
        val volumeUnits = listOf("mL", "L")

        lengthUnits.forEachIndexed { i, text ->
            val button = findViewById<Button>(resources.getIdentifier("lenBtn$i", "id", packageName))
            button.text = text
            button.setOnClickListener {
                insertAtCursor(formulaInput, text)
                if (text.first().isLetter()) addRecentUnit(text)
            }
        }

        volumeUnits.forEachIndexed { i, text ->
            val button = findViewById<Button>(resources.getIdentifier("volBtn$i", "id", packageName))
            button.text = text
            button.setOnClickListener {
                insertAtCursor(formulaInput, text)
                addRecentUnit(text)
            }
        }
    }

    private fun addRecentUnit(unit: String) {
        recentUnits.remove(unit)
        recentUnits.addFirst(unit)
        while (recentUnits.size > 6) recentUnits.removeLast()
        refreshRecentUnits()
    }

    private fun refreshRecentUnits() {
        val container = findViewById<LinearLayout>(R.id.recentUnitContainer)
        container.removeAllViews()
        recentUnits.forEach { unit ->
            val b = Button(this).apply {
                text = unit
                setOnClickListener {
                    insertAtCursor(findViewById(R.id.formulaInput), unit)
                }
            }
            container.addView(b)
        }
        container.invalidate()
        findViewById<HorizontalScrollView>(R.id.recentUnitsScroll).fullScroll(HorizontalScrollView.FOCUS_LEFT)
    }

    private fun showHistoryDialog(formulaInput: EditText) {
        if (history.isEmpty()) {
            Toast.makeText(this, "No history yet", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("History")
            .setItems(history.toTypedArray()) { _, which ->
                val line = history[which]
                val expr = line.substringBefore("=").trim()
                formulaInput.setText(expr)
                formulaInput.setSelection(expr.length)
            }
            .show()
    }

    private fun insertAtCursor(editText: EditText, value: String) {
        val start = editText.selectionStart.coerceAtLeast(0)
        editText.text.insert(start, value)
        editText.setSelection(start + value.length)
    }

    private fun deleteSmart(editText: EditText) {
        val cursor = editText.selectionStart
        if (cursor <= 0) return
        val text = editText.text.toString()
        val prev = text[cursor - 1]
        if (prev.isLetter()) {
            var start = cursor - 1
            while (start > 0 && text[start - 1].isLetter()) start--
            editText.text.delete(start, cursor)
            editText.setSelection(start)
        } else {
            editText.text.delete(cursor - 1, cursor)
            editText.setSelection(cursor - 1)
        }
    }
}
