package com.cropora

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min

/** Ring chart with rounded segments; the caller overlays the center label. */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val bounds = RectF()
    private var segments: List<Pair<Float, Int>> = emptyList()

    var thickness: Float = 14f * density
        set(value) { field = value; invalidate() }

    var trackColor: Int = ContextCompat.getColor(context, R.color.adv_surface_muted)
        set(value) { field = value; invalidate() }

    /** Values with their colors; zero values are skipped. */
    fun setSegments(values: List<Pair<Number, Int>>) {
        segments = values.map { it.first.toFloat() to it.second }.filter { it.first > 0f }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
        val inset = thickness / 2f
        val left = paddingLeft + (width - paddingLeft - paddingRight - size) / 2f
        val top = paddingTop + (height - paddingTop - paddingBottom - size) / 2f
        bounds.set(left + inset, top + inset, left + size - inset, top + size - inset)
        paint.strokeWidth = thickness

        paint.color = trackColor
        canvas.drawArc(bounds, 0f, 360f, false, paint)

        val total = segments.sumOf { it.first.toDouble() }.toFloat()
        if (total <= 0f) return
        // Leave a visible gap between rounded caps when there is more than one segment.
        val radius = bounds.width() / 2f
        val capDegrees = if (segments.size > 1) Math.toDegrees((thickness / radius).toDouble()).toFloat() else 0f
        var start = -90f
        segments.forEach { (value, color) ->
            val sweep = value / total * 360f
            val drawn = (sweep - capDegrees).coerceAtLeast(0.5f)
            paint.color = color
            canvas.drawArc(bounds, start + capDegrees / 2f, if (segments.size == 1) 360f else drawn, false, paint)
            start += sweep
        }
    }
}
