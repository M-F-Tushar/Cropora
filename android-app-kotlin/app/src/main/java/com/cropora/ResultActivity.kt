package com.cropora

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cropora.database.AppDatabase
import com.cropora.database.ScanRecord
import com.cropora.network.PredictionResponse
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ResultActivity : AppCompatActivity() {

    private lateinit var resultId: String
    private lateinit var modelLabel: String
    private lateinit var disease: String
    private var confidence: Float = 0f
    private var uncertain: Boolean = true
    private var guidanceAvailable: Boolean = false
    private lateinit var symptoms: String
    private lateinit var treatment: String
    private lateinit var prevention: String
    private var savedToHistory = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        resultId = intent.getStringExtra(EXTRA_RESULT_ID) ?: UUID.randomUUID().toString()
        modelLabel = intent.getStringExtra(EXTRA_MODEL_LABEL) ?: getString(R.string.result_unknown)
        disease = intent.getStringExtra(EXTRA_DISEASE) ?: getString(R.string.result_unknown)
        confidence = intent.getFloatExtra(EXTRA_CONFIDENCE, 0f)
            .takeIf { it.isFinite() }
            ?.coerceIn(0f, 1f)
            ?: 0f
        uncertain = intent.getBooleanExtra(EXTRA_UNCERTAIN, true)
        guidanceAvailable = intent.getBooleanExtra(EXTRA_GUIDANCE_AVAILABLE, false)
        symptoms = intent.getStringExtra(EXTRA_SYMPTOMS) ?: getString(R.string.guidance_unavailable)
        treatment = intent.getStringExtra(EXTRA_TREATMENT) ?: getString(R.string.guidance_unavailable)
        prevention = intent.getStringExtra(EXTRA_PREVENTION) ?: getString(R.string.guidance_unavailable)
        val confidencePercent = (confidence * 100f).roundToInt()

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
        savedToHistory = savedInstanceState?.getBoolean(STATE_SAVED_TO_HISTORY) ?: false
        findViewById<Button>(R.id.buttonSaveHistory).apply {
            isEnabled = !savedToHistory
            if (savedToHistory) {
                setText(R.string.saved_to_history)
            }
            setOnClickListener {
                saveToHistory(this)
            }
        }
    }

    private fun saveToHistory(saveButton: Button) {
        saveButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val record = ScanRecord(
                    resultId = resultId,
                    modelLabel = modelLabel,
                    disease = disease,
                    confidence = confidence,
                    uncertain = uncertain,
                    guidanceAvailable = guidanceAvailable,
                    symptoms = symptoms,
                    treatment = treatment,
                    prevention = prevention,
                    timestamp = System.currentTimeMillis()
                )
                AppDatabase.getInstance(applicationContext).scanDao().insertScan(record)
                savedToHistory = true
                saveButton.setText(R.string.saved_to_history)
                Toast.makeText(
                    this@ResultActivity,
                    R.string.history_saved,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                saveButton.isEnabled = true
                Toast.makeText(
                    this@ResultActivity,
                    R.string.history_save_error,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_SAVED_TO_HISTORY, savedToHistory)
        super.onSaveInstanceState(outState)
    }

    companion object {
        fun createIntent(context: Context, prediction: PredictionResponse): Intent {
            return Intent(context, ResultActivity::class.java).apply {
                putExtra(EXTRA_RESULT_ID, UUID.randomUUID().toString())
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

        private const val EXTRA_RESULT_ID = "extra_result_id"
        private const val EXTRA_MODEL_LABEL = "extra_model_label"
        private const val EXTRA_DISEASE = "extra_disease"
        private const val EXTRA_CONFIDENCE = "extra_confidence"
        private const val EXTRA_UNCERTAIN = "extra_uncertain"
        private const val EXTRA_GUIDANCE_AVAILABLE = "extra_guidance_available"
        private const val EXTRA_SYMPTOMS = "extra_symptoms"
        private const val EXTRA_TREATMENT = "extra_treatment"
        private const val EXTRA_PREVENTION = "extra_prevention"
        private const val STATE_SAVED_TO_HISTORY = "state_saved_to_history"
    }
}
