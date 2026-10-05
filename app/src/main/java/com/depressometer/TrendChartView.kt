package com.depressometer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * Simple line chart of recent scores (oldest -> newest) with a soft fill.
 * The y-axis is 0..100 (higher = more low-mood cues).
 */
class TrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#7C3AED")
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 255, 255)
        strokeWidth = 2f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255)
        textSize = 10f * resources.displayMetrics.scaledDensity
    }

    private val path = Path()
    private val fillPath = Path()
    private var scores: List<Float> = emptyList()

    /** Pass scores oldest -> newest. */
    fun setScores(values: List<Float>) {
        scores = values
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val padL = 34f * resources.displayMetrics.density
        val padR = 12f * resources.displayMetrics.density
        val padT = 12f * resources.displayMetrics.density
        val padB = 22f * resources.displayMetrics.density
        val left = padL
        val right = w - padR
        val top = padT
        val bottom = h - padB

        // grid lines at 0, 50, 100
        labelPaint.textAlign = Paint.Align.RIGHT
        for (v in intArrayOf(0, 50, 100)) {
            val y = bottom - (v / 100f) * (bottom - top)
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText(v.toString(), left - 6f, y + 4f, labelPaint)
        }

        if (scores.size < 2) {
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("—", (left + right) / 2f, (top + bottom) / 2f, labelPaint)
            return
        }

        val n = scores.size
        val dx = (right - left) / (n - 1)
        path.reset()
        fillPath.reset()
        scores.forEachIndexed { i, s ->
            val x = left + i * dx
            val y = bottom - (s.coerceIn(0f, 100f) / 100f) * (bottom - top)
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, bottom)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(right, bottom)
        fillPath.close()

        fillPaint.shader = LinearGradient(
            0f, top, 0f, bottom,
            intArrayOf(Color.argb(120, 124, 58, 237), Color.argb(10, 124, 58, 237)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)

        // last point dot
        val lastX = right
        val lastY = bottom - (scores.last().coerceIn(0f, 100f) / 100f) * (bottom - top)
        canvas.drawCircle(lastX, lastY, 6f * resources.displayMetrics.density, dotPaint)
    }
}
