package com.cropora

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class DiseaseCatalogContractTest {
    @Test
    fun catalogHasTenCompleteUniqueEntries() {
        val file = File("src/main/assets/diseases.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val entries = document.getElementsByTagName("disease")
        assertEquals(10, entries.length)

        val requiredTags = listOf("name", "plant", "symptoms", "treatment", "prevention")
        val names = mutableSetOf<String>()
        for (index in 0 until entries.length) {
            val element = entries.item(index) as Element
            for (tag in requiredTags) {
                val value = element.getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()
                assertTrue("$tag must not be blank", value.isNotBlank())
            }
            val normalizedName = element.getElementsByTagName("name")
                .item(0).textContent.trim().lowercase()
            assertTrue("duplicate disease name", names.add(normalizedName))
        }
    }
}
