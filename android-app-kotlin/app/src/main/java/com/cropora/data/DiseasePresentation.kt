package com.cropora.data

import android.content.Context
import com.cropora.R

object DiseasePresentation {
    fun name(context: Context, canonicalName: String, modelLabel: String = ""): String {
        val keys = context.resources.getStringArray(R.array.disease_keys)
        val normalized = DiseaseXmlParser.normalizeName(canonicalName)
        val index = keys.indexOfFirst { DiseaseXmlParser.normalizeName(it) == normalized }
            .takeIf { it >= 0 }
            ?: labelIndex(context, modelLabel)
        return if (index >= 0) context.resources.getStringArray(R.array.disease_names)[index]
            else canonicalName
    }

    private fun labelIndex(context: Context, modelLabel: String): Int {
        val canonical = when (modelLabel) {
            "Apple___Apple_scab" -> "Apple Scab"
            "Corn___Cercospora_leaf_spot Gray_leaf_spot" -> "Corn Gray Leaf Spot"
            else -> modelLabel.replace("___", " ").replace('_', ' ')
        }
        return context.resources.getStringArray(R.array.disease_keys).indexOfFirst {
            DiseaseXmlParser.normalizeName(it) == DiseaseXmlParser.normalizeName(canonical)
        }
    }
}