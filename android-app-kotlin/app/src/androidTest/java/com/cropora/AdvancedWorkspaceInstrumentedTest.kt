package com.cropora

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cropora.database.AppDatabase
import com.cropora.database.ScanRecord
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdvancedWorkspaceInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("cropora_settings", Context.MODE_PRIVATE)
    private val originalAdvanced = preferences.all["advanced_ui_enabled"] as Boolean?
    private val originalSetup = preferences.all["setup_complete"] as Boolean?
    private val ownedScanIds = mutableListOf<Long>()

    @Before
    fun prepare() {
        UiPreferences.completeSetup(context)
        UiPreferences.setAdvanced(context, false)
    }

    @After
    fun restore() {
        try {
            runBlocking {
                ownedScanIds.forEach { AppDatabase.getInstance(context).scanDao().deleteScanById(it) }
            }
        } finally {
            preferences.edit().apply {
                originalAdvanced?.let { putBoolean("advanced_ui_enabled", it) }
                    ?: remove("advanced_ui_enabled")
                originalSetup?.let { putBoolean("setup_complete", it) }
                    ?: remove("setup_complete")
            }.commit()
        }
    }

    @Test
    fun homeRefreshesOnSettingsReturnAndNavigationRetainsBothModes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var originalHome: MainActivity
            scenario.onActivity { originalHome = it }
            listOf(false, true, false).forEach { advanced ->
                onView(withId(R.id.buttonOpenSettings)).perform(scrollTo(), click())
                if (UiPreferences.isAdvanced(context) != advanced) {
                    onView(withId(R.id.switchAdvancedMode)).perform(scrollTo(), click())
                }
                pressBack()
                scenario.onActivity { activity ->
                    // Exercise onResume on the same home instance, not a recreation.
                    assertSame(originalHome, activity)
                    assertHomeMode(activity, advanced)
                }

                onView(withId(R.id.buttonOpenScan)).perform(scrollTo(), click())
                onView(withId(R.id.workspaceScanHeader)).check(matches(withEffectiveVisibility(
                    if (advanced) Visibility.VISIBLE else Visibility.GONE
                )))
                onView(withId(R.id.radioCloudMode)).check(matches(isChecked()))
                assertEquals(advanced, UiPreferences.isAdvanced(context))
                pressBack()
                scenario.onActivity { assertHomeMode(it, advanced) }

                onView(withId(R.id.buttonOpenResult)).perform(scrollTo(), click())
                onView(withId(R.id.visualEvidencePanel)).check(matches(withEffectiveVisibility(
                    if (advanced) Visibility.VISIBLE else Visibility.GONE
                )))
                onView(withId(R.id.textResultModelLabel)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                onView(withId(R.id.textResultConfidence)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                assertEquals(advanced, UiPreferences.isAdvanced(context))
                pressBack()
                scenario.onActivity { assertHomeMode(it, advanced) }
            }
        }
    }

    @Test
    fun homeAlsoRefreshesWhenPreferenceChangesWhileStopped() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            listOf(false, true, false).forEach { advanced ->
                scenario.moveToState(Lifecycle.State.CREATED)
                UiPreferences.setAdvanced(context, advanced)
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity { assertHomeMode(it, advanced) }
            }
        }
    }

    @Test
    fun savedDetailKeepsDataAndUncertaintyAcrossModeRoundTrip() {
        val dao = AppDatabase.getInstance(context).scanDao()
        val record = ScanRecord(
            resultId = "workspace-test-${UUID.randomUUID()}",
            modelLabel = "Tomato___Early_blight",
            disease = "Tomato Early Blight",
            confidence = 0.87f,
            uncertain = true,
            guidanceAvailable = true,
            symptoms = "Workspace test symptoms",
            treatment = "Workspace test treatment",
            prevention = "Workspace test prevention",
            timestamp = 1_700_000_000_000L
        )
        val scanId = runBlocking { dao.insertScan(record) }
        if (scanId > 0) ownedScanIds.add(scanId)
        assertTrue("Could not seed the workspace test scan", scanId > 0)
        val intent = Intent(context, HistoryDetailActivity::class.java)
            .putExtra(HistoryDetailActivity.EXTRA_SCAN_ID, scanId)
        ActivityScenario.launch<HistoryDetailActivity>(intent).use { scenario ->
            var normalData: List<String>? = null
            listOf(false, true, false).forEach { advanced ->
                UiPreferences.setAdvanced(context, advanced)
                scenario.recreate()
                awaitSavedDetail(scenario)
                scenario.onActivity { activity ->
                    listOf(R.id.textDetailModelLabel, R.id.textDetailConfidence, R.id.textDetailUncertain).forEach { id ->
                        assertEquals(View.VISIBLE, activity.findViewById<View>(id).visibility)
                    }
                    assertEquals(
                        activity.getString(R.string.model_label_format, record.modelLabel),
                        activity.findViewById<TextView>(R.id.textDetailModelLabel).text.toString()
                    )
                    assertEquals(
                        activity.getString(R.string.history_confidence_format, record.confidence * 100f),
                        activity.findViewById<TextView>(R.id.textDetailConfidence).text.toString()
                    )
                    assertEquals(
                        activity.getString(R.string.result_uncertain),
                        activity.findViewById<TextView>(R.id.textDetailUncertain).text.toString()
                    )
                    assertEquals(
                        if (advanced) View.VISIBLE else View.GONE,
                        activity.findViewById<View>(R.id.visualEvidencePanel).visibility
                    )
                    val data = listOf(
                        R.id.textDetailDisease, R.id.textDetailModelLabel, R.id.textDetailConfidence,
                        R.id.textDetailUncertain, R.id.textDetailGuidanceStatus, R.id.textDetailTimestamp,
                        R.id.textDetailLocation, R.id.textDetailSymptoms, R.id.textDetailTreatment,
                        R.id.textDetailPrevention
                    ).map { activity.findViewById<TextView>(it).text.toString() }
                    if (normalData == null) normalData = data else assertEquals(normalData, data)
                    if (advanced) {
                        assertEquals(
                            activity.getString(R.string.visual_confidence_value, 87),
                            activity.findViewById<TextView>(R.id.visualConfidenceValue).text.toString()
                        )
                        assertEquals(
                            activity.getString(R.string.visual_review_required),
                            activity.findViewById<TextView>(R.id.visualUncertaintyStatus).text.toString()
                        )
                        assertEquals(
                            activity.findViewById<TextView>(R.id.textDetailGuidanceStatus).text.toString(),
                            activity.findViewById<TextView>(R.id.visualGuidanceSource).text.toString()
                        )
                    }
                }
                assertEquals(record.copy(id = scanId), runBlocking { dao.getScanById(scanId) })
            }
        }
    }

    private fun assertHomeMode(activity: MainActivity, advanced: Boolean) {
        assertEquals(advanced, UiPreferences.isAdvanced(activity))
        listOf(R.id.workspaceHomeHeader, R.id.workspaceHomeWorkflow).forEach { id ->
            assertEquals(if (advanced) View.VISIBLE else View.GONE, activity.findViewById<View>(id).visibility)
        }
        listOf(R.id.buttonOpenResult, R.id.buttonOpenAnalytics).forEach { id ->
            assertEquals(View.VISIBLE, activity.findViewById<View>(id).visibility)
        }
        if (advanced) {
            val diagram = activity.findViewById<View>(R.id.workspaceHomeDiagram)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, diagram.importantForAccessibility)
            val description = diagram.contentDescription?.toString().orEmpty()
            var previousIndex = -1
            listOf(
                R.string.workspace_step_capture, R.string.workspace_step_routes,
                R.string.workspace_step_assess, R.string.workspace_step_guide
            ).forEach { step ->
                val index = description.indexOf(activity.getString(step))
                assertTrue("Missing or unordered accessible home step: $description", index > previousIndex)
                previousIndex = index
            }
        }
    }

    private fun awaitSavedDetail(scenario: ActivityScenario<HistoryDetailActivity>) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            var loaded = false
            scenario.onActivity { loaded = it.findViewById<View>(R.id.buttonDeleteHistory).isEnabled }
            if (loaded) return
            SystemClock.sleep(25)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Saved detail did not finish loading within 5 seconds")
    }
}
