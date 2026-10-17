package com.cropora

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cropora.data.DiseasePresentation
import com.cropora.network.PredictionResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultActivityInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("cropora_settings", Context.MODE_PRIVATE)
    private val originalAdvanced = preferences.all["advanced_ui_enabled"] as Boolean?

    @Before
    fun resetMode() {
        preferences.edit().remove("advanced_ui_enabled").commit()
    }

    @After
    fun restoreMode() {
        preferences.edit().apply {
            originalAdvanced?.let { putBoolean("advanced_ui_enabled", it) }
                ?: remove("advanced_ui_enabled")
        }.commit()
    }

    @Test
    fun normalIsDefaultAndSwitchingModesKeepsTheSameResult() {
        assertFalse(UiPreferences.isAdvanced(context))
        ActivityScenario.launch<ResultActivity>(
            ResultActivity.createIntent(context, prediction())
        ).use { scenario ->
            var normalData: List<String>? = null
            listOf(false, true, false).forEach { advanced ->
                UiPreferences.setAdvanced(context, advanced)
                scenario.recreate()
                awaitGuidance(scenario)
                scenario.onActivity { activity ->
                    fun text(id: Int) = activity.findViewById<TextView>(id).text.toString()
                    listOf(
                        R.id.textResultModelLabel,
                        R.id.textResultConfidence,
                        R.id.progressResultConfidence
                    ).forEach { viewId ->
                        assertEquals(View.VISIBLE, activity.findViewById<View>(viewId).visibility)
                    }
                    assertEquals(87, activity.findViewById<ProgressBar>(R.id.progressResultConfidence).progress)
                    assertEquals(
                        activity.getString(R.string.result_confident),
                        text(R.id.textResultStatus)
                    )
                    if (advanced) {
                        assertEquals("Tomato___Early_blight", text(R.id.textResultModelLabel))
                        assertAdvancedHero(activity, uncertain = false)
                    } else {
                        assertEquals(
                            activity.getString(R.string.model_label_format, "Tomato___Early_blight"),
                            text(R.id.textResultModelLabel)
                        )
                        assertEquals(activity.getString(R.string.confidence_format, 87), text(R.id.textResultConfidence))
                    }
                    val care = if (advanced) {
                        listOf(R.id.advCareSigns, R.id.advCareTreatment, R.id.advCarePrevention)
                    } else {
                        listOf(R.id.textResultSymptoms, R.id.textResultTreatment, R.id.textResultPrevention)
                    }
                    val data = (listOf(R.id.textResultDisease, R.id.textResultStatus, R.id.textGuidanceStatus) + care)
                        .map(::text)
                    assertEquals(
                        DiseasePresentation.name(activity, "Tomato Early Blight", "Tomato___Early_blight"),
                        data.first()
                    )
                    if (normalData == null) normalData = data else assertEquals(normalData, data)
                }
            }
        }
    }

    @Test
    fun uncertaintyWarningRemainsVisibleAcrossModeRoundTrip() {
        ActivityScenario.launch<ResultActivity>(
            ResultActivity.createIntent(context, prediction(uncertain = true))
        ).use { scenario ->
            listOf(false, true, false).forEach { advanced ->
                UiPreferences.setAdvanced(context, advanced)
                scenario.recreate()
                awaitGuidance(scenario)
                scenario.onActivity { activity ->
                    val status = activity.findViewById<TextView>(R.id.textResultStatus)
                    assertEquals(View.VISIBLE, status.visibility)
                    assertEquals(activity.getString(R.string.result_uncertain), status.text.toString())
                    if (advanced) assertAdvancedHero(activity, uncertain = true)
                }
            }
        }
    }

    @Test
    fun advancedGuidanceSourceFollowsAsyncLocalGuidance() {
        UiPreferences.setAdvanced(context, true)
        ActivityScenario.launch<ResultActivity>(
            ResultActivity.createIntent(context, prediction().copy(guidanceAvailable = false))
        ).use { scenario ->
            awaitGuidance(scenario)
            scenario.onActivity { activity ->
                assertEquals(
                    activity.getString(R.string.guidance_local_library),
                    activity.findViewById<TextView>(R.id.textGuidanceStatus).text.toString()
                )
                assertAdvancedHero(activity, uncertain = false)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.advResultPlant).visibility)
            }
        }
    }

    @Test
    fun resultShowsShareLocationAndSaveControls() {
        ActivityScenario.launch<ResultActivity>(
            ResultActivity.createIntent(context, prediction())
        ).use {
            onView(withId(R.id.buttonShareResult))
                .perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(withId(R.id.checkboxIncludeLocation))
                .perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(withId(R.id.buttonSaveHistory))
                .perform(scrollTo())
                .check(matches(isDisplayed()))
        }
    }

    private fun assertAdvancedHero(activity: ResultActivity, uncertain: Boolean) {
        val value = activity.findViewById<TextView>(R.id.textResultConfidence)
        assertEquals(activity.getString(R.string.adv_percent, 87), value.text.toString())
        assertEquals(activity.getString(R.string.confidence_format, 87), value.contentDescription.toString())
        assertEquals(
            activity.getString(if (uncertain) R.string.adv_status_review else R.string.adv_status_disease),
            activity.findViewById<TextView>(R.id.advResultStatusPill).text.toString()
        )
        assertEquals(
            if (uncertain) View.VISIBLE else View.GONE,
            activity.findViewById<View>(R.id.advResultReviewBanner).visibility
        )
    }

    @Test
    fun advancedResultShowsShareLocationAndSaveControls() {
        UiPreferences.setAdvanced(context, true)
        ActivityScenario.launch<ResultActivity>(
            ResultActivity.createIntent(context, prediction())
        ).use {
            listOf(R.id.buttonShareResult, R.id.checkboxIncludeLocation, R.id.buttonSaveHistory).forEach { id ->
                onView(withId(id)).check(matches(isDisplayed()))
            }
        }
    }

    private fun awaitGuidance(scenario: ActivityScenario<ResultActivity>) {
        // Espresso does not track the lifecycle coroutine's IO dispatcher.
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            var loaded = false
            scenario.onActivity { loaded = it.findViewById<View>(R.id.buttonSaveHistory).isEnabled }
            if (loaded) return
            SystemClock.sleep(25)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Result guidance did not finish loading within 5 seconds")
    }

    private fun prediction(uncertain: Boolean = false) = PredictionResponse(
        modelLabel = "Tomato___Early_blight",
        disease = "Tomato Early Blight",
        confidence = 0.87f,
        uncertain = uncertain,
        guidanceAvailable = true,
        symptoms = "Symptoms",
        treatment = "Treatment",
        prevention = "Prevention"
    )
}
