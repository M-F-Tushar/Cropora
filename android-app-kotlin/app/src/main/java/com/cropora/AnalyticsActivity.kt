package com.cropora

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cropora.data.DiseasePresentation
import com.cropora.database.AppDatabase
import com.cropora.database.ScanRecord
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class AnalyticsActivity : AppCompatActivity() {

    private lateinit var textAnalyticsEmpty: TextView
    private lateinit var layoutAnalyticsContent: View
    private lateinit var textTotalScans: TextView
    private lateinit var textHealthyScans: TextView
    private lateinit var textDiseasedScans: TextView
    private lateinit var textAverageConfidence: TextView
    private lateinit var textUncertainScans: TextView
    private lateinit var textLocationScans: TextView
    private lateinit var textTopDisease: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        textAnalyticsEmpty = findViewById(R.id.textAnalyticsEmpty)
        layoutAnalyticsContent = findViewById(R.id.layoutAnalyticsContent)
        textTotalScans = findViewById(R.id.textTotalScans)
        textHealthyScans = findViewById(R.id.textHealthyScans)
        textDiseasedScans = findViewById(R.id.textDiseasedScans)
        textAverageConfidence = findViewById(R.id.textAverageConfidence)
        textUncertainScans = findViewById(R.id.textUncertainScans)
        textLocationScans = findViewById(R.id.textLocationScans)
        textTopDisease = findViewById(R.id.textTopDisease)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDatabase.getInstance(applicationContext)
                    .scanDao()
                    .observeAllScans()
                    .catch {
                        showEmptyState()
                    }
                    .collect { scans ->
                        if (scans.isEmpty()) {
                            showEmptyState()
                        } else {
                            displayAnalytics(scans)
                        }
                    }
            }
        }
    }

    private fun showEmptyState() {
        textAnalyticsEmpty.visibility = View.VISIBLE
        layoutAnalyticsContent.visibility = View.GONE
    }

    private fun displayAnalytics(scans: List<ScanRecord>) {
        textAnalyticsEmpty.visibility = View.GONE
        layoutAnalyticsContent.visibility = View.VISIBLE

        val insights = ScanInsights.from(scans)
        val topDiseaseEntry = insights.topResults.maxByOrNull { it.second }
        val topDiseaseName = if (topDiseaseEntry != null) {
            val sample = topDiseaseEntry.first
            DiseasePresentation.name(this, sample.disease, sample.modelLabel)
        } else {
            getString(R.string.analytics_no_disease_detected)
        }

        textTotalScans.text = getString(R.string.analytics_total_scans_format, insights.total)
        textHealthyScans.text = getString(R.string.analytics_healthy_scans_format, insights.healthy)
        textDiseasedScans.text = getString(R.string.analytics_diseased_scans_format, insights.diseased)
        textAverageConfidence.text = getString(
            R.string.analytics_avg_confidence_format,
            insights.averageConfidence * 100.0
        )
        textUncertainScans.text = getString(R.string.analytics_uncertain_scans_format, insights.uncertain)
        textLocationScans.text = getString(R.string.analytics_location_scans_format, insights.withLocation)
        textTopDisease.text = getString(R.string.analytics_top_disease_format, topDiseaseName)
    }
}
