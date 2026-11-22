package com.syw.figlogsample

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.ceil

internal class WorkoutComparisonChart(context: Context, private val sets: List<Sets>) : View(context) {
    data class Sets(val number: Int, val previous: Double?, val current: Double?)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val previousColor = 0xFFAFE2BF.toInt()
    private val currentColor = 0xFF16845E.toInt()
    private val muted = 0xFF586477.toInt()
    private val maxWeight = sets.maxOfOrNull { maxOf(it.previous ?: 0.0, it.current ?: 0.0) } ?: 0.0
    private val axisMax = if (maxWeight < 1_000_000) (ceil(maxWeight / 20) * 20).coerceAtLeast(20.0) else maxWeight

    init {
        contentDescription = sets.joinToString(", ") {
            "${it.number}세트 이전 ${it.previous?.displayWeight() ?: "기록 없음"}, 이번 ${it.current?.displayWeight() ?: "기록 없음"}"
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sets.isEmpty()) return
        val left = dp(33)
        val right = width - dp(7)
        val top = dp(20)
        val bottom = height - dp(25)
        val plotHeight = bottom - top
        if (right <= left || plotHeight <= 0) return

        paint.strokeWidth = dp(1)
        paint.textSize = dp(10)
        for (line in 0..4) {
            val level = line / 4f
            val y = bottom - plotHeight * level
            paint.color = if (line == 0) 0xFFADB6C2.toInt() else 0xFFE8EDF0.toInt()
            canvas.drawLine(left, y, right, y, paint)
            paint.color = muted
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(display(axisMax * level), left - dp(5), y + dp(3), paint)
        }
        val groupWidth = (right - left) / sets.size
        val barWidth = minOf(dp(26), groupWidth * 0.37f)
        for ((index, set) in sets.withIndex()) {
            val center = left + groupWidth * (index + 0.5f)
            val gap = dp(1)
            drawBar(canvas, center - gap - barWidth / 2, barWidth, set.previous, previousColor, bottom, plotHeight)
            drawBar(canvas, center + gap + barWidth / 2, barWidth, set.current, currentColor, bottom, plotHeight)
            paint.color = muted
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${set.number}세트", center, bottom + dp(17), paint)
        }
    }

    private fun drawBar(canvas: Canvas, center: Float, width: Float, weight: Double?, color: Int, bottom: Float, plotHeight: Float) {
        if (weight == null) return
        val top = bottom - (weight / axisMax).toFloat() * plotHeight
        paint.color = color
        if (weight > 0) {
            canvas.drawRoundRect(center - width / 2, top, center + width / 2, bottom, dp(3), dp(3), paint)
        }
        paint.color = 0xFF101725.toInt()
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(display(weight), center, top - dp(4), paint)
    }

    private fun display(value: Double) = if (value >= 10_000) {
        String.format(java.util.Locale.US, "%.0e", value)
    } else value.displayWeight().take(6)

    private fun dp(value: Int) = value * density
}
