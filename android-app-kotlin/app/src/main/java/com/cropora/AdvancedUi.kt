package com.cropora

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.cropora.database.ScanRecord
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.text.BreakIterator
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.abs

/** Visual building blocks shared by the Advanced screens. */
internal object AdvancedUi {

    enum class Status(@StringRes val label: Int, @ColorRes val color: Int, @ColorRes val soft: Int) {
        HEALTHY(R.string.adv_status_healthy, R.color.adv_healthy, R.color.adv_healthy_soft),
        DISEASE(R.string.adv_status_disease, R.color.adv_danger, R.color.adv_danger_soft),
        REVIEW(R.string.adv_status_review, R.color.adv_warn, R.color.adv_warn_soft)
    }

    fun status(disease: String, modelLabel: String, uncertain: Boolean): Status = when {
        uncertain -> Status.REVIEW
        ScanInsights.isHealthy(disease, modelLabel) -> Status.HEALTHY
        else -> Status.DISEASE
    }

    fun status(record: ScanRecord): Status = status(record.disease, record.modelLabel, record.uncertain)

    /** Counts per status; every scan lands in exactly one bucket. */
    fun statusCounts(scans: List<ScanRecord>): Map<Status, Int> =
        Status.values().associateWith { status -> scans.count { status(it) == status } }

    fun color(context: Context, @ColorRes color: Int): Int = ContextCompat.getColor(context, color)

    /** Fills the health donut and its three legend rows (healthy, diseased, review). */
    fun bindHealth(donut: DonutChartView, legends: List<View>, counts: Map<Status, Int>) {
        val context = donut.context
        val healthy = counts[Status.HEALTHY] ?: 0
        val diseased = counts[Status.DISEASE] ?: 0
        val review = counts[Status.REVIEW] ?: 0
        donut.setSegments(
            listOf(
                healthy to color(context, Status.HEALTHY.color),
                diseased to color(context, Status.DISEASE.color),
                review to color(context, Status.REVIEW.color)
            )
        )
        donut.contentDescription = context.getString(R.string.adv_health_summary_desc, healthy, diseased, review)
        val rows = listOf(
            Triple(R.string.adv_healthy, Status.HEALTHY.color, healthy),
            Triple(R.string.adv_diseased, Status.DISEASE.color, diseased),
            Triple(R.string.adv_needs_review, Status.REVIEW.color, review)
        )
        legends.zip(rows).forEach { (legend, row) ->
            legend.findViewById<View>(R.id.advLegendDot).backgroundTintList =
                ColorStateList.valueOf(color(context, row.second))
            legend.findViewById<TextView>(R.id.advLegendLabel).setText(row.first)
            legend.findViewById<TextView>(R.id.advLegendValue).text = row.third.toString()
        }
    }

    /** Shared empty state (view_adv_empty). Pass a null action to hide the button. */
    fun bindEmpty(
        root: View,
        @DrawableRes icon: Int,
        @StringRes title: Int,
        @StringRes body: Int?,
        action: (() -> Unit)?
    ) {
        root.findViewById<ImageView>(R.id.advEmptyIcon).setImageResource(icon)
        root.findViewById<TextView>(R.id.advEmptyTitle).setText(title)
        root.findViewById<TextView>(R.id.advEmptyBody).apply {
            if (body != null) setText(body)
            visibility = if (body != null) View.VISIBLE else View.GONE
        }
        root.findViewById<View>(R.id.advEmptyAction).apply {
            visibility = if (action != null) View.VISIBLE else View.GONE
            setOnClickListener { action?.invoke() }
        }
    }

    fun openScanRecord(context: Context, record: ScanRecord) {
        context.startActivity(
            Intent(context, HistoryDetailActivity::class.java)
                .putExtra(HistoryDetailActivity.EXTRA_SCAN_ID, record.id)
        )
    }

    /** Rounded pill with a leading status dot. */
    fun stylePill(view: TextView, @ColorRes color: Int, @ColorRes soft: Int) {
        val context = view.context
        view.background = GradientDrawable().apply {
            cornerRadius = dp(context, 100f)
            setColor(ContextCompat.getColor(context, soft))
        }
        view.setTextColor(ContextCompat.getColor(context, color))
        TextViewCompat.setCompoundDrawableTintList(
            view, ColorStateList.valueOf(ContextCompat.getColor(context, color))
        )
    }

    fun bindStatusPill(view: TextView, status: Status) {
        view.setText(status.label)
        stylePill(view, status.color, status.soft)
    }

    /** Library badge: "Healthy" for healthy reference entries, "Disease" otherwise. */
    fun bindDiseaseBadge(view: TextView, canonicalName: String) {
        if (ScanInsights.isHealthy(canonicalName, "")) {
            view.setText(R.string.adv_healthy)
            stylePill(view, Status.HEALTHY.color, Status.HEALTHY.soft)
        } else {
            view.setText(R.string.adv_badge_disease)
            stylePill(view, Status.DISEASE.color, Status.DISEASE.soft)
        }
        view.visibility = View.VISIBLE
    }

    fun tintRing(ring: CircularProgressIndicator, status: Status) {
        val context = ring.context
        ring.setIndicatorColor(ContextCompat.getColor(context, status.color))
        ring.trackColor = ContextCompat.getColor(context, status.soft)
    }

    fun percent(context: Context, confidence: Float): String = context.getString(
        R.string.adv_percent,
        ResultTextFormatter.confidencePercent(confidence.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f)
    )

    private val avatarColors = intArrayOf(
        R.color.adv_primary, R.color.adv_info, R.color.adv_violet,
        R.color.adv_warn, R.color.adv_healthy, R.color.adv_danger
    )

    /** Circular avatar showing the first character of the crop name in a stable color. */
    fun bindAvatar(view: TextView, plant: String) {
        val context = view.context
        view.text = firstGrapheme(plant).uppercase()
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(context, avatarColors[abs(plant.lowercase().hashCode()) % avatarColors.size]))
        }
    }

    private fun firstGrapheme(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return "?"
        val iterator = BreakIterator.getCharacterInstance().apply { setText(trimmed) }
        return trimmed.substring(0, iterator.next().coerceAtLeast(1))
    }

    @StringRes
    fun greeting(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): Int = when (hour) {
        in 5..11 -> R.string.adv_greeting_morning
        in 12..16 -> R.string.adv_greeting_afternoon
        else -> R.string.adv_greeting_evening
    }

    fun dateTime(context: Context, timestamp: Long, dateStyle: Int = DateFormat.MEDIUM): String =
        DateFormat.getDateTimeInstance(dateStyle, DateFormat.SHORT, context.resources.configuration.locales[0])
            .format(Date(timestamp))

    fun serverHost(baseUrl: String): String {
        val uri = Uri.parse(baseUrl)
        val host = uri.host ?: return baseUrl
        return if (uri.port > 0) "$host:${uri.port}" else host
    }

    fun dp(context: Context, value: Float): Float = value * context.resources.displayMetrics.density
}
