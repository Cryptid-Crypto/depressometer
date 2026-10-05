package com.depressometer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Horizontal mood scale: green (cheerful) through yellow / orange / violet to
 * dark blue (depress-o-meter high). A marker shows where the current score sits.
 */
class GradientScaleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }
    private val markerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 230, 230, 230)
        textSize = 11f * resources.displayMetrics.scaledDensity
    }

    private val trackRect = RectF()
    private val gradient = LinearGradient(0f, 0f, 1f, 0f, STOPS, null, Shader.TileMode.CLAMP)

    private var displayScore = 50f
    private var animator: ValueAnimator? = null

    init {
        trackPaint.shader = gradient
    }

    fun setScore(score: Float, animate: Boolean) {
        val target = score.coerceIn(0f, 100f)
        animator?.cancel()
        if (!animate) {
            displayScore = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(displayScore, target).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                displayScore = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun reset() {
        animator?.cancel()
        displayScore = 50f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 12f * resources.displayMetrics.density
        val trackH = 18f * resources.displayMetrics.density
        val left = paddingLeft + pad
        val right = width - paddingRight - pad
        val top = paddingTop + pad

        trackRect.set(left, top, right, top + trackH)

        // Update gradient to real pixel coordinates.
        trackPaint.shader = LinearGradient(
            left, top, right, top, STOPS, null, Shader.TileMode.CLAMP
        )
        val radius = trackH / 2f
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        // marker
        val cx = left + (displayScore / 100f) * (right - left)
        val cy = top + radius
        val markerR = 11f * resources.displayMetrics.density
        canvas.drawCircle(cx, cy, markerR, markerFill.apply { color = colorForScore(displayScore) })
        canvas.drawCircle(cx, cy, markerR, markerRing)

        // labels
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Cheerful", left, cy + markerR + 16f * resources.displayMetrics.density, labelPaint)
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            "Depress-o-meter",
            right,
            cy + markerR + 16f * resources.displayMetrics.density,
            labelPaint
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }

    companion object {
        /** 0 (green, good) -> 100 (dark blue, low mood). */
        private val STOPS = intArrayOf(
            0xFF22C55E.toInt(), // green
            0xFFA3E635.toInt(), // lime
            0xFFFDE047.toInt(), // yellow
            0xFFF97316.toInt(), // orange
            0xFF7C3AED.toInt(), // violet
            0xFF1E3A8A.toInt()  // dark blue
        )

        /** Interpolated colour for a 0..100 score (also used to tint the score text). */
        fun colorForScore(score: Float): Int {
            val t = (score / 100f).coerceIn(0f, 1f) * (STOPS.size - 1)
            val i = t.toInt().coerceIn(0, STOPS.size - 2)
            val f = t - i
            return blend(STOPS[i], STOPS[i + 1], f)
        }

        private fun blend(c1: Int, c2: Int, f: Float): Int {
            val a = (Color.alpha(c1) + (Color.alpha(c2) - Color.alpha(c1)) * f).toInt()
            val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * f).toInt()
            val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * f).toInt()
            val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * f).toInt()
            return Color.argb(a, r, g, b)
        }
    }
}
