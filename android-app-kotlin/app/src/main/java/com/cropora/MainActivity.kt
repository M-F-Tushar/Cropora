package com.cropora

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.cropora.network.PredictionResponse
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private var setupDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.buttonOpenScan).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }

        findViewById<Button>(R.id.buttonOpenResult).setOnClickListener {
            val samplePrediction = PredictionResponse(
                modelLabel = getString(R.string.sample_model_label),
                disease = "Tomato Healthy",
                confidence = 0.96f,
                uncertain = false,
                guidanceAvailable = true,
                symptoms = getString(R.string.sample_symptoms),
                treatment = getString(R.string.sample_treatment),
                prevention = getString(R.string.sample_prevention)
            )
            startActivity(ResultActivity.createIntent(this, samplePrediction))
        }

        findViewById<Button>(R.id.buttonOpenHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        findViewById<Button>(R.id.buttonOpenLibrary).setOnClickListener {
            startActivity(Intent(this, DiseaseLibraryActivity::class.java))
        }

        findViewById<Button>(R.id.buttonOpenSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.buttonOpenAnalytics).setOnClickListener {
            startActivity(Intent(this, AnalyticsActivity::class.java))
        }
        if (!UiPreferences.isSetupComplete(this)) {
            showSetup(savedInstanceState)
        }
    }

    private fun showSetup(savedInstanceState: Bundle?) {
        val setupView = layoutInflater.inflate(R.layout.dialog_setup, null)
        val languageGroup = setupView.findViewById<RadioGroup>(R.id.groupSetupLanguage)
        val modeGroup = setupView.findViewById<RadioGroup>(R.id.groupSetupMode)
        languageGroup.check(if (resources.configuration.locales[0].language == "bn") {
            R.id.radioSetupBangla
        } else {
            R.id.radioSetupEnglish
        })
        modeGroup.check(if (UiPreferences.isAdvanced(this)) {
            R.id.radioSetupAdvanced
        } else {
            R.id.radioSetupSimple
        })
        savedInstanceState?.getInt("setup_language_choice", 0)?.takeIf { it != 0 }?.let(languageGroup::check)
        savedInstanceState?.getInt("setup_mode_choice", 0)?.takeIf { it != 0 }?.let(modeGroup::check)
        setupDialog = AlertDialog.Builder(this)
            .setTitle(R.string.setup_title)
            .setView(setupView)
            .setCancelable(false)
            .setPositiveButton(R.string.setup_continue) { _, _ ->
                UiPreferences.setAdvanced(this, modeGroup.checkedRadioButtonId == R.id.radioSetupAdvanced)
                UiPreferences.completeSetup(this)
                applyUiMode()
                UiPreferences.setLanguage(this,
                    if (languageGroup.checkedRadioButtonId == R.id.radioSetupBangla) "bn" else "en"
                )
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        setupDialog?.findViewById<RadioGroup>(R.id.groupSetupLanguage)?.let {
            outState.putInt("setup_language_choice", it.checkedRadioButtonId)
        }
        setupDialog?.findViewById<RadioGroup>(R.id.groupSetupMode)?.let {
            outState.putInt("setup_mode_choice", it.checkedRadioButtonId)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        setupDialog?.dismiss()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        applyUiMode()
    }

    private fun applyUiMode() {
        val advanced = UiPreferences.isAdvanced(this)
        listOf(
            R.id.workspaceHomeHeader,
            R.id.workspaceHomeWorkflow,
            R.id.workspaceHomeToolsIntro
        ).forEach { id ->
            findViewById<View>(id).visibility = if (advanced) View.VISIBLE else View.GONE
        }
        findViewById<View>(R.id.workspaceHomeNormalHeader).visibility =
            if (advanced) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.workspaceHomeToolsTitle).apply {
            setText(if (advanced) R.string.workspace_tools_title else R.string.quick_actions)
            ViewCompat.setAccessibilityHeading(this, true)
        }
        findViewById<Button>(R.id.buttonOpenResult).visibility = View.VISIBLE
        findViewById<Button>(R.id.buttonOpenAnalytics).visibility = View.VISIBLE
        listOf(
            R.id.buttonOpenScan, R.id.buttonOpenResult, R.id.buttonOpenHistory,
            R.id.buttonOpenLibrary, R.id.buttonOpenSettings, R.id.buttonOpenAnalytics
        ).forEach { id ->
            findViewById<Button>(id).apply {
                val normalHeight = if (id == R.id.buttonOpenScan) 56 else 52
                minHeight = (normalHeight * resources.displayMetrics.density).roundToInt()
                layoutParams = layoutParams.apply {
                    height = if (advanced) android.view.ViewGroup.LayoutParams.WRAP_CONTENT else minHeight
                }
            }
        }
        if (advanced) {
            findViewById<WorkflowDiagramView>(R.id.workspaceHomeDiagram).setSteps(
                listOf(
                    getString(R.string.workspace_step_capture),
                    getString(R.string.workspace_step_routes),
                    getString(R.string.workspace_step_assess),
                    getString(R.string.workspace_step_guide)
                ),
                activeStep = -1
            )
        }
    }
}