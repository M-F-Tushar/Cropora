package com.cropora

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.cropora.data.DiseasePresentation
import com.cropora.database.ScanRecord
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.SimpleDateFormat
import java.util.Calendar
import kotlin.math.roundToInt

class AdvancedInsightsFragment : AdvancedTabFragment(R.layout.fragment_adv_insights) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AdvancedUi.bindEmpty(
            view.findViewById(R.id.advInsightsEmpty), R.drawable.ic_analytics,
            R.string.adv_insights_empty_title, R.string.adv_insights_empty_body
        ) { navigator.startScan() }
        observeScans(::render)
    }

    private fun render(scans: List<ScanRecord>) {
        val view = view ?: return
        val context = requireContext()
        val empty = scans.isEmpty()
        view.findViewById<View>(R.id.advInsightsContent).visibility = if (empty) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.advInsightsEmpty).visibility = if (empty) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.advInsightsSubtitle).visibility = if (empty) View.GONE else View.VISIBLE
        if (empty) return

        val insights = ScanInsights.from(scans)
        view.findViewById<TextView>(R.id.advInsightsSubtitle).text =
            resources.getQuantityString(R.plurals.adv_insights_subtitle, insights.total, insights.total)

        // Health split
        view.findViewById<TextView>(R.id.advInsightsTotal).text = insights.total.toString()
        AdvancedUi.bindHealth(
            view.findViewById(R.id.advInsightsDonut),
            listOf(R.id.advInsightsLegendHealthy, R.id.advInsightsLegendDiseased, R.id.advInsightsLegendReview)
                .map { view.findViewById(it) },
            AdvancedUi.statusCounts(scans)
        )

        // Metrics
        val average = insights.averageConfidence.toFloat()
        view.findViewById<CircularProgressIndicator>(R.id.advInsightsAvgRing).progress =
            ResultTextFormatter.confidencePercent(average.coerceIn(0f, 1f))
        view.findViewById<TextView>(R.id.advInsightsAvgValue).text = AdvancedUi.percent(context, average)
        bindMetric(view.findViewById(R.id.advInsightsReview), R.drawable.adv_ic_warning,
            R.color.adv_warn, R.color.adv_warn_soft, insights.uncertain, R.string.adv_needs_review)
        bindMetric(view.findViewById(R.id.advInsightsLocation), R.drawable.adv_ic_location,
            R.color.adv_info, R.color.adv_info_soft, insights.withLocation, R.string.adv_with_location)
        bindMetric(view.findViewById(R.id.advInsightsDiseases), R.drawable.adv_ic_label,
            R.color.adv_violet, R.color.adv_violet_soft, insights.distinctDiseases, R.string.adv_conditions_found)

        // Most common
        val (topRecord, topCount) = insights.topResults.first()
        val topName = DiseasePresentation.name(context, topRecord.disease, topRecord.modelLabel)
        AdvancedUi.bindAvatar(view.findViewById(R.id.advInsightsTopAvatar), topName)
        view.findViewById<TextView>(R.id.advInsightsTopName).text = topName
        view.findViewById<TextView>(R.id.advInsightsTopCount).apply {
            text = resources.getQuantityString(R.plurals.adv_scans_count, topCount, topCount)
            AdvancedUi.stylePill(this, R.color.adv_primary, R.color.adv_primary_soft)
        }

        bindWeek(view, insights.lastSevenDays)
        bindTopList(view.findViewById(R.id.advInsightsTopList), insights)
    }

    private fun bindMetric(
        root: View,
        @DrawableRes icon: Int,
        @ColorRes color: Int,
        @ColorRes soft: Int,
        value: Int,
        @StringRes label: Int
    ) {
        val context = root.context
        root.findViewById<View>(R.id.advMetricBadge).backgroundTintList =
            ColorStateList.valueOf(AdvancedUi.color(context, soft))
        root.findViewById<ImageView>(R.id.advMetricIcon).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(AdvancedUi.color(context, color))
        }
        root.findViewById<TextView>(R.id.advMetricValue).text = value.toString()
        root.findViewById<TextView>(R.id.advMetricLabel).setText(label)
    }

    private fun bindWeek(view: View, days: IntArray) {
        val locale = resources.configuration.locales[0]
        val shortFormat = SimpleDateFormat("EEEEE", locale)
        val longFormat = SimpleDateFormat("EEEE", locale)
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(days.size - 1)) }
        val shortLabels = mutableListOf<String>()
        val spoken = mutableListOf<String>()
        days.forEach { count ->
            shortLabels += shortFormat.format(calendar.time)
            spoken += getString(R.string.adv_chart_day_value, longFormat.format(calendar.time), count)
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        view.findViewById<BarChartView>(R.id.advInsightsBars).apply {
            setData(days, shortLabels)
            contentDescription = spoken.joinToString(", ")
        }
        val weekTotal = days.sum()
        view.findViewById<TextView>(R.id.advInsightsWeekCount).apply {
            text = resources.getQuantityString(R.plurals.adv_week_count, weekTotal, weekTotal)
            AdvancedUi.stylePill(this, R.color.adv_info, R.color.adv_info_soft)
        }
    }

    private fun bindTopList(container: LinearLayout, insights: ScanInsights) {
        val context = container.context
        container.removeAllViews()
        val inflater = LayoutInflater.from(context)
        insights.topResults.take(TOP_COUNT).forEach { (record, count) ->
            val share = (count * 100f / insights.total).roundToInt()
            val row = inflater.inflate(R.layout.item_adv_top_result, container, false)
            row.findViewById<TextView>(R.id.advTopName).text =
                DiseasePresentation.name(context, record.disease, record.modelLabel)
            row.findViewById<TextView>(R.id.advTopCount).text = getString(R.string.adv_percent, share)
            row.findViewById<LinearProgressIndicator>(R.id.advTopProgress).apply {
                progress = share
                val color = if (ScanInsights.isHealthy(record.disease, record.modelLabel)) {
                    R.color.adv_healthy
                } else {
                    R.color.adv_danger
                }
                setIndicatorColor(AdvancedUi.color(context, color))
            }
            container.addView(row)
        }
    }

    private companion object {
        const val TOP_COUNT = 5
    }
}
