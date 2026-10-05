package com.depressometer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
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
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.ads.MobileAds
import com.google.android.material.navigation.NavigationView
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
    private lateinit var moodCat: MoodCatView
    private lateinit var scale: GradientScaleView
    private lateinit var scoreText: TextView
    private lateinit var levelText: TextView
    private lateinit var hintText: TextView
    private lateinit var baselineText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var scanButton: Button
    private lateinit var shareButton: Button
    private lateinit var switchButton: Button
    private lateinit var menuButton: ImageButton
    private lateinit var drawer: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var actionTitle: TextView
    private lateinit var suggestionsBox: LinearLayout
    private lateinit var badgeText: TextView

    private lateinit var pointsStore: PointsStore
    private var lastEarnedPoints = 0
    private lateinit var catArt: Array<Bitmap?>

    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    // Scan session state
    private var isScanning = false
    private var resultLocked = false
    private var scanEndAt = 0L
    private val frameFeatures = mutableListOf<ScoreModel.FrameFeatures>()

    // Last locked result (for sharing)
    private var lastScore = 50f
    private var lastLevel = ""
    private var lastAffirmation = ""

    private val handler = Handler(Looper.getMainLooper())
    private val appPrefs by lazy { getSharedPreferences("depressometer_store", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.preview)
        overlay = findViewById(R.id.overlay)
        moodCat = findViewById(R.id.mood_cat)
        scale = findViewById(R.id.mood_scale)
        scoreText = findViewById(R.id.score_text)
        levelText = findViewById(R.id.level_text)
        hintText = findViewById(R.id.hint_text)
        baselineText = findViewById(R.id.baseline_text)
        progressBar = findViewById(R.id.scan_progress)
        scanButton = findViewById(R.id.btn_scan)
        shareButton = findViewById(R.id.btn_share)
        switchButton = findViewById(R.id.btn_switch)
        menuButton = findViewById(R.id.btn_menu)
        drawer = findViewById(R.id.drawer)
        navView = findViewById(R.id.nav_view)
        actionTitle = findViewById(R.id.action_title)
        suggestionsBox = findViewById(R.id.suggestions_box)
        badgeText = findViewById(R.id.badge_text)

        MobileAds.initialize(this)
        pointsStore = PointsStore(this)
        historyStore = HistoryStore(this)

        catArt = arrayOf(
            decode(R.drawable.cat_cheerful),
            decode(R.drawable.cat_happy),
            decode(R.drawable.cat_neutral),
            decode(R.drawable.cat_blue),
            decode(R.drawable.cat_sad)
        )

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        faceDetector = FaceDetection.getClient(options)
        cameraExecutor = Executors.newSingleThreadExecutor()

        scanButton.setOnClickListener { startScan() }
        switchButton.setOnClickListener { switchCamera() }
        menuButton.setOnClickListener { NavMenu.open(drawer) }
        shareButton.setOnClickListener { shareResult() }

        NavMenu.setup(this, drawer, navView, R.id.nav_scan, onRemind = { toggleReminder() })

        updateSwitchLabel()
        updateHint()
        scale.setScore(50f, animate = false)
        moodCat.setScore(50f)
        updateBaselineLabel(null)
        applyCosmetics()
        moodCat.setArt(catArt.getOrNull(2))

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    // ---------------------------------------------------------------- menu

    private fun remindersEnabled() = appPrefs.getBoolean("reminder_enabled", false)

    private fun setRemindersEnabled(enabled: Boolean) =
        appPrefs.edit().putBoolean("reminder_enabled", enabled).apply()

    private fun toggleReminder() {
        if (remindersEnabled()) {
            ReminderScheduler.cancel(this)
            setRemindersEnabled(false)
            Toast.makeText(this, getString(R.string.reminder_off_toast), Toast.LENGTH_SHORT).show()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF
            )
        } else {
            enableReminder()
        }
    }

    private fun enableReminder() {
        ReminderScheduler.schedule(this)
        setRemindersEnabled(true)
        Toast.makeText(this, getString(R.string.reminder_on_toast), Toast.LENGTH_SHORT).show()
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
        switchButton.setText(
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) R.string.btn_scan_others
            else R.string.btn_scan_self
        )
    }

    private fun updateHint() {
        hintText.setText(
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) R.string.hint_front
            else R.string.hint_back
        )
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
        scanButton.setText(R.string.btn_scanning)
        shareButton.visibility = View.GONE
        actionTitle.visibility = View.GONE
        suggestionsBox.visibility = View.GONE
        scoreText.text = getString(R.string.score_placeholder)
        levelText.setText(R.string.btn_scanning)
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
        scanButton.setText(R.string.btn_scan_again)

        if (frameFeatures.isEmpty()) {
            resultLocked = false
            scoreText.text = getString(R.string.score_placeholder)
            levelText.setText(R.string.no_face)
            scale.reset()
            return
        }

        val finalScore = ScoreModel.score(frameFeatures)
        resultLocked = true

        // Personal baseline: capture the previous reference, then fold in the new score.
        val prevBaseline = historyStore.baseline()
        val delta = if (prevBaseline != null) finalScore - prevBaseline else null
        historyStore.updateBaseline(finalScore)

        val level = levelString(finalScore)
        lastScore = finalScore
        lastLevel = level
        scoreText.text = String.format(Locale.US, "%.1f", finalScore)
        levelText.text = level
        applyScoreVisuals(finalScore, animate = true)
        updateBaselineLabel(delta)
        hintText.setText(R.string.scan_complete)

        val cameraName = if (lensFacing == CameraSelector.LENS_FACING_BACK) "Back" else "Front"
        historyStore.add(ScanRecord(System.currentTimeMillis(), finalScore, level, cameraName))

        // Reward good-mood scans with points.
        lastEarnedPoints = when {
            finalScore < 20f -> PointsStore.POINTS_SCORE_GREAT
            finalScore < 40f -> PointsStore.POINTS_SCORE_GOOD
            else -> 0
        }
        if (lastEarnedPoints > 0) pointsStore.addPoints(lastEarnedPoints)

        showResult(finalScore)

        shareButton.visibility = View.VISIBLE
    }

    private fun resetResult() {
        resultLocked = false
        isScanning = false
        handler.removeCallbacks(scanTicker)
        overlay.stopScan()
        progressBar.visibility = View.INVISIBLE
        scanButton.isEnabled = true
        scanButton.setText(R.string.btn_start_scan)
        shareButton.visibility = View.GONE
        scoreText.text = getString(R.string.score_placeholder)
        scoreText.setTextColor(Color.parseColor("#FFDD00"))
        levelText.setText(R.string.tap_to_begin)
        actionTitle.visibility = View.GONE
        suggestionsBox.visibility = View.GONE
        scale.reset()
        moodCat.setScore(50f)
        overlay.setAccentColor(Color.argb(255, 255, 221, 0))
        updateBaselineLabel(null)
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
        val imgW = imageProxy.width
        val imgH = imageProxy.height
        val inputImage =
            InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces[0]
                    val f = featuresOf(face)
                    val coaching = coachRes(face, imgW, imgH)
                    runOnUiThread { onFrame(f, coaching) }
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

    /** Positioning guidance from the face box relative to the frame. */
    private fun coachRes(face: Face, imgW: Int, imgH: Int): Int {
        if (imgW == 0 || imgH == 0) return R.string.coach_good
        val box = face.boundingBox
        val sizeRatio = box.width().toFloat() / imgW
        val cx = box.exactCenterX() / imgW
        val cy = box.exactCenterY() / imgH
        return when {
            sizeRatio < 0.22f -> R.string.coach_closer
            sizeRatio > 0.80f -> R.string.coach_back
            cx < 0.34f || cx > 0.66f || cy < 0.28f || cy > 0.72f -> R.string.coach_center
            else -> R.string.coach_good
        }
    }

    private fun onFrame(f: ScoreModel.FrameFeatures, coachingRes: Int) {
        when {
            isScanning -> {
                frameFeatures.add(f)
                val live = ScoreModel.score(frameFeatures)
                scoreText.text = String.format(Locale.US, "%.1f", live)
                levelText.text = levelString(live)
                applyScoreVisuals(live, animate = false)
                hintText.setText(coachingRes)
            }
            !resultLocked -> {
                val instant = ScoreModel.instant(f)
                scoreText.text = String.format(Locale.US, "%.1f", instant)
                levelText.text = getString(R.string.level_live, levelString(instant))
                applyScoreVisuals(instant, animate = false)
                hintText.setText(coachingRes)
            }
            // else: locked — leave the final score untouched.
        }
    }

    private fun applyScoreVisuals(score: Float, animate: Boolean) {
        val color = GradientScaleView.colorForScore(score)
        scoreText.setTextColor(color)
        scale.setScore(score, animate)
        moodCat.setSkin(
            (Shop.byId(pointsStore.equippedSkin()) ?: Shop.defaultSkin()).fur,
            (Shop.byId(pointsStore.equippedSkin()) ?: Shop.defaultSkin()).accent
        )
        catArt.getOrNull(affinityIndex(score))?.let { moodCat.setArt(it) }
        overlay.setAccentColor(color)
    }

    private fun decode(resId: Int): Bitmap? = try {
        BitmapFactory.decodeResource(resources, resId)
    } catch (e: Exception) {
        null
    }

    private fun levelString(score: Float): String = getString(
        when {
            score < 20f -> R.string.level_cheerful
            score < 40f -> R.string.level_happy
            score < 60f -> R.string.level_neutral
            score < 80f -> R.string.level_blue
            else -> R.string.level_high
        }
    )

    private fun updateBaselineLabel(delta: Float?) {
        baselineText.text = if (delta == null) {
            getString(R.string.baseline_none)
        } else {
            getString(R.string.baseline_vs, String.format(Locale.US, "%+.1f", delta))
        }
    }

    // ---------------------------------------------------------------- result

    private fun showResult(score: Float) {
        val density = resources.displayMetrics.density
        actionTitle.visibility = View.VISIBLE
        suggestionsBox.visibility = View.VISIBLE
        suggestionsBox.removeAllViews()

        val affirmation = affirmationFor(score)
        lastAffirmation = affirmation
        suggestionsBox.addView(styledLine(affirmation, "#FFE9A8", 15f, true, true, density))

        if (lastEarnedPoints > 0) {
            suggestionsBox.addView(
                styledLine(getString(R.string.points_earned_scan, lastEarnedPoints), "#7CFFB2", 14f, true, false, density)
            )
        }

        for (tip in suggestionsFor(score)) {
            suggestionsBox.addView(styledLine("•  $tip", "#EEEEEE", 14f, false, false, density))
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
        setTypeface(typeface, if (bold) Typeface.BOLD_ITALIC else if (italic) Typeface.ITALIC else Typeface.NORMAL)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (6 * density).toInt() }
    }

    private fun affinityIndex(score: Float): Int = when {
        score < 20f -> 0
        score < 40f -> 1
        score < 60f -> 2
        score < 80f -> 3
        else -> 4
    }

    private fun affirmationFor(score: Float): String =
        resources.getStringArray(R.array.affirmations)[affinityIndex(score)]

    private fun suggestionsFor(score: Float): List<String> {
        val resId = when {
            score < 20f -> R.array.tips_cheerful
            score < 40f -> R.array.tips_happy
            score < 60f -> R.array.tips_neutral
            score < 80f -> R.array.tips_blue
            else -> R.array.tips_high
        }
        return resources.getStringArray(resId).toList()
    }

    private fun shareResult() {
        val bitmap = ShareCard.render(this, lastScore, lastLevel, lastAffirmation)
        ShareCard.share(this, bitmap, getString(R.string.share_chooser))
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_CAMERA -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    startCamera()
                } else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
                }
            }
            REQ_NOTIF -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    enableReminder()
                } else {
                    Toast.makeText(
                        this, getString(R.string.notif_permission_needed), Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /** Apply the equipped shop skin + badge. */
    private fun applyCosmetics() {
        val skin = Shop.byId(pointsStore.equippedSkin()) ?: Shop.defaultSkin()
        moodCat.setSkin(skin.fur, skin.accent)
        val badge = Shop.byId(pointsStore.equippedBadge())
        badgeText.text = badge?.badgeRes?.let { getString(it) } ?: ""
    }

    override fun onResume() {
        super.onResume()
        // Cosmetics may have changed on the points screen.
        applyCosmetics()
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
        private const val REQ_NOTIF = 2
        private const val SCAN_DURATION_MS = 4000L
        private const val MENU_POINTS = 0
        private const val MENU_HISTORY = 1
        private const val MENU_INFO = 2
        private const val MENU_REMIND = 3
    }
}
