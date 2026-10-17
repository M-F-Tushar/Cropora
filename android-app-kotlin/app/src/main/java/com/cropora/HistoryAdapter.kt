package com.cropora

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cropora.database.ScanRecord
import com.cropora.data.DiseasePresentation
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.text.DateFormat
import java.util.Date


class HistoryAdapter(
    private val advanced: Boolean = false,
    private val onItemSelected: (ScanRecord) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val items = mutableListOf<ScanRecord>()

    fun submitList(scans: List<ScanRecord>) {
        items.clear()
        items.addAll(scans)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            if (advanced) R.layout.item_scan_history_advanced else R.layout.item_scan_history,
            parent,
            false
        )
        return HistoryViewHolder(view, advanced)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position], onItemSelected)
    }

    override fun getItemCount(): Int = items.size

    class HistoryViewHolder(
        itemView: View,
        private val advanced: Boolean = false
    ) : RecyclerView.ViewHolder(itemView) {
        private val textDisease: TextView = itemView.findViewById(R.id.textHistoryDisease)
        private val textConfidence: TextView = itemView.findViewById(R.id.textHistoryConfidence)
        private val textTimestamp: TextView = itemView.findViewById(R.id.textHistoryTimestamp)
        private val ring: CircularProgressIndicator? = itemView.findViewById(R.id.advItemRing)
        private val statusPill: TextView? = itemView.findViewById(R.id.advItemStatus)

        fun bind(record: ScanRecord, onItemSelected: (ScanRecord) -> Unit) {
            val context = itemView.context
            textDisease.text = DiseasePresentation.name(context, record.disease, record.modelLabel)
            if (advanced) {
                val status = AdvancedUi.status(record)
                textConfidence.text = AdvancedUi.percent(context, record.confidence)
                textTimestamp.text = AdvancedUi.dateTime(context, record.timestamp)
                ring?.let {
                    it.progress = ResultTextFormatter.confidencePercent(record.confidence.coerceIn(0f, 1f))
                    AdvancedUi.tintRing(it, status)
                }
                statusPill?.let { AdvancedUi.bindStatusPill(it, status) }
            } else {
                textConfidence.text = context.getString(
                    R.string.history_confidence_format,
                    record.confidence * 100f
                )
                val formattedTimestamp = DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT,
                    itemView.resources.configuration.locales[0]
                ).format(Date(record.timestamp))
                textTimestamp.text = context.getString(
                    R.string.history_saved_at_format,
                    formattedTimestamp
                )
            }
            itemView.setOnClickListener { onItemSelected(record) }
        }
    }
}
