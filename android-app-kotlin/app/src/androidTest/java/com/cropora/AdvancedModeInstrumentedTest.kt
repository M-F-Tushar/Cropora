package com.cropora

import android.app.Activity
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
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cropora.database.AppDatabase
import com.cropora.database.ScanRecord
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdvancedModeInstrumentedTest {
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
    fun homeSwitchesLayoutWhenModeChangesInSettings() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertHomeMode(it, advanced = false) }

            onView(withId(R.id.buttonOpenSettings)).perform(scrollTo(), click())
            onView(withId(R.id.switchAdvancedMode)).perform(scrollTo(), click())
            // Settings switches its own layout immediately.
            onView(withId(R.id.advSettingsVersion)).perform(scrollTo()).check(matches(isDisplayed()))
            pressBack()
            onView(withId(R.id.advBottomBar)).check(matches(isDisplayed()))
            scenario.onActivity { assertHomeMode(it, advanced = true) }

            onView(withId(R.id.advHomeSettings)).perform(click())
            onView(withId(R.id.switchAdvancedMode)).perform(scrollTo(), click())
            pressBack()
            onView(withId(R.id.buttonOpenScan)).check(matches(isDisplayed()))
            scenario.onActivity { assertHomeMode(it, advanced = false) }
        }
    }

    @Test
    fun homeFollowsPreferenceChangedWhileStopped() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            listOf(true, false, true).forEach { advanced ->
                scenario.moveToState(Lifecycle.State.CREATED)
                UiPreferences.setAdvanced(context, advanced)
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity { assertHomeMode(it, advanced) }
            }
        }
    }

    @Test
    fun advancedTabsNavigateAndBackReturnsHome() {
        UiPreferences.setAdvanced(context, true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.advHomeHero)).check(matches(isDisplayed()))
            onView(withId(R.id.advTabHome)).check(matches(isSelected()))

            onView(withId(R.id.advTabScans)).perform(click())
            onView(withId(R.id.advScansFilter)).check(matches(isDisplayed()))
            onView(withId(R.id.advTabScans)).check(matches(isSelected()))

            onView(withId(R.id.advTabLibrary)).perform(click())
            onView(withId(R.id.advLibrarySearch)).check(matches(isDisplayed()))
            awaitCondition(scenario, "Library did not load") { activity ->
                activity.findViewById<View>(R.id.advLibraryProgress).visibility == View.GONE
            }
            scenario.onActivity { activity ->
                val count = activity.findViewById<TextView>(R.id.advLibraryCount).text.toString()
                assertEquals(activity.resources.getQuantityString(R.plurals.adv_library_count, 38, 38), count)
            }

            onView(withId(R.id.advTabInsights)).perform(click())
            onView(withId(R.id.advTabInsights)).check(matches(isSelected()))

            // The selected tab survives recreation.
            scenario.recreate()
            onView(withId(R.id.advTabInsights)).check(matches(isSelected()))

            pressBack()
            onView(withId(R.id.advHomeHero)).check(matches(isDisplayed()))
            onView(withId(R.id.advTabHome)).check(matches(isSelected()))
        }
    }

    @Test
    fun tabExtraOpensRequestedTab() {
        UiPreferences.setAdvanced(context, true)
        val intent = MainActivity.tabIntent(context, AdvancedTab.LIBRARY)
        ActivityScenario.launch<MainActivity>(intent).use {
            onView(withId(R.id.advTabLibrary)).check(matches(isSelected()))
            onView(withId(R.id.advLibrarySearch)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun advancedScanPreselectsOfflineEngineAndShowsPlaceholder() {
        UiPreferences.setAdvanced(context, true)
        val intent = Intent(context, ScanActivity::class.java).putExtra(ScanActivity.EXTRA_OFFLINE, true)
        ActivityScenario.launch<ScanActivity>(intent).use {
            onView(withId(R.id.radioOfflineMode)).check(matches(isChecked()))
            onView(withId(R.id.advScanPlaceholder)).check(matches(isDisplayed()))
            onView(withId(R.id.buttonDetectDisease)).check(matches(not(isEnabled())))
            onView(withId(R.id.buttonTakePhoto)).check(matches(isDisplayed()))
            onView(withId(R.id.buttonChooseGallery)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun savedDetailKeepsDataAndUncertaintyAcrossModeRoundTrip() {
        val dao = AppDatabase.getInstance(context).scanDao()
        val record = ScanRecord(
            resultId = "advanced-test-${UUID.randomUUID()}",
            modelLabel = "Tomato___Early_blight",
            disease = "Tomato Early Blight",
            confidence = 0.87f,
            uncertain = true,
            guidanceAvailable = true,
            symptoms = "Advanced test symptoms",
            treatment = "Advanced test treatment",
            prevention = "Advanced test prevention",
            timestamp = 1_700_000_000_000L
        )
        val scanId = runBlocking { dao.insertScan(record) }
        if (scanId > 0) ownedScanIds.add(scanId)
        assertTrue("Could not seed the test scan", scanId > 0)
        val intent = Intent(context, HistoryDetailActivity::class.java)
            .putExtra(HistoryDetailActivity.EXTRA_SCAN_ID, scanId)
        ActivityScenario.launch<HistoryDetailActivity>(intent).use { scenario ->
            listOf(false, true, false).forEach { advanced ->
                UiPreferences.setAdvanced(context, advanced)
                scenario.recreate()
                awaitCondition(scenario, "Saved detail did not finish loading") { activity ->
                    activity.findViewById<View>(R.id.buttonDeleteHistory).isEnabled
                }
                scenario.onActivity { activity ->
                    fun text(id: Int) = activity.findViewById<TextView>(id).text.toString()
                    val uncertainText = activity.findViewById<TextView>(R.id.textDetailUncertain)
                    assertEquals(activity.getString(R.string.result_uncertain), uncertainText.text.toString())
                    if (advanced) {
                        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.advDetailReviewBanner).visibility)
                        assertEquals(record.modelLabel, text(R.id.textDetailModelLabel))
                        assertEquals(activity.getString(R.string.adv_percent, 87), text(R.id.textDetailConfidence))
                        assertEquals(activity.getString(R.string.adv_status_review), text(R.id.advDetailStatusPill))
                        assertEquals(record.symptoms, text(R.id.advCareSigns))
                        assertEquals(record.treatment, text(R.id.advCareTreatment))
                        assertEquals(record.prevention, text(R.id.advCarePrevention))
                        assertEquals(activity.getString(R.string.adv_location_none), text(R.id.textDetailLocation))
                    } else {
                        assertEquals(View.VISIBLE, uncertainText.visibility)
                        assertEquals(
                            activity.getString(R.string.model_label_format, record.modelLabel),
                            text(R.id.textDetailModelLabel)
                        )
                        assertEquals(
                            activity.getString(R.string.history_confidence_format, record.confidence * 100f),
                            text(R.id.textDetailConfidence)
                        )
                        assertEquals(record.symptoms, text(R.id.textDetailSymptoms))
                    }
                }
                assertEquals(record.copy(id = scanId), runBlocking { dao.getScanById(scanId) })
            }
        }
    }

    private fun assertHomeMode(activity: MainActivity, advanced: Boolean) {
        assertEquals(advanced, UiPreferences.isAdvanced(activity))
        if (advanced) {
            assertNotNull(activity.findViewById<View>(R.id.advBottomBar))
            assertNull(activity.findViewById<View>(R.id.buttonOpenScan))
        } else {
            assertNull(activity.findViewById<View>(R.id.advBottomBar))
            listOf(
                R.id.buttonOpenScan, R.id.buttonOpenResult, R.id.buttonOpenHistory,
                R.id.buttonOpenLibrary, R.id.buttonOpenSettings, R.id.buttonOpenAnalytics
            ).forEach { id ->
                assertEquals(View.VISIBLE, activity.findViewById<View>(id).visibility)
            }
        }
    }

    private fun <T : Activity> awaitCondition(
        scenario: ActivityScenario<T>,
        message: String,
        condition: (T) -> Boolean
    ) {
        // Espresso does not track the lifecycle coroutines' IO dispatcher.
        val deadline = SystemClock.uptimeMillis() + 5_000
        var done = false
        while (!done && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { done = condition(it) }
            if (!done) SystemClock.sleep(25)
        }
        if (!done) throw AssertionError("$message within 5 seconds")
    }
}
