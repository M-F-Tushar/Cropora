package com.cropora

import com.cropora.network.PredictionResponse
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionResponseContractTest {
    @Test
    fun parsesAllEightApiFields() {
        val json = """
            {
              "model_label": "Tomato___Early_blight",
              "disease": "Tomato Early Blight",
              "confidence": 0.87,
              "uncertain": false,
              "guidance_available": true,
              "symptoms": "Brown spots",
              "treatment": "Remove leaves",
              "prevention": "Rotate crops"
            }
        """.trimIndent()

        val response = Gson().fromJson(json, PredictionResponse::class.java)

        assertEquals("Tomato___Early_blight", response.modelLabel)
        assertEquals("Tomato Early Blight", response.disease)
        assertEquals(0.87f, response.confidence, 0.0001f)
        assertFalse(response.uncertain)
        assertTrue(response.guidanceAvailable)
        assertEquals("Brown spots", response.symptoms)
        assertEquals("Remove leaves", response.treatment)
        assertEquals("Rotate crops", response.prevention)
    }
}
