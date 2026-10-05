package com.depressometer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanAdapter(private val items: List<ScanRecord>) :
    RecyclerView.Adapter<ScanAdapter.ScanViewHolder>() {

    class ScanViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val date: TextView = view.findViewById(R.id.item_date)
        val score: TextView = view.findViewById(R.id.item_score)
        val level: TextView = view.findViewById(R.id.item_level)
        val camera: TextView = view.findViewById(R.id.item_camera)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scan, parent, false)
        return ScanViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScanViewHolder, position: Int) {
        val record = items[position]
        val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        holder.date.text = fmt.format(Date(record.timestamp))
        holder.score.text = String.format(Locale.US, "%.1f", record.score)
        holder.level.text = record.level
        holder.camera.text = "${record.camera} camera"
    }

    override fun getItemCount(): Int = items.size
}
