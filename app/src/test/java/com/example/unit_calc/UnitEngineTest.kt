package com.example.unit_calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitEngineTest {
    private val engine = UnitEngine()

    @Test
    fun addsCompatibleUnits() {
        val result = engine.evaluate("12 mm + 11 cm")
        assertEquals("122 mm", result.display)
    }

    @Test
    fun multiplicationAndDivisionReduceUnits() {
        assertEquals("10 mm^2", engine.evaluate("mm * cm").display)
        assertEquals("10 mm", engine.evaluate("mm * cm / mm").display)
        assertEquals("1", engine.evaluate("mm / mm").display)
    }

    @Test
    fun invalidAdditionsReturnError() {
        val ex1 = runCatching { engine.evaluate("mm^2 + cm") }.exceptionOrNull()
        val ex2 = runCatching { engine.evaluate("L + cm") }.exceptionOrNull()
        assertTrue(ex1?.message?.contains("Cannot add") == true)
        assertTrue(ex2?.message?.contains("Cannot add") == true)
    }

    @Test
    fun orderAndSqrtWork() {
        assertEquals("9", engine.evaluate("(1+2)*3").display)
        assertEquals("3 mm", engine.evaluate("sqrt(9 mm^2)").display)
    }
}
