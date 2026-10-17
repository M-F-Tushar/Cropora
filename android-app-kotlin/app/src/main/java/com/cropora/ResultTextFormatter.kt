package com.cropora

import kotlin.math.roundToInt

object ResultTextFormatter {
    fun confidencePercent(confidence: Float): Int = (confidence * 100f).roundToInt()

    fun shareText(
        disease: String,
        confidence: Float,
        symptoms: String,
        treatment: String,
        prevention: String
    ): String {
        return """
            Disease: $disease
            Confidence: ${confidencePercent(confidence)}%

            Symptoms: $symptoms

            Treatment: $treatment

            Prevention: $prevention

            Model suggestion only; verify serious cases with a qualified agricultural source.
        """.trimIndent()
    }
}
