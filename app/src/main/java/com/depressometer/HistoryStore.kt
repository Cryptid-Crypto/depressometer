package com.depressometer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Persists scan history as a JSON array in SharedPreferences, plus a slowly
 * adapting personal baseline used to contextualise new scores.
 * Most-recent-first, capped at [MAX] entries.
 */
class HistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): List<ScanRecord> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ScanRecord(
                    timestamp = o.getLong("t"),
                    score = o.getDouble("s").toFloat(),
                    level = o.optString("l", ""),
                    camera = o.optString("c", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(record: ScanRecord) {
        val list = all().toMutableList()
        list.add(0, record)
        while (list.size > MAX) list.removeAt(list.size - 1)

        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("t", r.timestamp)
                put("s", r.score.toDouble())
                put("l", r.level)
                put("c", r.camera)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    // ------------------------------------------------------------ baseline

    /** Current personal baseline, or null until at least one scan exists. */
    fun baseline(): Float? =
        if (prefs.contains(KEY_BASELINE)) prefs.getFloat(KEY_BASELINE, 50f) else null

    /**
     * Fold a new raw score into the personal baseline via an exponential moving
     * average, so the reference drifts slowly with the person over time.
     * Returns the updated baseline.
     */
    fun updateBaseline(raw: Float): Float {
        val current = baseline()
        val updated = if (current == null) raw else EMA * raw + (1 - EMA) * current
        prefs.edit().putFloat(KEY_BASELINE, updated).apply()
        return updated
    }

    // ------------------------------------------------------------ streaks

    /** Consecutive days (ending today or yesterday) with at least one scan. */
    fun streakDays(): Int {
        val records = all()
        if (records.isEmpty()) return 0

        val days = records.map { dayKey(it.timestamp) }.toHashSet()
        val cal = Calendar.getInstance()

        // Allow the streak to still count if today has no scan yet.
        if (!days.contains(dayKey(cal.timeInMillis))) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        var streak = 0
        while (days.contains(dayKey(cal.timeInMillis))) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    private fun dayKey(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    companion object {
        private const val PREFS = "depressometer_store"
        private const val KEY = "scan_history"
        private const val KEY_BASELINE = "baseline"
        private const val MAX = 100
        private const val EMA = 0.3f
    }
}
