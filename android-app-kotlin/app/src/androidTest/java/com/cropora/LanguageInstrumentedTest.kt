package com.cropora

import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cropora.data.DiseasePresentation
import com.cropora.data.DiseaseRepository
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("cropora_settings", Context.MODE_PRIVATE)
    private var originalLocales = LocaleListCompat.getEmptyLocaleList()
    private var originalSetup = false
    private var originalAdvanced = false
    private var originalLanguage: String? = null

    @Before
    fun prepare() {
        originalSetup = UiPreferences.isSetupComplete(context)
        originalAdvanced = UiPreferences.isAdvanced(context)
        originalLanguage = preferences.getString("app_language", null)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            originalLocales = AppCompatDelegate.getApplicationLocales()
            UiPreferences.setLanguage(context, "en")
        }
    }

    @After
    fun restore() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(originalLocales)
        }
        preferences.edit().putBoolean("setup_complete", originalSetup)
            .putBoolean("advanced_ui_enabled", originalAdvanced)
            .putString("app_language", originalLanguage).commit()
    }

    @Test
    fun setupPersistsLanguageAndModeAndDoesNotReappear() {
        preferences.edit().putBoolean("setup_complete", false).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.radioSetupBangla)).perform(click())
            onView(withId(R.id.radioSetupAdvanced)).perform(click())
            scenario.recreate()
            onView(withId(R.id.radioSetupBangla)).check(matches(isChecked()))
            onView(withId(R.id.radioSetupAdvanced)).check(matches(isChecked()))
            onView(withText(R.string.setup_continue)).perform(click())
            onView(withId(R.id.radioSetupBangla)).check(doesNotExist())
            onView(withId(R.id.buttonOpenAnalytics)).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                assertEquals("bn", activity.resources.configuration.locales[0].language)
                assertTrue(UiPreferences.isAdvanced(activity))
                assertTrue(UiPreferences.isSetupComplete(activity))
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.radioSetupBangla)).check(doesNotExist())
            scenario.onActivity { activity ->
                assertEquals("bn", activity.resources.configuration.locales[0].language)
            }
        }
    }

    @Test
    fun settingsSwitchesBothDirectionsWithoutChangingMode() {
        UiPreferences.completeSetup(context)
        UiPreferences.setAdvanced(context, true)
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            onView(withId(R.id.radioBangla)).perform(click())
            onView(withId(R.id.radioBangla)).check(matches(isChecked()))
            onView(withId(R.id.textSettingsTitle)).check(matches(withText("সেটিংস")))
            scenario.recreate()
            onView(withId(R.id.radioBangla)).check(matches(isChecked()))
            onView(withId(R.id.switchAdvancedMode)).check(matches(isChecked()))
            onView(withId(R.id.radioEnglish)).perform(click())
            onView(withId(R.id.textSettingsTitle)).check(matches(withText("Settings")))
            onView(withId(R.id.switchAdvancedMode)).perform(click())
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("en", activity.resources.configuration.locales[0].language)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.buttonOpenAnalytics).visibility)
            }
        }
    }

    @Test
    fun libraryAndModelNamesFollowLocaleWithoutChangingCanonicalIdentifiers() {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("bn"))
        }
        val bangla = context.createConfigurationContext(configuration)
        val repository = DiseaseRepository.getInstance(context)
        assertEquals(38, repository.getAllDiseases().size)
        assertEquals(38, repository.getLocalizedDiseases(bangla).size)
        context.resources.getStringArray(R.array.disease_keys).forEach { canonical ->
            val local = repository.findByName(canonical)
            val localized = repository.findLocalizedByName(bangla, canonical)
            assertTrue("Missing English guidance: $canonical", local != null)
            assertTrue("Missing Bangla guidance: $canonical", localized != null)
            assertEquals(local!!.name, localized!!.name)
        }
        val english = repository.findByName("Tomato Early Blight")!!
        val translated = repository.findLocalizedByName(bangla, english.name)!!
        assertEquals(english.name, translated.name)
        assertTrue(translated.symptoms.any { it in '\u0980'..'\u09ff' })
        context.assets.open("labels.txt").bufferedReader().use { reader ->
            reader.readLines().filter { it.isNotBlank() }.forEach { label ->
                val display = DiseasePresentation.name(bangla, "", label)
                assertTrue("Missing Bangla name for $label", display.any { it in '\u0980'..'\u09ff' })
            }
        }
    }
}