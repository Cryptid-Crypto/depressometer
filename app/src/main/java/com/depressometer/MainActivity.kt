package com.depressometer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var faceDetector: FaceDetector
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var historyStore: HistoryStore

    private lateinit var previewView: PreviewView
    private lateinit var overlay: FaceOverlayView
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
    private val scanSamples = mutableListOf<Float>()

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.preview)
        overlay = findViewById(R.id.overlay)
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
            CameraSelector.LENS_FACING_BACK
        else
            CameraSelector.LENS_FACING_FRONT

        updateSwitchLabel()
        updateHint()
        resetResult()
        bindUseCases()
    }

    private fun updateSwitchLabel() {
        // If currently on the front camera, the action offers to scan *others*.
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
        scanSamples.clear()

        overlay.startScan()
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        scanButton.isEnabled = false
        scanButton.text = "Scanning…"
        actionTitle.visibility = View.GONE
        suggestionsBox.visibility = View.GONE
        scoreText.text = "--"
        levelText.text = "Hold still…"

        scanEndAt = System.currentTimeMillis() + SCAN_DURATION_MS
        handler.post(scanTicker)
    }

    private val scanTicker = object : Runnable {
        override fun run() {
            if (!isScanning) return
            val now = System.currentTimeMillis()
            val elapsed = SCAN_DURATION_MS - (scanEndAt - now)
            progressBar.progress = (elapsed * 100 / SCAN_DURATION_MS).coerceIn(0, 100)
            if (now >= scanEndAt) finishScan() else handler.postDelayed(this, 60)
        }
    }

    private fun finishScan() {
        isScanning = false
        overlay.stopScan()
        progressBar.visibility = View.INVISIBLE
        scanButton.isEnabled = true
        scanButton.text = "Scan again"

        if (scanSamples.isEmpty()) {
            // Nothing detected — don't lock or save a meaningless result.
            resultLocked = false
            scoreText.text = "--"
            levelText.text = "No face detected — try again"
            return
        }

        // Lock in the average of every frame captured during the scan.
        val finalScore = scanSamples.average().toFloat().coerceIn(0f, 100f)
        resultLocked = true

        scoreText.text = String.format(java.util.Locale.US, "%.1f", finalScore)
        levelText.text = levelForScore(finalScore)
        hintText.text = "Scan complete ✓"

        val cameraName = if (lensFacing == CameraSelector.LENS_FACING_BACK) "Back" else "Front"
        historyStore.add(
            ScanRecord(System.currentTimeMillis(), finalScore, levelForScore(finalScore), cameraName)
        )

        showSuggestions(finalScore)
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
        levelText.text = "Tap Start scan to begin"
        actionTitle.visibility = View.GONE
        suggestionsBox.visibility = View.GONE
        hintText.text = ""
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
                    val score = computeScore(faces[0])
                    runOnUiThread { onScore(score) }
                }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Face detection failed", e) }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun onScore(instant: Float) {
        when {
            isScanning -> {
                scanSamples.add(instant)
                val avg = scanSamples.average().toFloat()
                scoreText.text = String.format(java.util.Locale.US, "%.1f", avg)
                levelText.text = levelForScore(avg)
            }
            !resultLocked -> {
                // Live preview before a scan starts.
                scoreText.text = String.format(java.util.Locale.US, "%.1f", instant)
                levelText.text = "(live) ${levelForScore(instant)}"
            }
            // else: result is locked — leave the final score untouched.
        }
    }

    /**
     * Playful, non-clinical heuristic. Lower score = happier.
     *  - smilingProbability pushes the score down
     *  - closed eyes / a big head tilt push it up
     */
    private fun computeScore(face: Face): Float {
        val smiling = face.smilingProbability ?: 0.5f
        val leftEye = face.leftEyeOpenProbability ?: 0.5f
        val rightEye = face.rightEyeOpenProbability ?: 0.5f
        val tilt = abs(face.headEulerAngleZ) / 90f

        var score = 50f
        score -= (smiling - 0.5f) * 80f
        score += (1f - ((leftEye + rightEye) / 2f)) * 20f
        score += tilt * 10f
        return score.coerceIn(0f, 100f)
    }

    private fun levelForScore(score: Float): String = when {
        score < 20f -> "CHEERFUL"
        score < 40f -> "HAPPY"
        score < 60f -> "NEUTRAL"
        score < 80f -> "BLUE"
        else -> "DEPRESS-O-METER HIGH"
    }

    // ---------------------------------------------------------------- suggestions

    private fun showSuggestions(score: Float) {
        val tips = suggestionsFor(score)
        actionTitle.visibility = View.VISIBLE
        suggestionsBox.visibility = View.VISIBLE
        suggestionsBox.removeAllViews()

        val density = resources.displayMetrics.density
        for (tip in tips) {
            val tv = TextView(this).apply {
                text = "•  $tip"
                setTextColor(Color.parseColor("#EEEEEE"))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (6 * density).toInt() }
            }
            suggestionsBox.addView(tv)
        }
    }

    private fun suggestionsFor(score: Float): List<String> = when {
        score < 20f -> listOf(
            "Great mood detected — keep doing what you're doing.",
            "Share the good energy: message a friend you appreciate.",
            "Bank this win — note what made today good."
        )
        score < 40f -> listOf(
            "Solid mood. A short walk or stretch can keep it going.",
            "Hydrate — dehydration often reads as low energy.",
            "Text someone you like; connection lifts mood fast."
        )
        score < 60f -> listOf(
            "Step outside for 10 minutes of daylight.",
            "Try 5 slow breaths: in 4s, hold 4s, out 4s.",
            "Put on one song you love and actually listen.",
            "Drink a glass of water and stretch your shoulders."
        )
        score < 80f -> listOf(
            "Reach out to one person today — a call or a text.",
            "Move your body for 15 minutes (walk, dance, anything).",
            "Cut back on doomscrolling for the next hour.",
            "Get some sunlight and a proper meal.",
            "Be kind to yourself — this is a fun app, not a diagnosis."
        )
        else -> listOf(
            "Talk to someone you trust today — you don't have to carry it alone.",
            "Small step: drink water, open a window, step outside.",
            "Consider a real check-in with a professional if this persists.",
            "This app is just for fun — it is NOT a medical assessment."
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
