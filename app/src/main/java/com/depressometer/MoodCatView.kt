package com.depressometer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * The cat companion. If generated character art is supplied it is drawn in a
 * circular frame with a mood-coloured ring; otherwise a vector cat is drawn
 * whose colour and expression track the score.
 */
class MoodCatView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val earPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val innerEarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1E1E2E")
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    /** Solid white disc behind the art so the mascot circle always reads clean. */
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val mouthPath = Path()
    private val clipPath = Path()
    private val srcRect = Rect()
    private val dstRect = RectF()

    private val washPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        alpha = 92
    }

    private var score = 50f
    private var skinTint: Int? = null
    private var art: Bitmap? = null

    fun setScore(score: Float) {
        this.score = score.coerceIn(0f, 100f)
        invalidate()
    }

    /** Colour wash for a shop skin, applied over the character art. Null = none. */
    fun setSkinTint(color: Int?) {
        skinTint = color
        invalidate()
    }

    /** Supply generated character art (drawn in a circular frame). Pass null to use the vector cat. */
    fun setArt(bitmap: Bitmap?) {
        art = bitmap
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val moodColor = GradientScaleView.colorForScore(score)
        val bmp = art

        if (bmp != null) {
            val cx = w / 2f
            val cy = h / 2f
            val r = minOf(w, h) * 0.46f
            clipPath.reset()
            clipPath.addCircle(cx, cy, r, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawCircle(cx, cy, r, bgPaint)
            val layer = canvas.saveLayer(cx - r, cy - r, cx + r, cy + r, null)
            srcRect.set(0, 0, bmp.width, bmp.height)
            // Fit the whole character inside the circle (inscribed square) so the
            // entire cat — head to tail — stays visible, not cropped by the ring.
            val half = r * 0.70f
            dstRect.set(cx - half, cy - half, cx + half, cy + half)
            canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint)
            val tint = skinTint
            if (tint != null) {
                washPaint.color = tint
                canvas.drawRect(dstRect, washPaint)
            }
            canvas.restoreToCount(layer)
            canvas.restore()
            ringPaint.color = moodColor
            canvas.drawCircle(cx, cy, r, ringPaint)
            return
        }

        // ---- vector fallback ----
        val fur = blend(moodColor, Color.WHITE, 0.35f)
        val dark = blend(moodColor, Color.BLACK, 0.30f)
        headPaint.color = fur
        earPaint.color = fur
        strokePaint.color = dark
        innerEarPaint.color = dark

        val cx = w / 2f
        val cy = h * 0.56f
        val r = minOf(w, h) * 0.34f

        val earH = r * 0.7f
        val earL = Path().apply {
            moveTo(cx - r * 0.75f, cy - r * 0.55f)
            lineTo(cx - r * 0.55f, cy - r * 0.55f - earH)
            lineTo(cx - r * 0.15f, cy - r * 0.72f)
            close()
        }
        val earR = Path().apply {
            moveTo(cx + r * 0.75f, cy - r * 0.55f)
            lineTo(cx + r * 0.55f, cy - r * 0.55f - earH)
            lineTo(cx + r * 0.15f, cy - r * 0.72f)
            close()
        }
        canvas.drawPath(earL, earPaint)
        canvas.drawPath(earR, earPaint)
        canvas.drawPath(earL, innerEarPaint)
        canvas.drawPath(earR, innerEarPaint)
        canvas.drawCircle(cx, cy, r, headPaint)

        val mood = (score - 50f) / 50f
        val eyeH = r * (0.16f + 0.06f * mood.coerceAtLeast(0f))
        val eyeW = r * 0.15f
        val eyeY = cy - r * 0.12f
        canvas.drawOval(cx - r * 0.34f - eyeW, eyeY - eyeH, cx - r * 0.34f + eyeW, eyeY + eyeH, eyePaint)
        canvas.drawOval(cx + r * 0.34f - eyeW, eyeY - eyeH, cx + r * 0.34f + eyeW, eyeY + eyeH, eyePaint)

        val nose = Path().apply {
            moveTo(cx - r * 0.08f, cy + r * 0.12f)
            lineTo(cx + r * 0.08f, cy + r * 0.12f)
            lineTo(cx, cy + r * 0.22f)
            close()
        }
        canvas.drawPath(nose, eyePaint)

        val my = cy + r * 0.30f
        val mw = r * 0.30f
        mouthPath.reset()
        mouthPath.moveTo(cx - mw, my)
        mouthPath.quadTo(cx, my + mood * r * 0.28f, cx + mw, my)
        canvas.drawPath(mouthPath, strokePaint)
    }

    private fun blend(c1: Int, c2: Int, f: Float): Int {
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * f).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * f).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * f).toInt()
        return Color.argb(255, r, g, b)
    }
}
