package com.cropora

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cropora.data.Disease

class DiseaseAdapter(
    private val onItemSelected: (Disease) -> Unit
) : RecyclerView.Adapter<DiseaseAdapter.DiseaseViewHolder>() {
    private val items = mutableListOf<Disease>()

    fun submitList(diseases: List<Disease>) {
        items.clear()
        items.addAll(diseases)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiseaseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_disease, parent, false)
        return DiseaseViewHolder(view)
    }

    override fun onBindViewHolder(holder: DiseaseViewHolder, position: Int) {
        holder.bind(items[position], onItemSelected)
    }

    override fun getItemCount(): Int = items.size

    class DiseaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textName: TextView = itemView.findViewById(R.id.textDiseaseName)
        private val textPlant: TextView = itemView.findViewById(R.id.textDiseasePlant)
        private val textSymptoms: TextView = itemView.findViewById(R.id.textDiseaseSymptomsPreview)

        fun bind(disease: Disease, onItemSelected: (Disease) -> Unit) {
            textName.text = disease.name
            textPlant.text = disease.plant
            textSymptoms.text = disease.symptoms
            itemView.setOnClickListener { onItemSelected(disease) }
        }
    }
}
