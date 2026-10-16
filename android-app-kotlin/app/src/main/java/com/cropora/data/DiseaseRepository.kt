package com.cropora.data

import android.content.Context

class DiseaseRepository private constructor(context: Context) {
    private val appContext = context.applicationContext

    @Volatile
    private var cachedDiseases: List<Disease>? = null

    fun getAllDiseases(): List<Disease> {
        return cachedDiseases ?: synchronized(this) {
            cachedDiseases ?: appContext.assets.open(ASSET_NAME).use { inputStream ->
                DiseaseXmlParser.parse(inputStream)
            }.also { diseases ->
                cachedDiseases = diseases
            }
        }
    }

    fun findByName(name: String): Disease? {
        val normalizedName = DiseaseXmlParser.normalizeName(name)
        return getAllDiseases().firstOrNull { disease ->
            DiseaseXmlParser.normalizeName(disease.name) == normalizedName
        }
    }

    companion object {
        private const val ASSET_NAME = "diseases.xml"

        @Volatile
        private var instance: DiseaseRepository? = null

        fun getInstance(context: Context): DiseaseRepository {
            return instance ?: synchronized(this) {
                instance ?: DiseaseRepository(context).also { repository ->
                    instance = repository
                }
            }
        }
    }
}
