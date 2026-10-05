package com.depressometer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Draws a dimmed backdrop with a clear oval "face target" and an animated
 * scanning line that sweeps top-to-bottom while a scan is running.
 */
class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val ovalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.argb(255, 255, 221, 0)
    }
    private val scanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 8f
        color = Color.argb(200, 255, 221, 0)
    }
    private val scanGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 26f
        color = Color.argb(60, 255, 221, 0)
    }

    private val path = Path()
    private val ovalRect = RectF()

    private var scanning = false
    private var scanProgress = 0f
    private var animator: ValueAnimator? = null

    fun startScan() {
        scanning = true
        if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1300
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    scanProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
        invalidate()
    }

    fun stopScan() {
        scanning = false
        animator?.cancel()
        animator = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        var ovalW = w * 0.72f
        var ovalH = ovalW * 1.30f
        val maxH = h * 0.62f
        if (ovalH > maxH) {
            ovalH = maxH
            ovalW = ovalH / 1.30f
        }
        val cx = w / 2f
        val cy = h * 0.40f
        ovalRect.set(cx - ovalW / 2f, cy - ovalH / 2f, cx + ovalW / 2f, cy + ovalH / 2f)

        // Dim everything *outside* the oval using an even-odd filled path.
        path.reset()
        path.addRect(0f, 0f, w, h, Path.Direction.CW)
        path.addOval(ovalRect, Path.Direction.CW)
        path.fillType = Path.FillType.EVEN_ODD
        canvas.drawPath(path, dimPaint)

        ovalPaint.color = if (scanning) Color.argb(255, 255, 221, 0)
                          else Color.argb(150, 255, 255, 255)
        canvas.drawOval(ovalRect, ovalPaint)

        if (scanning) {
            val y = ovalRect.top + scanProgress * ovalRect.height()
            canvas.drawLine(ovalRect.left, y, ovalRect.right, y, scanGlowPaint)
            canvas.drawLine(ovalRect.left, y, ovalRect.right, y, scanLinePaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }
}
