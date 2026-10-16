package com.cropora.data

import android.util.Xml
import java.io.InputStream
import java.util.Locale
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

object DiseaseXmlParser {
    @Throws(XmlPullParserException::class)
    fun parse(inputStream: InputStream): List<Disease> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(inputStream, "UTF-8")
        }
        val diseases = mutableListOf<Disease>()
        val normalizedNames = mutableSetOf<String>()

        var name = ""
        var plant = ""
        var symptoms = ""
        var treatment = ""
        var prevention = ""
        var currentTag: String? = null
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "disease") {
                        name = ""
                        plant = ""
                        symptoms = ""
                        treatment = ""
                        prevention = ""
                    }
                }
                XmlPullParser.TEXT -> {
                    val value = parser.text.trim()
                    if (value.isNotEmpty()) {
                        when (currentTag) {
                            "name" -> name += value
                            "plant" -> plant += value
                            "symptoms" -> symptoms += value
                            "treatment" -> treatment += value
                            "prevention" -> prevention += value
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "disease") {
                        val values = listOf(name, plant, symptoms, treatment, prevention)
                        if (values.any { it.isBlank() }) {
                            throw XmlPullParserException("Every disease requires five non-empty fields")
                        }
                        val normalizedName = normalizeName(name)
                        if (!normalizedNames.add(normalizedName)) {
                            throw XmlPullParserException("Duplicate disease name: $name")
                        }
                        diseases += Disease(name, plant, symptoms, treatment, prevention)
                    }
                    currentTag = null
                }
            }
            event = parser.next()
        }

        if (diseases.isEmpty()) {
            throw XmlPullParserException("Disease catalog is empty")
        }
        return diseases
    }

    fun normalizeName(value: String): String = value.trim().lowercase(Locale.US)
}
