package com.cropora

import com.cropora.data.DiseaseXmlParser
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class DiseaseCatalogContractTest {
    @Test
    fun catalogCoversEveryModelLabelWithCompleteUniqueEntries() {
        val file = File("src/main/assets/diseases.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val entries = document.getElementsByTagName("disease")
        val labels = File("src/main/assets/labels.txt").readLines().filter { it.isNotBlank() }
        assertEquals(38, entries.length)

        val requiredTags = listOf("model_label", "name", "plant", "symptoms", "treatment", "prevention", "source")
        val names = mutableSetOf<String>()
        val catalogLabels = mutableSetOf<String>()
        val resourceDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/disease_names.xml"))
        val arrays = resourceDocument.getElementsByTagName("string-array")
        val keys = (0 until arrays.length).map { arrays.item(it) as Element }
            .first { it.getAttribute("name") == "disease_keys" }.getElementsByTagName("item")
        val canonicalNames = (0 until keys.length).map {
            DiseaseXmlParser.normalizeName(keys.item(it).textContent)
        }.toSet()
        for (index in 0 until entries.length) {
            val element = entries.item(index) as Element
            for (tag in requiredTags) {
                val value = element.getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()
                assertTrue("$tag must not be blank", value.isNotBlank())
            }
            val normalizedName = element.getElementsByTagName("name")
                .item(0).textContent.let(DiseaseXmlParser::normalizeName)
            assertTrue("duplicate disease name", names.add(normalizedName))
            assertTrue("unreachable catalog name: $normalizedName", normalizedName in canonicalNames)
            val label = element.getElementsByTagName("model_label").item(0).textContent.trim()
            assertTrue("duplicate model label", catalogLabels.add(label))
        }
        assertEquals(labels.toSet(), catalogLabels)
    }

    @Test
    fun legacyHuanglongbingNameStillResolves() {
        assertEquals(
            DiseaseXmlParser.normalizeName("Orange Huanglongbing (Citrus Greening)"),
            DiseaseXmlParser.normalizeName("Orange Haunglongbing (Citrus greening)")
        )
    }
}
