package com.cropora

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsActivityInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val originalAdvanced = UiPreferences.isAdvanced(context)

    @get:Rule
    val activityRule = ActivityScenarioRule(SettingsActivity::class.java)

    @After
    fun restoreMode() {
        UiPreferences.setAdvanced(context, originalAdvanced)
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
}
