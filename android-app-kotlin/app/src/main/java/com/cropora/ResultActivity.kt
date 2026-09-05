package com.cropora

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cropora.network.PredictionResponse
import kotlin.math.roundToInt

class ResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val disease = intent.getStringExtra(EXTRA_DISEASE) ?: getString(R.string.result_unknown)
        val modelLabel = intent.getStringExtra(EXTRA_MODEL_LABEL) ?: getString(R.string.result_unknown)
        val confidence = intent.getFloatExtra(EXTRA_CONFIDENCE, 0f)
        val uncertain = intent.getBooleanExtra(EXTRA_UNCERTAIN, true)
        val guidanceAvailable = intent.getBooleanExtra(EXTRA_GUIDANCE_AVAILABLE, false)
        val symptoms = intent.getStringExtra(EXTRA_SYMPTOMS) ?: getString(R.string.guidance_unavailable)
        val treatment = intent.getStringExtra(EXTRA_TREATMENT) ?: getString(R.string.guidance_unavailable)
        val prevention = intent.getStringExtra(EXTRA_PREVENTION) ?: getString(R.string.guidance_unavailable)
        val normalizedConfidence = confidence
            .takeIf { it.isFinite() }
            ?.coerceIn(0f, 1f)
            ?: 0f
        val confidencePercent = (normalizedConfidence * 100f).roundToInt()

        findViewById<TextView>(R.id.textResultDisease).text = disease
        findViewById<TextView>(R.id.textResultModelLabel).text = getString(
            R.string.model_label_format,
            modelLabel
        )
        findViewById<TextView>(R.id.textResultConfidence).text = getString(
            R.string.confidence_format,
            confidencePercent
        )
        findViewById<ProgressBar>(R.id.progressResultConfidence).progress = confidencePercent
        findViewById<TextView>(R.id.textResultStatus).text = getString(
            if (uncertain) R.string.result_uncertain else R.string.result_confident
        )
        findViewById<TextView>(R.id.textGuidanceStatus).text = getString(
            if (guidanceAvailable) R.string.guidance_available else R.string.guidance_not_reviewed
        )
        findViewById<TextView>(R.id.textResultSymptoms).text = symptoms
        findViewById<TextView>(R.id.textResultTreatment).text = treatment
        findViewById<TextView>(R.id.textResultPrevention).text = prevention
    }

    companion object {
        fun createIntent(context: Context, prediction: PredictionResponse): Intent {
            return Intent(context, ResultActivity::class.java).apply {
                putExtra(EXTRA_MODEL_LABEL, prediction.modelLabel)
                putExtra(EXTRA_DISEASE, prediction.disease)
                putExtra(EXTRA_CONFIDENCE, prediction.confidence)
                putExtra(EXTRA_UNCERTAIN, prediction.uncertain)
                putExtra(EXTRA_GUIDANCE_AVAILABLE, prediction.guidanceAvailable)
                putExtra(EXTRA_SYMPTOMS, prediction.symptoms)
                putExtra(EXTRA_TREATMENT, prediction.treatment)
                putExtra(EXTRA_PREVENTION, prediction.prevention)
            }
        }

        private const val EXTRA_MODEL_LABEL = "extra_model_label"
        private const val EXTRA_DISEASE = "extra_disease"
        private const val EXTRA_CONFIDENCE = "extra_confidence"
        private const val EXTRA_UNCERTAIN = "extra_uncertain"
        private const val EXTRA_GUIDANCE_AVAILABLE = "extra_guidance_available"
        private const val EXTRA_SYMPTOMS = "extra_symptoms"
        private const val EXTRA_TREATMENT = "extra_treatment"
        private const val EXTRA_PREVENTION = "extra_prevention"
    }
}
