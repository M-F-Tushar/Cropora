package com.cropora

import com.cropora.database.ScanRecord
import java.util.Calendar
import java.util.TimeZone

/** Aggregates saved scans for the Normal analytics screen and Advanced insights. */
data class ScanInsights(
    val total: Int,
    val healthy: Int,
    val diseased: Int,
    val uncertain: Int,
    val withLocation: Int,
    val averageConfidence: Double,
    val distinctDiseases: Int,
    /** Most frequent results first, as a representative record and its count. */
    val topResults: List<Pair<ScanRecord, Int>>,
    /** Scans per day for the last seven days, oldest first; the last entry is today. */
    val lastSevenDays: IntArray
) {
    companion object {
        fun isHealthy(disease: String, modelLabel: String): Boolean =
            disease.contains("Healthy", ignoreCase = true) ||
                modelLabel.contains("Healthy", ignoreCase = true)

        fun from(
            scans: List<ScanRecord>,
            now: Long = System.currentTimeMillis(),
            timeZone: TimeZone = TimeZone.getDefault()
        ): ScanInsights {
            val healthy = scans.count { isHealthy(it.disease, it.modelLabel) }
            val grouped = scans.groupBy { it.disease }
            val topResults = grouped.entries
                .sortedByDescending { it.value.size }
                .map { it.value.first() to it.value.size }
            val distinctDiseases = grouped.values.count { group ->
                !isHealthy(group.first().disease, group.first().modelLabel)
            }
            return ScanInsights(
                total = scans.size,
                healthy = healthy,
                diseased = scans.size - healthy,
                uncertain = scans.count { it.uncertain },
                withLocation = scans.count { it.latitude != null && it.longitude != null },
                averageConfidence = if (scans.isEmpty()) 0.0
                    else scans.sumOf { it.confidence.toDouble() } / scans.size,
                distinctDiseases = distinctDiseases,
                topResults = topResults,
                lastSevenDays = dailyCounts(scans.map { it.timestamp }, now, timeZone)
            )
        }

        private fun dailyCounts(timestamps: List<Long>, now: Long, timeZone: TimeZone): IntArray {
            val counts = IntArray(7)
            val calendar = Calendar.getInstance(timeZone).apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayStarts = LongArray(7)
            for (index in 6 downTo 0) {
                dayStarts[index] = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            }
            timestamps.forEach { timestamp ->
                if (timestamp > now) return@forEach
                val index = dayStarts.indexOfLast { start -> timestamp >= start }
                if (index >= 0) counts[index]++
            }
            return counts
        }
    }

    override fun equals(other: Any?): Boolean = other is ScanInsights &&
        total == other.total && healthy == other.healthy && diseased == other.diseased &&
        uncertain == other.uncertain && withLocation == other.withLocation &&
        averageConfidence == other.averageConfidence && distinctDiseases == other.distinctDiseases &&
        topResults == other.topResults && lastSevenDays.contentEquals(other.lastSevenDays)

    override fun hashCode(): Int = 31 * total + lastSevenDays.contentHashCode()
}
