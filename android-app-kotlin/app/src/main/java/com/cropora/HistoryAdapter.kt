package com.cropora

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cropora.database.ScanRecord
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

class HistoryAdapter(
    private val onItemSelected: (ScanRecord) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val items = mutableListOf<ScanRecord>()

    fun submitList(scans: List<ScanRecord>) {
        items.clear()
        items.addAll(scans)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scan_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position], onItemSelected)
    }

    override fun getItemCount(): Int = items.size

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textDisease: TextView = itemView.findViewById(R.id.textHistoryDisease)
        private val textConfidence: TextView = itemView.findViewById(R.id.textHistoryConfidence)
        private val textTimestamp: TextView = itemView.findViewById(R.id.textHistoryTimestamp)

        fun bind(record: ScanRecord, onItemSelected: (ScanRecord) -> Unit) {
            textDisease.text = record.disease
            textConfidence.text = itemView.context.getString(
                R.string.confidence_format,
                (record.confidence * 100f).roundToInt()
            )
            val formattedTimestamp = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT
            ).format(Date(record.timestamp))
            textTimestamp.text = itemView.context.getString(
                R.string.history_saved_at_format,
                formattedTimestamp
            )
            itemView.setOnClickListener { onItemSelected(record) }
        }
    }
}
