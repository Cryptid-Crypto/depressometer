package com.depressometer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** Renders a shareable result card (PNG) and hands it to the share sheet. */
object ShareCard {

    private const val W = 1080
    private const val H = 1350

    fun render(context: Context, score: Float, level: String, affirmation: String): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // Background
        val bg = Paint().apply { color = Color.parseColor("#12122A") }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        val accent = GradientScaleView.colorForScore(score)

        // Title
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 54f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
        }
        c.drawText("Depressometer", W / 2f, 140f, title)

        // Big score
        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent; textSize = 300f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
        }
        c.drawText(String.format(Locale.US, "%.1f", score), W / 2f, 500f, scorePaint)

        // Level
        val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 64f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
        }
        c.drawText(level, W / 2f, 600f, levelPaint)

        // Gradient bar
        val barLeft = 120f
        val barRight = W - 120f
        val barTop = 680f
        val barH = 46f
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                barLeft, barTop, barRight, barTop, STOPS, null, Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(RectF(barLeft, barTop, barRight, barTop + barH), barH / 2f, barH / 2f, barPaint)

        // marker
        val mx = barLeft + (score.coerceIn(0f, 100f) / 100f) * (barRight - barLeft)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.WHITE
        }
        c.drawCircle(mx, barTop + barH / 2f, 26f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        c.drawCircle(mx, barTop + barH / 2f, 26f, ring)

        // Affirmation (wrapped)
        val affPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFE9A8"); textSize = 46f; textAlign = Paint.Align.CENTER
        }
        var y = 830f
        for (line in wrap(affirmation, 34)) {
            c.drawText(line, W / 2f, y, affPaint)
            y += 60f
        }

        // Footer
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 200, 200, 200); textSize = 32f; textAlign = Paint.Align.CENTER
        }
        c.drawText("A playful mood scanner — not a medical device.", W / 2f, H - 90f, footer)

        return bmp
    }

    fun share(context: Context, bitmap: Bitmap, chooserTitle: String) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "depressometer_result.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val uri: Uri = FileProvider.getUriForFile(
            context, "com.depressometer.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private fun wrap(text: String, maxChars: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.isEmpty()) {
                current.append(word)
            } else if (current.length + 1 + word.length <= maxChars) {
                current.append(" ").append(word)
            } else {
                lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private val STOPS = intArrayOf(
        0xFF22C55E.toInt(),
        0xFFA3E635.toInt(),
        0xFFFDE047.toInt(),
        0xFFF97316.toInt(),
        0xFF7C3AED.toInt(),
        0xFF1E3A8A.toInt()
    )
}
