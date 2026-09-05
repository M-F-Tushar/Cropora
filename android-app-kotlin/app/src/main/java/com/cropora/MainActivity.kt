package com.cropora

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.cropora.network.PredictionResponse

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.buttonOpenScan).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }

        findViewById<Button>(R.id.buttonOpenResult).setOnClickListener {
            val samplePrediction = PredictionResponse(
                modelLabel = getString(R.string.sample_model_label),
                disease = getString(R.string.sample_disease),
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
    }
}