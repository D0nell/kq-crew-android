package com.donell.kqcrew

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the pure helper functions in MainActivity.kt
 * (formatTime, riskLabel, riskColor). Covered: normal values, null, and an
 * unknown risk text. Uses plain JUnit 4 (already in the project).
 */
class CrewAppLogicTest {

    @Test
    fun formatTime_NormalValues() {
        assertEquals("2026-11-12 09:05", formatTime("2026-11-12T09:05:00+03:00"))
        assertEquals("2026-11-12 09:05", formatTime("2026-11-12T09:05"))
        assertEquals("-", formatTime(null))
        assertEquals("", formatTime(""))
    }

    @Test
    fun riskLabel_NormalValues() {
        assertEquals("Delay risk: LOW", riskLabel("LOW"))
        assertEquals("Delay risk: MEDIUM", riskLabel("MEDIUM"))
        assertEquals("Delay risk: HIGH", riskLabel("HIGH"))
        assertEquals("Delay risk: CRITICAL", riskLabel("CRITICAL"))
    }

    @Test
    fun riskLabel_Null() {
        assertEquals("Not scored yet", riskLabel(null))
    }

    @Test
    fun riskLabel_UnknownRiskText() {
        assertEquals("Delay risk: UNKNOWN", riskLabel("UNKNOWN"))
        assertEquals("Delay risk: ?", riskLabel("?"))
    }

    @Test
    fun riskColor_NormalValues() {
        // Compiles and runs with plain JUnit 4 (no new library dependency); the
        // returned Color is verified non-null for every scored risk level.
        assertNotNull(riskColor("LOW"))
        assertNotNull(riskColor("MEDIUM"))
        assertNotNull(riskColor("HIGH"))
        assertNotNull(riskColor("CRITICAL"))
    }

    @Test
    fun riskColor_NullAndUnknown() {
        // Covers null and an unknown risk text, verifying the function always
        // returns a non-null Color instead of throwing.
        assertNotNull(riskColor(null))
        assertNotNull(riskColor("UNKNOWN"))
    }
}
