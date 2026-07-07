package com.horizon.coparentinglog

import com.horizon.coparentinglog.core.MessageNormalization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageNormalizationTest {
    @Test
    fun normalizesTenDigitUsNumber() {
        assertEquals("+15551234567", MessageNormalization.normalize("(555) 123-4567"))
    }

    @Test
    fun normalizesElevenDigitUsNumber() {
        assertEquals("+15551234567", MessageNormalization.normalize("1-555-123-4567"))
    }

    @Test
    fun comparesSameNumber() {
        assertTrue(MessageNormalization.areSameNumber("(555) 123-4567", "+1 555 123 4567"))
    }
}

