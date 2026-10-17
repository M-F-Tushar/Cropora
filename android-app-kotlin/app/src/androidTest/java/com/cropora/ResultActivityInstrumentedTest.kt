package com.cropora

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cropora.network.PredictionResponse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultActivityInstrumentedTest {
    @Test
    fun resultShowsShareLocationAndSaveControls() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prediction = PredictionResponse(
            modelLabel = "Tomato___Early_blight",
            disease = "Tomato Early Blight",
            confidence = 0.87f,
            uncertain = false,
            guidanceAvailable = true,
            symptoms = "Symptoms",
            treatment = "Treatment",
            prevention = "Prevention"
        )

        ActivityScenario.launch<ResultActivity>(
            ResultActivity.createIntent(context, prediction)
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
}
