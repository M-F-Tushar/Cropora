package com.cropora

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultTextFormatterTest {
    @Test
    fun confidencePercentUsesZeroToOneInput() {
        assertEquals(87, ResultTextFormatter.confidencePercent(0.87f))
        assertEquals(88, ResultTextFormatter.confidencePercent(0.875f))
    }

    @Test
    fun shareTextContainsGuidanceAndDisclaimerButNoLocation() {
        val text = ResultTextFormatter.shareText(
            disease = "Tomato Early Blight",
            confidence = 0.92f,
            symptoms = "Brown spots",
            treatment = "Remove affected leaves",
            prevention = "Rotate crops"
        )

        assertTrue(text.contains("Disease: Tomato Early Blight"))
        assertTrue(text.contains("Confidence: 92%"))
        assertTrue(text.contains("Model suggestion only"))
        assertFalse(text.contains("Latitude"))
        assertFalse(text.contains("Longitude"))
    }
}
