package com.depressometer

import kotlin.math.sqrt

/**
 * Research-informed "depress-o-meter" score.
 *
 *   LOW score  (green)     = mood cues look positive
 *   HIGH score (dark blue) = cues resemble patterns seen in low mood
 *
 * The composite blends facial cues that appear repeatedly in the
 * facial-action-coding (FACS) depression literature:
 *
 *  • Smile intensity — AU12 (lip corner puller) + AU6 (cheek raiser).
 *      Girard, Cohn et al. (ACII 2009) and Jones et al. (2018) found depressed
 *      participants show FEWER AU12 (happiness) activations and MORE AU1
 *      (inner brow raiser), AU4 (brow lowerer) and AU15 (lip corner depressor)
 *      — the classic sadness signature. ML Kit exposes this as
 *      `smilingProbability`.
 *
 *  • Eye openness — an Eye-Aspect-Ratio (EAR) style alertness cue.
 *      The FacePsy mobile-sensing study (2024) found eye-open probability and
 *      EAR positively correlated with depressive episodes (r up to ~0.35),
 *      linked to altered alertness / sleep disturbance.
 *
 *  • Head movement — FacePsy reported head *yaw* had the strongest negative
 *      correlation with depressive episodes (r ≈ −0.33): less head movement /
 *      orientation change. Reduced overall facial movement is also the classic
 *      "psychomotor retardation" marker (Girard & Cohn 2009).
 *
 *  • Expressivity variation — blunted affect: depressed individuals show less
 *      frame-to-frame variation in facial muscle activity (Girard & Cohn 2009).
 *
 * CAVEAT: "smiling depression" is real — some people mask low mood with a
 * smile (FacePsy even saw smiling probability rise with episodes). The smile
 * cue is therefore weighted, but the score is never driven by it alone.
 *
 * This is a toy. It is NOT a medical assessment or diagnosis.
 */
object ScoreModel {

    /** Per-frame facial cues extracted from ML Kit for one camera frame. */
    data class FrameFeatures(
        val smile: Float,   // 0..1 smilingProbability
        val eyeOpen: Float, // 0..1 mean of left/right eye-open probability
        val yaw: Float,     // headEulerAngleY (degrees)
        val pitch: Float,   // headEulerAngleX (degrees)
        val roll: Float     // headEulerAngleZ (degrees)
    )

    private const val W_SMILE = 0.40f
    private const val W_EYE = 0.25f
    private const val W_MOVEMENT = 0.20f
    private const val W_EXPRESSIVITY = 0.15f

    /** Combined head rotation (deg) treated as "lively" — no penalty below this. */
    private const val MOVEMENT_GOOD_DEG = 12f

    /** Smiling std-dev treated as "expressive". */
    private const val EXPRESSIVITY_GOOD = 0.12f

    /** Composite 0..100 score over a whole scan; higher = more low-mood cues. */
    fun score(frames: List<FrameFeatures>): Float {
        if (frames.isEmpty()) return 50f

        val meanSmile = frames.map { it.smile }.average().toFloat()
        val meanEye = frames.map { it.eyeOpen }.average().toFloat()

        val movement = range(frames.map { it.yaw }) +
            range(frames.map { it.pitch }) +
            range(frames.map { it.roll })

        val smileStd = stdDev(frames.map { it.smile })

        val smileDeficit = (1f - meanSmile).coerceIn(0f, 1f)
        val eyeDeficit = (1f - meanEye).coerceIn(0f, 1f)
        val movementDeficit = (1f - (movement / MOVEMENT_GOOD_DEG)).coerceIn(0f, 1f)
        val expressivityDeficit = (1f - (smileStd / EXPRESSIVITY_GOOD)).coerceIn(0f, 1f)

        val composite = W_SMILE * smileDeficit +
            W_EYE * eyeDeficit +
            W_MOVEMENT * movementDeficit +
            W_EXPRESSIVITY * expressivityDeficit

        return (composite * 100f).coerceIn(0f, 100f)
    }

    /** Instant single-frame score for the live pre-scan preview. */
    fun instant(f: FrameFeatures): Float {
        val composite = 0.6f * (1f - f.smile) + 0.4f * (1f - f.eyeOpen)
        return (composite * 100f).coerceIn(0f, 100f)
    }

    fun levelFor(score: Float): String = when {
        score < 20f -> "CHEERFUL"
        score < 40f -> "HAPPY"
        score < 60f -> "NEUTRAL"
        score < 80f -> "BLUE"
        else -> "DEPRESS-O-METER HIGH"
    }

    private fun range(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        return (values.maxOrNull()!! - values.minOrNull()!!)
    }

    private fun stdDev(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance).toFloat()
    }
}
