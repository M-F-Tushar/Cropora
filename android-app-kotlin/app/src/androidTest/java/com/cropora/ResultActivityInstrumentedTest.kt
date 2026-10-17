package com.cropora

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    private val originalAdvanced = UiPreferences.isAdvanced(context)

    @Before
    fun resetMode() {
        context.getSharedPreferences("cropora_settings", Context.MODE_PRIVATE)
            .edit().remove("advanced_ui_enabled").commit()
    }

    @After
    fun restoreMode() {
        UiPreferences.setAdvanced(context, originalAdvanced)
    }

    @Test
    fun simpleIsDefaultAndSwitchingModesKeepsTheSameResult() {
        assertFalse(UiPreferences.isAdvanced(context))
        ActivityScenario.launch<ResultActivity>(
            ResultActivity.createIntent(context, prediction())
        ).use { scenario ->
            listOf(false, true, false).forEach { advanced ->
                UiPreferences.setAdvanced(context, advanced)
                scenario.recreate()
                scenario.onActivity { activity ->
                    val visibility = if (advanced) View.VISIBLE else View.GONE
                    listOf(
                        R.id.textResultModelLabel,
                        R.id.textResultConfidence,
                        R.id.progressResultConfidence
                    ).forEach { viewId ->
                        assertEquals(visibility, activity.findViewById<View>(viewId).visibility)
                    }
                    assertEquals(
                        "Tomato Early Blight",
                        activity.findViewById<TextView>(R.id.textResultDisease).text.toString()
                    )
                    assertEquals(
                        activity.getString(R.string.confidence_format, 87),
                        activity.findViewById<TextView>(R.id.textResultConfidence).text.toString()
                    )
                    assertEquals(
                        activity.getString(
                            if (advanced) R.string.result_confident else R.string.result_simple_suggestion
                        ),
                        activity.findViewById<TextView>(R.id.textResultStatus).text.toString()
                    )
                }
            }
        }
    }

    @Test
    fun uncertaintyWarningRemainsVisibleInBothModes() {
        ActivityScenario.launch<ResultActivity>(
            ResultActivity.createIntent(context, prediction(uncertain = true))
        ).use { scenario ->
            listOf(false, true).forEach { advanced ->
                UiPreferences.setAdvanced(context, advanced)
                scenario.recreate()
                scenario.onActivity { activity ->
                    val status = activity.findViewById<TextView>(R.id.textResultStatus)
                    assertEquals(View.VISIBLE, status.visibility)
                    assertEquals(activity.getString(R.string.result_uncertain), status.text.toString())
                }
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
