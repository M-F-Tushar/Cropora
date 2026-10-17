package com.cropora

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cropora.database.AppDatabase
import com.cropora.data.DiseaseRepository
import com.cropora.data.Disease
import com.cropora.data.DiseasePresentation
import com.cropora.database.ScanRecord
import com.cropora.network.PredictionResponse
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException

class ResultActivity : AppCompatActivity() {

    private lateinit var resultId: String
    private lateinit var modelLabel: String
    private lateinit var disease: String
    private var confidence: Float = 0f
    private var uncertain: Boolean = true
    private var guidanceAvailable: Boolean = false
    private var guidanceLoadComplete: Boolean = false
    private lateinit var symptoms: String
    private lateinit var treatment: String
    private lateinit var prevention: String
    private var savedToHistory = false
    private var displayGuidance: Disease? = null

    private fun displayText(localized: String?, original: String, fallback: Int): String =
        localized ?: if (resources.configuration.locales[0].language == "bn") getString(fallback) else original

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val saveButton = findViewById<Button>(R.id.buttonSaveHistory)
        if (result.values.any { granted -> granted }) {
            insertHistory(saveButton, findLastKnownLocation())
        } else {
            Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_SHORT).show()
            insertHistory(saveButton, null)
        }
    }

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
        val confidencePercent = ResultTextFormatter.confidencePercent(confidence)

        findViewById<TextView>(R.id.textResultDisease).text = DiseasePresentation.name(this, disease, modelLabel)
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
        findViewById<TextView>(R.id.textResultSymptoms).text = displayText(null, symptoms, R.string.generic_symptoms)
        findViewById<TextView>(R.id.textResultTreatment).text = displayText(null, treatment, R.string.generic_treatment)
        findViewById<TextView>(R.id.textResultPrevention).text = displayText(null, prevention, R.string.generic_prevention)
        savedToHistory = savedInstanceState?.getBoolean(STATE_SAVED_TO_HISTORY) ?: false
        findViewById<Button>(R.id.buttonSaveHistory).apply {
            isEnabled = !savedToHistory && guidanceLoadComplete
            if (savedToHistory) {
                setText(R.string.saved_to_history)
            }
            setOnClickListener {
                saveToHistory(this)
            }
        }
        findViewById<Button>(R.id.buttonShareResult).setOnClickListener { shareResult() }
        loadLocalGuidance()
    }

    override fun onResume() {
        super.onResume()
        val advanced = UiPreferences.isAdvanced(this)
        listOf(
            R.id.textResultModelLabel,
            R.id.textResultConfidence,
            R.id.progressResultConfidence
        ).forEach { viewId ->
            findViewById<View>(viewId).visibility = if (advanced) View.VISIBLE else View.GONE
        }
        findViewById<TextView>(R.id.textResultStatus).setText(
            when {
                uncertain -> R.string.result_uncertain
                advanced -> R.string.result_confident
                else -> R.string.result_simple_suggestion
            }
        )
    }

    private fun loadLocalGuidance() {
        lifecycleScope.launch {
            var localGuidanceFound = false
            try {
                val localDisease = withContext(Dispatchers.IO) {
                    val repository = DiseaseRepository.getInstance(applicationContext)
                    displayGuidance = repository.findLocalizedByName(this@ResultActivity, disease)
                    repository.findByName(disease)
                }
                if (localDisease != null) {
                    symptoms = localDisease.symptoms
                    treatment = localDisease.treatment
                    prevention = localDisease.prevention
                    guidanceAvailable = true
                    localGuidanceFound = true
                }
            } catch (_: IOException) {
                localGuidanceFound = false
            } catch (_: XmlPullParserException) {
                localGuidanceFound = false
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                localGuidanceFound = false
            }

            findViewById<TextView>(R.id.textGuidanceStatus).text = getString(
                when {
                    localGuidanceFound -> R.string.guidance_local_library
                    guidanceAvailable -> R.string.guidance_available
                    else -> R.string.guidance_not_reviewed
                }
            )
            findViewById<TextView>(R.id.textResultSymptoms).text = displayText(displayGuidance?.symptoms, symptoms, R.string.generic_symptoms)
            findViewById<TextView>(R.id.textResultTreatment).text = displayText(displayGuidance?.treatment, treatment, R.string.generic_treatment)
            findViewById<TextView>(R.id.textResultPrevention).text = displayText(displayGuidance?.prevention, prevention, R.string.generic_prevention)
            guidanceLoadComplete = true
            findViewById<Button>(R.id.buttonSaveHistory).isEnabled = !savedToHistory
        }
    }

    private fun saveToHistory(saveButton: Button) {
        saveButton.isEnabled = false
        val includeLocation = findViewById<CheckBox>(R.id.checkboxIncludeLocation).isChecked
        when {
            !includeLocation -> insertHistory(saveButton, null)
            hasLocationPermission() -> insertHistory(saveButton, findLastKnownLocation())
            else -> locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    private fun insertHistory(saveButton: Button, location: Location?) {
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
                    latitude = location?.latitude,
                    longitude = location?.longitude,
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
                startActivity(Intent(this@ResultActivity, HistoryActivity::class.java))
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

    private fun shareResult() {
        val shareText = getString(
            R.string.share_result_template,
            DiseasePresentation.name(this, disease, modelLabel),
            ResultTextFormatter.confidencePercent(confidence),
            displayText(displayGuidance?.symptoms, symptoms, R.string.generic_symptoms),
            displayText(displayGuidance?.treatment, treatment, R.string.generic_treatment),
            displayText(displayGuidance?.prevention, prevention, R.string.generic_prevention)
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_result_subject))
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_result_chooser)))
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun findLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        val manager = getSystemService(LocationManager::class.java) ?: return null
        return try {
            manager.getProviders(true)
                .mapNotNull { provider -> manager.getLastKnownLocation(provider) }
                .maxByOrNull { location -> location.time }
        } catch (_: SecurityException) {
            null
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
