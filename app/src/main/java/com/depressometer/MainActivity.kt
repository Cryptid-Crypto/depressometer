package com.depressometer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face detection.*
import java.util.concurrent.ExecutionException
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var faceDetector: FaceDetector
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize ML Kit face detector
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        faceDetector = FaceDetection.getClient(options)

        // Request camera permission if not granted
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        } else {
            startCamera()
        }

        // Disclaimer text
        val disclaimer = findViewById<TextView>(R.id.disclaimer_text)
        disclaimer.text = (
            "Depressometer\n" +
            "A playful camera-based mood scanner.\n" +
            "NOT a medical diagnosis or treatment.\n" +
            "If you're struggling, please consult a professional."
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraLivePreview()
        }, null)
    }

    private fun bindCameraLivePreview() {
        if (cameraProvider == null) return

        // Image analysis use case: receives camera frames for analysis
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(androidx.camera.core.Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        // Set analyzer
        imageAnalysis?.setAnalyzer(cameraExecutor) { image ->
            if (image != null) {
                val inputImage = InputImage.fromMediaImage(image, image.imageInfo.cameraSensorOrientation)
                val task = faceDetector.process(inputImage)
                task.addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        // First face → compute "score"
                        val face = faces[0]
                        val score = computeScore(face)
                        runOnUiThread {
                            val scoreView = findViewById<TextView>(R.id.score_text)
                            scoreView.text = "Depressometer Score: ${score.toStringAsFixed(1)}"
                            val levelText = findViewById<TextView>(R.id.level_text)
                            levelText.text = levelForScore(score)
                        }
                    }
                }
                task.addOnFailureListener { e -> Log.e("Depressometer", "Face detection failed", e) }
            }
        }

        imageAnalysis?.setUseCaseDefaults(
            Preview.PreviewBuilder.setLensFacing(LensFacing.FRONT)
        )

        // Preview use case: shows camera feed
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(PreviewViewSurfaceProvider(findViewById(R.id.preview)))
        }

        cameraProvider?.bindToLivePreview(
            listOf(imageAnalysis, preview),
            cameraExecutor
        )
    }

    /** Simple heuristic: lower score = more smiling = "less depressed" */
    private fun computeScore(face: Face): Float {
        val bounds = face.boundingBox
        // Mouth region approximation from face landmarks
        var smileScore = 50.0f  // baseline neutral

        val landmarks = face.getLandmarks()
        if (landmarks != null) {
            // Try to get mouth landmarks
            val mouth = landmarks[FaceLandmark.CHOULE] ?: return smileScore

            // Simple heuristic: ratio of mouth width to face width
            // Open-mouth smile → wider mouth → lower score
            // For demo, just use a randomized or fixed fallback
            // TODO: Real geometry would use multiple landmarks
            smileScore = (1.0f - abs(face.headEulerAngleX / 90.0f)) * 50.0f + 25.0f
        }

        // Clamp to 0-100
        return smileScore.coerceIn(0.0f, 100.0f)
    }

    private fun levelForScore(score: String): String {
        val s = score.toFloat()
        return when {
            s < 20 → "CHEERFUL 😄"
            s < 40 → "HAPPY 🙂"
            s < 60 → "NEUTRAL 😐"
            s < 80 → "BLUE 😟"
            else → "DEPRESS-O-METER HIGH 😥"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 1) {
            if (results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            }
        }
    }
}