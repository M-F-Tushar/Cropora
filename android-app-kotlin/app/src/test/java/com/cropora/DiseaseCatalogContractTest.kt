package com.cropora

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
        for (index in 0 until entries.length) {
            val element = entries.item(index) as Element
            for (tag in requiredTags) {
                val value = element.getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()
                assertTrue("$tag must not be blank", value.isNotBlank())
            }
            val normalizedName = element.getElementsByTagName("name")
                .item(0).textContent.trim().lowercase()
            assertTrue("duplicate disease name", names.add(normalizedName))
            val label = element.getElementsByTagName("model_label").item(0).textContent.trim()
            assertTrue("duplicate model label", catalogLabels.add(label))
        }
        assertEquals(labels.toSet(), catalogLabels)
    }
}
