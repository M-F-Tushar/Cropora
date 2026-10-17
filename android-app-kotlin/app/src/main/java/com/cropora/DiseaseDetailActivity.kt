package com.cropora

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import com.cropora.data.Disease
import com.cropora.data.DiseasePresentation
import com.cropora.data.DiseaseRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException
import kotlin.math.roundToInt

class DiseaseDetailActivity : AppCompatActivity() {
    private data class TextAppearance(
        val size: Float,
        val colors: ColorStateList,
        val typeface: Typeface?,
        val lineSpacingExtra: Float,
        val lineSpacingMultiplier: Float
    )

    private val normalTextAppearance = mutableMapOf<Int, TextAppearance>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_disease_detail)
        listOf(
            R.id.textDiseaseDetailName, R.id.textDiseaseDetailPlant,
            R.id.referenceDetailSignsHeading, R.id.referenceDetailActionHeading,
            R.id.referenceDetailPreventionHeading, R.id.textDiseaseDetailSymptoms,
            R.id.textDiseaseDetailTreatment, R.id.textDiseaseDetailPrevention
        ).forEach { id ->
            val text = findViewById<TextView>(id)
            normalTextAppearance[id] = TextAppearance(
                text.textSize, text.textColors, text.typeface,
                text.lineSpacingExtra, text.lineSpacingMultiplier
            )
        }
        listOf(
            R.id.textDiseaseDetailName, R.id.referenceDetailSignsHeading,
            R.id.referenceDetailActionHeading, R.id.referenceDetailPreventionHeading
        ).forEach { id -> ViewCompat.setAccessibilityHeading(findViewById(id), true) }

        val diseaseName = intent.getStringExtra(EXTRA_DISEASE_NAME)
        if (diseaseName.isNullOrBlank()) {
            showErrorAndFinish(R.string.disease_invalid_name)
            return
        }
        loadDisease(diseaseName)
    }

    override fun onResume() {
        super.onResume()
        val advanced = UiPreferences.isAdvanced(this)
        val padding = if (advanced) dp(20) else 0
        findViewById<View>(R.id.referenceDetailHeader).apply {
            if (advanced) setBackgroundResource(R.drawable.reference_header) else background = null
            setPadding(padding, padding, padding, padding)
        }
        listOf(R.id.referenceDetailEyebrow, R.id.referenceDetailIntro).forEach { id ->
            findViewById<View>(id).visibility = if (advanced) View.VISIBLE else View.GONE
        }
        styleText(R.id.textDiseaseDetailName, advanced, 28f, "#FFFFFF", bold = true)
        styleText(R.id.textDiseaseDetailPlant, advanced, 16f, "#D3EEE1")
        styleSection(
            advanced, R.id.referenceDetailSigns, R.drawable.reference_card_signs,
            R.id.referenceDetailSignsHeading, R.string.reference_detail_signs, R.string.symptoms_heading,
            R.id.textDiseaseDetailSymptoms, "#70460D"
        )
        styleSection(
            advanced, R.id.referenceDetailAction, R.drawable.reference_card_action,
            R.id.referenceDetailActionHeading, R.string.reference_detail_action, R.string.treatment_heading,
            R.id.textDiseaseDetailTreatment, "#1E5941"
        )
        styleSection(
            advanced, R.id.referenceDetailPrevention, R.drawable.reference_card_evidence,
            R.id.referenceDetailPreventionHeading, R.string.reference_detail_prevention, R.string.prevention_heading,
            R.id.textDiseaseDetailPrevention, "#23476C"
        )
    }

    private fun styleSection(
        advanced: Boolean,
        containerId: Int,
        backgroundId: Int,
        headingId: Int,
        advancedHeading: Int,
        normalHeading: Int,
        bodyId: Int,
        accent: String
    ) {
        findViewById<View>(containerId).apply {
            if (advanced) setBackgroundResource(backgroundId) else background = null
            val padding = if (advanced) dp(20) else 0
            setPadding(padding, padding, padding, padding)
        }
        findViewById<TextView>(headingId).setText(if (advanced) advancedHeading else normalHeading)
        styleText(headingId, advanced, 18f, accent, bold = true)
        styleText(bodyId, advanced, 16f, "#34434B")
        findViewById<TextView>(bodyId).apply {
            layoutParams = (layoutParams as LinearLayout.LayoutParams).apply {
                topMargin = if (advanced) dp(8) else 0
            }
            if (advanced) setLineSpacing(dp(3).toFloat(), 1f)
        }
    }

    private fun styleText(id: Int, advanced: Boolean, size: Float, color: String, bold: Boolean = false) {
        val normal = normalTextAppearance.getValue(id)
        findViewById<TextView>(id).apply {
            if (advanced) {
                textSize = size
                setTextColor(Color.parseColor(color))
                setTypeface(normal.typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
            } else {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, normal.size)
                setTextColor(normal.colors)
                typeface = normal.typeface
                setLineSpacing(normal.lineSpacingExtra, normal.lineSpacingMultiplier)
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun loadDisease(name: String) {
        lifecycleScope.launch {
            try {
                val disease = withContext(Dispatchers.IO) {
                    DiseaseRepository.getInstance(applicationContext).findLocalizedByName(this@DiseaseDetailActivity, name)
                }
                if (disease == null) {
                    showErrorAndFinish(R.string.disease_not_found)
                } else {
                    renderDisease(disease)
                }
            } catch (exception: IOException) {
                showErrorAndFinish(R.string.disease_library_error)
            } catch (exception: XmlPullParserException) {
                showErrorAndFinish(R.string.disease_library_error)
            }
        }
    }

    private fun renderDisease(disease: Disease) {
        findViewById<TextView>(R.id.textDiseaseDetailName).text = DiseasePresentation.name(this, disease.name)
        findViewById<TextView>(R.id.textDiseaseDetailPlant).text = getString(
            R.string.disease_plant_format,
            disease.plant
        )
        findViewById<TextView>(R.id.textDiseaseDetailSymptoms).text = disease.symptoms
        findViewById<TextView>(R.id.textDiseaseDetailTreatment).text = disease.treatment
        findViewById<TextView>(R.id.textDiseaseDetailPrevention).text = disease.prevention
    }

    private fun showErrorAndFinish(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_DISEASE_NAME = "extra_disease_name"
    }
}
