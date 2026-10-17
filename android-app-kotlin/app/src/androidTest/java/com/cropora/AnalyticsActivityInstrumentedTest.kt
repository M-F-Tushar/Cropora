package com.cropora

import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cropora.database.AppDatabase
import com.cropora.database.ScanRecord
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalyticsActivityInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun prepareDatabase() {
        runBlocking {
            AppDatabase.getInstance(context).clearAllTables()
        }
    }

    @After
    fun cleanupDatabase() {
        runBlocking {
            AppDatabase.getInstance(context).clearAllTables()
        }
    }

    @Test
    fun analyticsShowsEmptyStateWhenNoScansExist() {
        ActivityScenario.launch(AnalyticsActivity::class.java).use {
            onView(withId(R.id.textAnalyticsTitle)).check(matches(isDisplayed()))
            onView(withId(R.id.textAnalyticsEmpty)).check(matches(isDisplayed()))
            onView(withId(R.id.layoutAnalyticsContent)).check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun analyticsDisplaysCalculatedMetricsWhenScansExist() {
        runBlocking {
            val dao = AppDatabase.getInstance(context).scanDao()
            dao.insertScan(
                ScanRecord(
                    resultId = "scan-1",
                    modelLabel = "Tomato___Healthy",
                    disease = "Tomato Healthy",
                    confidence = 0.95f,
                    uncertain = false,
                    guidanceAvailable = true,
                    symptoms = "None",
                    treatment = "None",
                    prevention = "None",
                    latitude = 23.8103,
                    longitude = 90.4125,
                    timestamp = System.currentTimeMillis()
                )
            )
            dao.insertScan(
                ScanRecord(
                    resultId = "scan-2",
                    modelLabel = "Tomato___Early_blight",
                    disease = "Tomato Early Blight",
                    confidence = 0.85f,
                    uncertain = true,
                    guidanceAvailable = true,
                    symptoms = "Lesions",
                    treatment = "Fungicide",
                    prevention = "Crop rotation",
                    latitude = null,
                    longitude = null,
                    timestamp = System.currentTimeMillis(),
                )
            )
        }

        ActivityScenario.launch(AnalyticsActivity::class.java).use { scenario ->
            onView(withId(R.id.textAnalyticsTitle)).check(matches(isDisplayed()))
            onView(withId(R.id.textAnalyticsEmpty)).check(matches(not(isDisplayed())))
            onView(withId(R.id.layoutAnalyticsContent)).check(matches(isDisplayed()))

            scenario.onActivity { activity ->
                val totalScansText = activity.findViewById<TextView>(R.id.textTotalScans).text.toString()
                val healthyScansText = activity.findViewById<TextView>(R.id.textHealthyScans).text.toString()
                val diseasedScansText = activity.findViewById<TextView>(R.id.textDiseasedScans).text.toString()
                val avgConfidenceText = activity.findViewById<TextView>(R.id.textAverageConfidence).text.toString()
                val uncertainScansText = activity.findViewById<TextView>(R.id.textUncertainScans).text.toString()
                val locationScansText = activity.findViewById<TextView>(R.id.textLocationScans).text.toString()

                assertEquals(activity.getString(R.string.analytics_total_scans_format, 2), totalScansText)
                assertEquals(activity.getString(R.string.analytics_healthy_scans_format, 1), healthyScansText)
                assertEquals(activity.getString(R.string.analytics_diseased_scans_format, 1), diseasedScansText)
                assertEquals(activity.getString(R.string.analytics_avg_confidence_format, 90.0), avgConfidenceText)
                assertEquals(activity.getString(R.string.analytics_uncertain_scans_format, 1), uncertainScansText)
                assertEquals(activity.getString(R.string.analytics_location_scans_format, 1), locationScansText)
            }
        }
    }
}