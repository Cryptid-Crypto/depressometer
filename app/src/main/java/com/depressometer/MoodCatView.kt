package com.depressometer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * A tiny cat companion whose colour and expression reflect the current score.
 * Low score (green) = happy cat; high score (dark blue) = droopy cat.
 */
class MoodCatView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val earPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1E1E2E")
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }
    private val mouthPath = Path()

    private var score = 50f

    fun setScore(score: Float) {
        this.score = score.coerceIn(0f, 100f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val accent = GradientScaleView.colorForScore(score)
        // soften the accent a little for the fur
        val fur = blend(accent, Color.WHITE, 0.35f)
        headPaint.color = fur
        earPaint.color = fur
        strokePaint.color = blend(accent, Color.BLACK, 0.25f)

        val cx = w / 2f
        val cy = h * 0.56f
        val r = minOf(w, h) * 0.34f

        // ears
        val earH = r * 0.7f
        val earW = r * 0.55f
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
        run { // inner ears
            val inner = Paint(earPaint).apply { color = blend(accent, Color.BLACK, 0.45f) }
            canvas.drawPath(earL, inner)
            canvas.drawPath(earR, inner)
        }
        // head
        canvas.drawCircle(cx, cy, r, headPaint)

        // eyes (squint a bit when happy, wider when low)
        val mood = (score - 50f) / 50f // -1 happy .. 1 sad
        val eyeH = r * (0.16f + 0.06f * mood.coerceAtLeast(0f))
        val eyeW = r * 0.15f
        val eyeY = cy - r * 0.12f
        canvas.drawOval(cx - r * 0.34f - eyeW, eyeY - eyeH, cx - r * 0.34f + eyeW, eyeY + eyeH, eyePaint)
        canvas.drawOval(cx + r * 0.34f - eyeW, eyeY - eyeH, cx + r * 0.34f + eyeW, eyeY + eyeH, eyePaint)

        // nose
        val nose = Path().apply {
            moveTo(cx - r * 0.08f, cy + r * 0.12f)
            lineTo(cx + r * 0.08f, cy + r * 0.12f)
            lineTo(cx, cy + r * 0.22f)
            close()
        }
        canvas.drawPath(nose, eyePaint)

        // mouth: smiles (arc up) when happy, droops when low
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
