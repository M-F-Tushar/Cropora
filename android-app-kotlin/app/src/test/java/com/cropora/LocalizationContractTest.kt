package com.cropora

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LocalizationContractTest {
    private fun elements(path: String, tag: String): List<Element> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = document.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun strings(folder: String): Map<String, String> =
        elements("src/main/res/$folder/strings.xml", "string")
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent }

    private fun array(folder: String, name: String): List<String> {
        val nodes = elements("src/main/res/$folder/disease_names.xml", "string-array")
            .first { it.getAttribute("name") == name }.getElementsByTagName("item")
        return (0 until nodes.length).map { nodes.item(it).textContent.trim() }
    }

    @Test
    fun banglaCoversEveryTranslatableStringAndPreservesPlaceholders() {
        val english = strings("values")
        val bangla = strings("values-bn")
        assertEquals(english.keys, bangla.keys)
        val placeholder = Regex("%(?:[0-9]+\\$)?(?:\\.[0-9]+)?[sdf]|%%")
        english.forEach { (name, value) ->
            assertTrue("Blank translation: $name", bangla.getValue(name).isNotBlank())
            assertEquals(name, placeholder.findAll(value).map { it.value }.toList(),
                placeholder.findAll(bangla.getValue(name)).map { it.value }.toList())
        }
    }

    @Test
    fun everyModelLabelHasEnglishAndBanglaDisplayNames() {
        val keys = array("values", "disease_keys").map { it.lowercase(Locale.US) }
        val english = array("values", "disease_names")
        val bangla = array("values-bn", "disease_names")
        assertEquals(38, keys.toSet().size)
        assertEquals(keys.size, english.size)
        assertEquals(keys.size, bangla.size)
        assertTrue(english.all { it.isNotBlank() })
        assertTrue(bangla.all { it.any { character -> character in '\u0980'..'\u09ff' } })
        File("src/main/assets/labels.txt").readLines().filter { it.isNotBlank() }.forEach { label ->
            val canonical = when (label) {
                "Apple___Apple_scab" -> "Apple Scab"
                "Corn___Cercospora_leaf_spot Gray_leaf_spot" -> "Corn Gray Leaf Spot"
                else -> label.replace("___", " ").replace('_', ' ')
            }
            assertTrue("Missing model label: $label", canonical.lowercase(Locale.US) in keys)
        }
    }

    @Test
    fun banglaGuidanceMatchesReviewedCatalogAndKeepsCanonicalNames() {
        val english = elements("src/main/assets/diseases.xml", "disease")
        val bangla = elements("src/main/assets/diseases-bn.xml", "disease")
        fun text(entry: Element, tag: String) = entry.getElementsByTagName(tag).item(0).textContent.trim()
        assertEquals(english.map { text(it, "name") }.toSet(), bangla.map { text(it, "name") }.toSet())
        assertEquals(bangla.size, bangla.map { text(it, "name") }.toSet().size)
        bangla.forEach { entry ->
            listOf("plant", "symptoms", "treatment", "prevention").forEach { tag ->
                assertTrue("Untranslated $tag", text(entry, tag).any { it in '\u0980'..'\u09ff' })
            }
        }
    }
}