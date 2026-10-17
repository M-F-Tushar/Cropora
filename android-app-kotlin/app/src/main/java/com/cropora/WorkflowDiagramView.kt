package com.cropora

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import java.text.NumberFormat

/** Four-stage reading order: across the first row, then back across the second. */
class WorkflowDiagramView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val drawingPath = Path()
    private val shapeBounds = RectF()
    private val dashedConnector = android.graphics.DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
    private var measuredLabels = emptyList<StaticLayout>()
    private var stepNumbers = emptyList<String>()
    private var labels = emptyList<String>()
    private var activeStep = -1
    private val accents = intArrayOf(0xFF176B57.toInt(), 0xFF285BA6.toInt(), 0xFF7450A1.toInt(), 0xFF98600B.toInt())
    private val surfaces = intArrayOf(0xFFE0F3EB.toInt(), 0xFFE8F0FF.toInt(), 0xFFF1EAFE.toInt(), 0xFFFFF0D4.toInt())
    private fun dp(value: Float) = value * resources.displayMetrics.density

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = false
    }

    fun setSteps(labels: List<String>, activeStep: Int = -1) {
        require(labels.size == 4) { "A workflow diagram requires four stages" }
        if (this.labels == labels && this.activeStep == activeStep) return
        this.labels = labels.toList()
        stepNumbers = labels.indices.map { NumberFormat.getIntegerInstance(resources.configuration.locales[0]).format(it + 1) }
        this.activeStep = activeStep.takeIf { it in labels.indices } ?: -1
        contentDescription = labels.mapIndexed { index, label ->
            val numbered = "${NumberFormat.getIntegerInstance(resources.configuration.locales[0]).format(index + 1)}. $label"
            if (index == this.activeStep) context.getString(R.string.visual_current_stage, numbered) else numbered
        }.joinToString(". ")
        requestLayout()
        invalidate()
    }

    private fun labelLayout(label: String, columnWidth: Int): StaticLayout {
        textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
        textPaint.color = 0xFF263D4D.toInt()
        textPaint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        return StaticLayout.Builder.obtain(label, 0, label.length, textPaint, columnWidth.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true).build()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = dp(300f).toInt() + paddingLeft + paddingRight
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val columnWidth = ((width - paddingLeft - paddingRight) / 2 - dp(12f)).toInt()
        measuredLabels = labels.map { labelLayout(it, columnWidth) }
        val labelHeight = measuredLabels.maxOfOrNull { it.height } ?: dp(20f).toInt()
        val desiredHeight = (2 * (dp(70f) + labelHeight) + paddingTop + paddingBottom).toInt()
        // XML uses a nominal height; grow with translated labels and the system font scale.
        setMeasuredDimension(width, maxOf(resolveSize(desiredHeight, heightMeasureSpec), desiredHeight))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (measuredLabels.size != 4) return
        val width = (width - paddingLeft - paddingRight).toFloat()
        val column = width / 2f
        val labelWidth = (column - dp(12f)).toInt()
        val layouts = measuredLabels
        val rowHeight = dp(70f) + layouts.maxOf { it.height }
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val left = paddingLeft + column / 2f
        val right = paddingLeft + column * 1.5f
        val xs = if (rtl) floatArrayOf(right, left, left, right) else floatArrayOf(left, right, right, left)
        val ys = floatArrayOf(paddingTop + dp(27f), paddingTop + dp(27f), paddingTop + rowHeight + dp(27f), paddingTop + rowHeight + dp(27f))
        paint.color = 0xFFB9C8D3.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.5f)
        paint.pathEffect = dashedConnector
        for (i in 0..2) {
            if (i == 1) {
                val gutter = if (rtl) paddingLeft + dp(5f) else paddingLeft + width - dp(5f)
                val path = drawingPath.apply {
                    rewind()
                    moveTo(xs[i], ys[i]); lineTo(gutter, ys[i]); lineTo(gutter, ys[i + 1]); lineTo(xs[i + 1], ys[i + 1])
                }
                canvas.drawPath(path, paint)
            } else canvas.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1], paint)
        }
        paint.pathEffect = null
        for (index in 0..2) {
            val direction = if (index == 1) {
                if (rtl) 1f else -1f
            } else if (xs[index + 1] > xs[index]) 1f else -1f
            val tipX = xs[index + 1] - direction * dp(28f)
            val tipY = ys[index + 1]
            val arrow = drawingPath.apply {
                rewind()
                moveTo(tipX - direction * dp(6f), tipY - dp(4f))
                lineTo(tipX, tipY)
                lineTo(tipX - direction * dp(6f), tipY + dp(4f))
            }
            canvas.drawPath(arrow, paint)
        }
        for (index in labels.indices) {
            val x = xs[index]
            val y = ys[index]
            val radius = dp(22f)
            val shape = drawingPath.apply {
                rewind()
                when (index) {
                    0 -> addCircle(x, y, radius, Path.Direction.CW)
                    1 -> {
                        shapeBounds.set(x - radius, y - radius, x + radius, y + radius)
                        addRoundRect(shapeBounds, dp(8f), dp(8f), Path.Direction.CW)
                    }
                    2 -> { moveTo(x, y - radius - dp(2f)); lineTo(x + radius + dp(2f), y); lineTo(x, y + radius + dp(2f)); lineTo(x - radius - dp(2f), y); close() }
                    else -> { moveTo(x - radius / 2, y - radius); lineTo(x + radius / 2, y - radius); lineTo(x + radius, y); lineTo(x + radius / 2, y + radius); lineTo(x - radius / 2, y + radius); lineTo(x - radius, y); close() }
                }
            }
            paint.style = Paint.Style.FILL
            paint.color = if (index == activeStep) accents[index] else surfaces[index]
            canvas.drawPath(shape, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(if (index == activeStep) 2.5f else 1f)
            paint.color = accents[index]
            canvas.drawPath(shape, paint)
            paint.style = Paint.Style.FILL
            paint.color = if (index == activeStep) Color.WHITE else accents[index]
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics).coerceAtMost(dp(21f))
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            val number = stepNumbers[index]
            canvas.drawText(number, x, y - (paint.ascent() + paint.descent()) / 2f, paint)
            canvas.save()
            canvas.translate(x - labelWidth / 2f, y + dp(28f))
            layouts[index].draw(canvas)
            canvas.restore()
        }
    }
}
