package com.depressometer

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val list = findViewById<RecyclerView>(R.id.history_list)
        val empty = findViewById<TextView>(R.id.empty_text)
        val chart = findViewById<TrendChartView>(R.id.trend_chart)
        val streakValue = findViewById<TextView>(R.id.streak_value)
        val store = HistoryStore(this)

        val records = store.all().sortedByDescending { it.timestamp }
        list.layoutManager = LinearLayoutManager(this)

        streakValue.text = getString(R.string.streak_days, store.streakDays())

        // Chart wants oldest -> newest.
        val chrono = records.sortedBy { it.timestamp }.map { it.score }
        chart.setScores(chrono.takeLast(30))

        if (records.isEmpty()) {
            empty.visibility = View.VISIBLE
            list.visibility = View.GONE
        } else {
            empty.visibility = View.GONE
            list.visibility = View.VISIBLE
            list.adapter = ScanAdapter(records)
        }

        findViewById<Button>(R.id.btn_clear).setOnClickListener {
            store.clear()
            recreate()
        }
    }
}
