package com.cropora

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import com.google.android.material.progressindicator.CircularProgressIndicator

internal object AdvancedResultPresentation {
    fun render(activity: Activity, confidence: Float, uncertain: Boolean, guidanceSource: CharSequence) {
        val advanced = UiPreferences.isAdvanced(activity)
        activity.findViewById<View>(R.id.visualEvidencePanel).visibility = if (advanced) View.VISIBLE else View.GONE
        if (!advanced) return
        val percentage = ResultTextFormatter.confidencePercent(confidence.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f)
        activity.findViewById<CircularProgressIndicator>(R.id.visualConfidenceRing).apply {
            progress = percentage
            setIndicatorColor(if (uncertain) 0xFF98600B.toInt() else 0xFF285BA6.toInt())
        }
        activity.findViewById<TextView>(R.id.visualConfidenceValue).apply {
            text = activity.getString(R.string.visual_confidence_value, percentage)
            contentDescription = activity.getString(R.string.confidence_format, percentage)
        }
        activity.findViewById<TextView>(R.id.visualUncertaintyStatus).apply {
            setText(if (uncertain) R.string.visual_review_required else R.string.visual_threshold_met)
            setTextColor(if (uncertain) 0xFF80500B.toInt() else 0xFF24528E.toInt())
            background = surface(this, if (uncertain) 0xFFFFF0D4.toInt() else 0xFFE8F0FF.toInt())
        }
        activity.findViewById<TextView>(R.id.visualGuidanceSource).text = guidanceSource
        activity.findViewById<WorkflowDiagramView>(R.id.visualReviewDiagram).setSteps(
            listOf(R.string.visual_step_signal, R.string.visual_step_observe, R.string.visual_step_verify, R.string.visual_step_plan)
                .map { activity.getString(it) }
        )
    }

    fun styleCare(activity: Activity, bodies: List<Int>, headings: List<Int>) {
        val advanced = UiPreferences.isAdvanced(activity)
        val surfaces = intArrayOf(0xFFE8F0FF.toInt(), 0xFFFFF0D4.toInt(), 0xFFE0F3EB.toInt())
        val accents = intArrayOf(0xFF24528E.toInt(), 0xFF80500B.toInt(), 0xFF176B57.toInt())
        val normalHeadings = listOf(R.string.symptoms_heading, R.string.treatment_heading, R.string.prevention_heading)
        val visualHeadings = listOf(R.string.visual_signs, R.string.visual_action, R.string.visual_prevention)
        bodies.forEachIndexed { index, id ->
            activity.findViewById<TextView>(id).apply {
                background = if (advanced) surface(this, surfaces[index]) else null
                val padding = if (advanced) (16 * resources.displayMetrics.density).toInt() else 0
                setPaddingRelative(padding, padding, padding, padding)
            }
            activity.findViewById<TextView>(headings[index]).apply {
                setText(if (advanced) visualHeadings[index] else normalHeadings[index])
                setTextColor(if (advanced) accents[index] else activity.getColor(R.color.leaf_green_dark))
                androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
            }
        }
    }

    private fun surface(view: View, color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = 12 * view.resources.displayMetrics.density
    }
}
