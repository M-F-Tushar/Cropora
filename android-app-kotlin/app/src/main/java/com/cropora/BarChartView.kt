package com.cropora

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max

/** Compact column chart with rounded bars, day labels and the latest bar highlighted. */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * resources.displayMetrics.scaledDensity
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * resources.displayMetrics.scaledDensity
        isFakeBoldText = true
    }
    private val rect = RectF()
    private var values: IntArray = IntArray(0)
    private var labels: List<String> = emptyList()

    private val barColor = ContextCompat.getColor(context, R.color.adv_primary_muted)
    private val highlightColor = ContextCompat.getColor(context, R.color.adv_primary)
    private val emptyColor = ContextCompat.getColor(context, R.color.adv_surface_muted)
    private val labelColor = ContextCompat.getColor(context, R.color.adv_ink_faint)
    private val valueColor = ContextCompat.getColor(context, R.color.adv_ink)

    fun setData(values: IntArray, labels: List<String>) {
        require(values.size == labels.size)
        this.values = values
        this.labels = labels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return
        val labelHeight = labelPaint.textSize + 8f * density
        val valueHeight = valuePaint.textSize + 6f * density
        val chartTop = paddingTop + valueHeight
        val chartBottom = height - paddingBottom - labelHeight
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(0f)
        val slot = (width - paddingLeft - paddingRight).toFloat() / values.size
        val barWidth = (slot * 0.5f).coerceAtMost(28f * density)
        val corner = barWidth / 2f
        val maxValue = max(1, values.maxOrNull() ?: 0)
        val minBar = barWidth

        values.forEachIndexed { index, value ->
            val centerX = paddingLeft + slot * index + slot / 2f
            val barHeight = if (value == 0) minBar else max(minBar, chartHeight * value / maxValue)
            rect.set(centerX - barWidth / 2f, chartBottom - barHeight, centerX + barWidth / 2f, chartBottom)
            barPaint.color = when {
                value == 0 -> emptyColor
                index == values.lastIndex -> highlightColor
                else -> barColor
            }
            canvas.drawRoundRect(rect, corner, corner, barPaint)
            if (value > 0) {
                valuePaint.color = valueColor
                canvas.drawText(value.toString(), centerX, rect.top - 6f * density, valuePaint)
            }
            labelPaint.color = if (index == values.lastIndex) valueColor else labelColor
            labelPaint.isFakeBoldText = index == values.lastIndex
            canvas.drawText(labels[index], centerX, height - paddingBottom - 2f * density, labelPaint)
        }
    }
}
