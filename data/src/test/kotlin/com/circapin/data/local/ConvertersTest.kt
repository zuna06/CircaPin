package com.circapin.data.local

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `dates survive a round trip`() {
        val date = LocalDate(2026, 11, 6)

        assertEquals(date, converters.epochDaysToLocalDate(converters.localDateToEpochDays(date)))
        assertNull(converters.localDateToEpochDays(null))
        assertNull(converters.epochDaysToLocalDate(null))
    }

    @Test
    fun `a 24 hour alertness curve survives a round trip`() {
        val curve = List(24) { it / 24f }

        assertEquals(curve, converters.textToFloats(converters.floatsToText(curve)))
    }

    @Test
    fun `an empty list survives a round trip`() {
        assertEquals(emptyList<Float>(), converters.textToFloats(converters.floatsToText(emptyList())))
    }
}
