package com.cropora

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cropora.data.Disease
import com.cropora.data.DiseaseRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException

class DiseaseDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_disease_detail)

        val diseaseName = intent.getStringExtra(EXTRA_DISEASE_NAME)
        if (diseaseName.isNullOrBlank()) {
            showErrorAndFinish(R.string.disease_invalid_name)
            return
        }
        loadDisease(diseaseName)
    }

    private fun loadDisease(name: String) {
        lifecycleScope.launch {
            try {
                val disease = withContext(Dispatchers.IO) {
                    DiseaseRepository.getInstance(applicationContext).findByName(name)
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
        findViewById<TextView>(R.id.textDiseaseDetailName).text = disease.name
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
