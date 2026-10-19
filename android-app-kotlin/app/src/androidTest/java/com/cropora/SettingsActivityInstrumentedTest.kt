package com.cropora

import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.slider.Slider
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsActivityInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val originalAdvanced = UiPreferences.isAdvanced(context)
    private val originalThreshold = UiPreferences.getConfidenceThreshold(context)

    @get:Rule
    val activityRule = ActivityScenarioRule(SettingsActivity::class.java)

    @After
    fun restoreSettings() {
        UiPreferences.setAdvanced(context, originalAdvanced)
        UiPreferences.setConfidenceThreshold(context, originalThreshold)
    }

    @Test
    fun advancedModePersistsAndCanBeTurnedOff() {
        UiPreferences.setAdvanced(context, false)
        activityRule.scenario.recreate()
        onView(withId(R.id.switchAdvancedMode)).check(matches(not(isChecked())))
        onView(withId(R.id.switchAdvancedMode)).perform(click())
        activityRule.scenario.recreate()
        onView(withId(R.id.switchAdvancedMode)).check(matches(isChecked()))
        onView(withId(R.id.switchAdvancedMode)).perform(click())
        activityRule.scenario.recreate()
        onView(withId(R.id.switchAdvancedMode)).check(matches(not(isChecked())))
    }

    @Test
    fun reminderSwitchIsVisible() {
        onView(withId(R.id.switchScanReminders)).check(matches(isDisplayed()))
    }

    @Test
    fun savedConfidenceThresholdIsShownAfterRecreate() {
        UiPreferences.setConfidenceThreshold(context, 0.70f)
        activityRule.scenario.recreate()
        activityRule.scenario.onActivity { activity ->
            assertEquals(70f, activity.findViewById<Slider>(R.id.sliderConfidenceThreshold).value, 0f)
            assertEquals(
                activity.getString(R.string.settings_confidence_threshold_format, 70),
                activity.findViewById<TextView>(R.id.textConfidenceThresholdValue).text.toString()
            )
        }
    }
}
