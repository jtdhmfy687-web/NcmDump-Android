package com.ncmdump.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class AudioTrimView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint().apply { color = 0xFFE0D8F0.toInt() }
    private val selectedPaint = Paint().apply { color = 0xFF7C6BC4.toInt() }
    private val handlePaint = Paint().apply { color = 0xFF5D4FA3.toInt() }
    private val textPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 28f
        isAntiAlias = true
    }

    var durationMs: Long = 0
        set(value) {
            field = value
            startMs = 0
            endMs = value
            invalidate()
        }

    var startMs: Long = 0
        private set
    var endMs: Long = 0
        private set

    private var dragging: Int = 0 // 0=none, 1=left, 2=right
    private val handleWidth = 40f
    private val barHeight = 80f

    var onTrimChanged: ((Long, Long) -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (durationMs <= 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val barTop = (h - barHeight) / 2
        val barBottom = barTop + barHeight

        // 背景条
        canvas.drawRoundRect(0f, barTop, w, barBottom, 16f, 16f, bgPaint)

        // 选中区域
        val left = (startMs.toFloat() / durationMs * w).coerceIn(0f, w)
        val right = (endMs.toFloat() / durationMs * w).coerceIn(0f, w)
        if (right > left) {
            canvas.drawRoundRect(left, barTop, right, barBottom, 16f, 16f, selectedPaint)
        }

        // 左滑块
        canvas.drawRoundRect(left - handleWidth/2, barTop - 10f, left + handleWidth/2, barBottom + 10f, 8f, 8f, handlePaint)
        // 右滑块
        canvas.drawRoundRect(right - handleWidth/2, barTop - 10f, right + handleWidth/2, barBottom + 10f, 8f, 8f, handlePaint)

        // 时间文字
        val startText = formatTime(startMs)
        val endText = formatTime(endMs)
        val startBounds = Rect()
        textPaint.getTextBounds(startText, 0, startText.length, startBounds)
        canvas.drawText(startText, left - startBounds.width()/2f, barTop - 20f, textPaint)
        val endBounds = Rect()
        textPaint.getTextBounds(endText, 0, endText.length, endBounds)
        canvas.drawText(endText, right - endBounds.width()/2f, barTop - 20f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (durationMs <= 0) return false
        val x = event.x
        val w = width.toFloat()
        val leftPos = startMs.toFloat() / durationMs * w
        val rightPos = endMs.toFloat() / durationMs * w

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragging = when {
                    Math.abs(x - leftPos) < handleWidth -> 1
                    Math.abs(x - rightPos) < handleWidth -> 2
                    else -> 0
                }
                return dragging != 0
            }
            MotionEvent.ACTION_MOVE -> {
                val pos = (x / w * durationMs).toLong().coerceIn(0, durationMs)
                if (dragging == 1) {
                    startMs = min(pos, endMs - 1000)
                } else if (dragging == 2) {
                    endMs = max(pos, startMs + 1000)
                }
                invalidate()
                onTrimChanged?.invoke(startMs, endMs)
                return true
            }
            MotionEvent.ACTION_UP -> {
                dragging = 0
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
               else String.format("%02d:%02d", m, s)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = resolveSize(140, heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }
}
