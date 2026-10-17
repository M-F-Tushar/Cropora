package com.cropora

import com.cropora.database.ScanRecord
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanInsightsTest {
    private val utc = TimeZone.getTimeZone("UTC")

    /** 2024-03-15 14:00 UTC. */
    private val now = Calendar.getInstance(utc).apply {
        clear()
        set(2024, Calendar.MARCH, 15, 14, 0, 0)
    }.timeInMillis

    private fun scan(
        disease: String,
        label: String = "",
        confidence: Float = 0.9f,
        uncertain: Boolean = false,
        located: Boolean = false,
        daysAgo: Int = 0
    ) = ScanRecord(
        resultId = "$disease-$daysAgo-$confidence",
        modelLabel = label,
        disease = disease,
        confidence = confidence,
        uncertain = uncertain,
        guidanceAvailable = true,
        symptoms = "",
        treatment = "",
        prevention = "",
        latitude = if (located) 23.8 else null,
        longitude = if (located) 90.4 else null,
        timestamp = now - daysAgo * DAY
    )

    @Test
    fun emptyHistoryHasZeroedInsights() {
        val insights = ScanInsights.from(emptyList(), now, utc)
        assertEquals(0, insights.total)
        assertEquals(0.0, insights.averageConfidence, 0.0)
        assertTrue(insights.topResults.isEmpty())
        assertArrayEquals(IntArray(7), insights.lastSevenDays)
    }

    @Test
    fun countsMatchTheAnalyticsDefinitions() {
        val scans = listOf(
            scan("Tomato Early Blight", confidence = 0.8f, located = true),
            scan("Tomato Early Blight", confidence = 0.6f, uncertain = true),
            scan("Potato Late Blight", confidence = 0.7f),
            scan("Tomato Healthy", confidence = 0.9f, located = true),
            scan("Unknown", label = "Apple___healthy", confidence = 1.0f)
        )
        val insights = ScanInsights.from(scans, now, utc)

        assertEquals(5, insights.total)
        assertEquals(2, insights.healthy)
        assertEquals(3, insights.diseased)
        assertEquals(1, insights.uncertain)
        assertEquals(2, insights.withLocation)
        assertEquals(0.8, insights.averageConfidence, 1e-6)
        assertEquals(2, insights.distinctDiseases)
        assertEquals("Tomato Early Blight", insights.topResults.first().first.disease)
        assertEquals(2, insights.topResults.first().second)
        assertEquals(4, insights.topResults.size)
    }

    @Test
    fun lastSevenDaysBucketsByCalendarDayAndIgnoresOlderOrFutureScans() {
        val scans = listOf(
            scan("A", daysAgo = 0),
            scan("B", daysAgo = 0),
            scan("C", daysAgo = 1),
            scan("D", daysAgo = 6),
            scan("E", daysAgo = 7),
            scan("F", daysAgo = -1)
        )
        assertArrayEquals(
            intArrayOf(1, 0, 0, 0, 0, 1, 2),
            ScanInsights.from(scans, now, utc).lastSevenDays
        )
    }

    @Test
    fun healthyMatchesDiseaseOrModelLabel() {
        assertTrue(ScanInsights.isHealthy("Tomato Healthy", ""))
        assertTrue(ScanInsights.isHealthy("", "Corn___healthy"))
        assertFalse(ScanInsights.isHealthy("Tomato Early Blight", "Tomato___Early_blight"))
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
