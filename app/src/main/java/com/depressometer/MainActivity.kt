package com.depressometer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var faceDetector: FaceDetector
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var historyStore: HistoryStore

    private lateinit var previewView: PreviewView
    private lateinit var overlay: FaceOverlayView
    private lateinit var scale: GradientScaleView
    private lateinit var scoreText: TextView
    private lateinit var levelText: TextView
    private lateinit var hintText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var scanButton: Button
    private lateinit var switchButton: Button
    private lateinit var actionTitle: TextView
    private lateinit var suggestionsBox: LinearLayout

    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    // Scan session state
    private var isScanning = false
    private var resultLocked = false
    private var scanEndAt = 0L
    private val frameFeatures = mutableListOf<ScoreModel.FrameFeatures>()

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.preview)
        overlay = findViewById(R.id.overlay)
        scale = findViewById(R.id.mood_scale)
        scoreText = findViewById(R.id.score_text)
        levelText = findViewById(R.id.level_text)
        hintText = findViewById(R.id.hint_text)
        progressBar = findViewById(R.id.scan_progress)
        scanButton = findViewById(R.id.btn_scan)
        switchButton = findViewById(R.id.btn_switch)
        actionTitle = findViewById(R.id.action_title)
        suggestionsBox = findViewById(R.id.suggestions_box)

        historyStore = HistoryStore(this)

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        faceDetector = FaceDetection.getClient(options)
        cameraExecutor = Executors.newSingleThreadExecutor()

        scanButton.setOnClickListener { startScan() }
        switchButton.setOnClickListener { switchCamera() }
        findViewById<Button>(R.id.btn_history).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        updateSwitchLabel()
        updateHint()
        scale.setScore(50f, animate = false) // neutral resting marker

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    // ---------------------------------------------------------------- camera

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Camera provider error", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(cameraExecutor) { imageProxy -> processImage(imageProxy) }

        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, selector, preview, analysis)
        } catch (e: Exception) {
            Log.e(TAG, "bind failed for lens $lensFacing", e)
            Toast.makeText(this, "That camera isn't available on this device", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun switchCamera() {
        if (isScanning) return
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT)
            CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
        updateSwitchLabel()
        updateHint()
        resetResult()
        bindUseCases()
    }

    private fun updateSwitchLabel() {
        switchButton.text = if (lensFacing == CameraSelector.LENS_FACING_FRONT)
            "Scan others" else "Scan self"
    }

    private fun updateHint() {
        hintText.text = if (lensFacing == CameraSelector.LENS_FACING_FRONT)
            "Center your face in the oval" else "Point the back camera at the person and hold still"
    }

    // ---------------------------------------------------------------- scan

    private fun startScan() {
        if (isScanning) return
        isScanning = true
        resultLocked = false
        frameFeatures.clear()

        overlay.startScan()
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        scanButton.isEnabled = false
        scanButton.text = "Scanning…"
        actionTitle.visibility = View.GONE
        suggestionsBox.visibility = View.GONE
        scoreText.text = "--"
        levelText.text = "Hold still…"
        scale.reset()

        scanEndAt = System.currentTimeMillis() + SCAN_DURATION_MS
        handler.post(scanTicker)
    }

    private val scanTicker = object : Runnable {
        override fun run() {
            if (!isScanning) return
            val now = System.currentTimeMillis()
            val elapsed = SCAN_DURATION_MS - (scanEndAt - now)
            progressBar.progress = (elapsed * 100 / SCAN_DURATION_MS).toInt().coerceIn(0, 100)
            if (now >= scanEndAt) finishScan() else handler.postDelayed(this, 60)
        }
    }

    private fun finishScan() {
        isScanning = false
        overlay.stopScan()
        progressBar.visibility = View.INVISIBLE
        scanButton.isEnabled = true
        scanButton.text = "Scan again"

        if (frameFeatures.isEmpty()) {
            resultLocked = false
            scoreText.text = "--"
            levelText.text = "No face detected — try again"
            scale.reset()
            return
        }

        val finalScore = ScoreModel.score(frameFeatures)
        resultLocked = true

        val level = ScoreModel.levelFor(finalScore)
        scoreText.text = String.format(Locale.US, "%.1f", finalScore)
        levelText.text = level
        applyScoreVisuals(finalScore, animate = true)
        hintText.text = "Scan complete ✓"

        val cameraName = if (lensFacing == CameraSelector.LENS_FACING_BACK) "Back" else "Front"
        historyStore.add(ScanRecord(System.currentTimeMillis(), finalScore, level, cameraName))

        showResult(finalScore)
    }

    private fun resetResult() {
        resultLocked = false
        isScanning = false
        handler.removeCallbacks(scanTicker)
        overlay.stopScan()
        progressBar.visibility = View.INVISIBLE
        scanButton.isEnabled = true
        scanButton.text = "Start scan"
        scoreText.text = "--"
        scoreText.setTextColor(Color.parseColor("#FFDD00"))
        levelText.text = "Tap Start scan to begin"
        actionTitle.visibility = View.GONE
        suggestionsBox.visibility = View.GONE
        scale.reset()
        overlay.setAccentColor(Color.argb(255, 255, 221, 0))
        updateHint()
    }

    // ---------------------------------------------------------------- analysis

    @ExperimentalGetImage
    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val inputImage =
            InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val f = featuresOf(faces[0])
                    runOnUiThread { onFrame(f) }
                }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Face detection failed", e) }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun featuresOf(face: Face): ScoreModel.FrameFeatures {
        val leftEye = face.leftEyeOpenProbability ?: 0.5f
        val rightEye = face.rightEyeOpenProbability ?: 0.5f
        return ScoreModel.FrameFeatures(
            smile = face.smilingProbability ?: 0.5f,
            eyeOpen = (leftEye + rightEye) / 2f,
            yaw = face.headEulerAngleY,
            pitch = face.headEulerAngleX,
            roll = face.headEulerAngleZ
        )
    }

    private fun onFrame(f: ScoreModel.FrameFeatures) {
        when {
            isScanning -> {
                frameFeatures.add(f)
                val live = ScoreModel.score(frameFeatures)
                scoreText.text = String.format(Locale.US, "%.1f", live)
                levelText.text = ScoreModel.levelFor(live)
                applyScoreVisuals(live, animate = false)
            }
            !resultLocked -> {
                val instant = ScoreModel.instant(f)
                scoreText.text = String.format(Locale.US, "%.1f", instant)
                levelText.text = "(live) ${ScoreModel.levelFor(instant)}"
                applyScoreVisuals(instant, animate = false)
            }
            // else: locked — leave the final score untouched.
        }
    }

    /** Tint the score text, gradient marker and overlay oval to the score colour. */
    private fun applyScoreVisuals(score: Float, animate: Boolean) {
        val color = GradientScaleView.colorForScore(score)
        scoreText.setTextColor(color)
        scale.setScore(score, animate)
        if (!resultLocked) overlay.setAccentColor(color)
    }

    // ---------------------------------------------------------------- result

    private fun showResult(score: Float) {
        val density = resources.displayMetrics.density
        actionTitle.visibility = View.VISIBLE
        suggestionsBox.visibility = View.VISIBLE
        suggestionsBox.removeAllViews()

        // 1) A supportive thought first (always, tuned to how things look).
        affirmationFor(score)?.let { line ->
            suggestionsBox.addView(styledLine(line, "#FFE9A8", 15f, bold = true, italic = true, density))
        }

        // 2) Practical suggestions.
        for (tip in suggestionsFor(score)) {
            suggestionsBox.addView(styledLine("•  $tip", "#EEEEEE", 14f, bold = false, italic = false, density))
        }
    }

    private fun styledLine(
        text: String,
        hex: String,
        sizeSp: Float,
        bold: Boolean,
        italic: Boolean,
        density: Float
    ): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor(hex))
        textSize = sizeSp
        if (bold) setTypeface(typeface, Typeface.BOLD_ITALIC)
        else if (italic) setTypeface(typeface, Typeface.ITALIC)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (6 * density).toInt() }
    }

    /** Supportive / encouraging thought, always shown after a scan. */
    private fun affirmationFor(score: Float): String = when {
        score < 20f -> "You're glowing today — that good energy is yours to keep. 🌟"
        score < 40f -> "Your face is carrying real warmth right now. Nice. ✨"
        score < 60f -> "Steady and balanced — a solid place to be. 🌤️"
        score < 80f -> "A softer day, and that's okay. You're doing better than you feel. 💙"
        else -> "Hard moments never tell the whole story. You matter, and this passes. 💙"
    }

    private fun suggestionsFor(score: Float): List<String> = when {
        score < 20f -> listOf(
            "Keep doing what you're doing — it's working.",
            "Bank this win: note one thing that made today good.",
            "Share the energy — tell someone you appreciate them."
        )
        score < 40f -> listOf(
            "A short walk or stretch keeps the good mood rolling.",
            "Hydrate — low energy often just means low water.",
            "Message a friend; connection lifts mood fast."
        )
        score < 60f -> listOf(
            "Step outside for 10 minutes of daylight.",
            "Try 5 slow breaths: in 4s, hold 4s, out 4s.",
            "Put on one song you love and actually listen to it.",
            "Drink a glass of water and roll your shoulders."
        )
        score < 80f -> listOf(
            "Reach out to one person today — a call or a text.",
            "Move your body for 15 minutes (walk, dance, anything).",
            "Cut back on doomscrolling for the next hour.",
            "Get some sunlight and eat a proper meal."
        )
        else -> listOf(
            "Talk to someone you trust today — you don't have to carry it alone.",
            "One small step: water, an open window, a step outside.",
            "If this feeling lingers, a real check-in with a professional can help."
        )
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(scanTicker)
        cameraExecutor.shutdown()
        faceDetector.close()
    }

    companion object {
        private const val TAG = "Depressometer"
        private const val REQ_CAMERA = 1
        private const val SCAN_DURATION_MS = 4000L
    }
}
