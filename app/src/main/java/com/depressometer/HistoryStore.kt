package com.depressometer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists scan history as a JSON array in SharedPreferences.
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

    companion object {
        private const val PREFS = "depressometer_store"
        private const val KEY = "scan_history"
        private const val MAX = 100
    }
}
