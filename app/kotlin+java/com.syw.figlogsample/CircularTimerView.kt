package com.syw.figlogsample

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
//S05 시나리오 타이머 관련 클래스 파일
class CircularTimerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFDDE5E2.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF008F60.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }

    // 남은 시간 비율 (0.0 ~ 1.0)
    var progress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = 15f

        val rect = RectF(
            padding,
            padding,
            width - padding,
            height - padding
        )

        // 전체 회색 원
        canvas.drawArc(
            rect,
            -90f,
            360f,
            false,
            backgroundPaint
        )

        // 남은 시간 초록색 원
        canvas.drawArc(
            rect,
            -90f,
            360f * progress,
            false,
            progressPaint
        )
    }
}
