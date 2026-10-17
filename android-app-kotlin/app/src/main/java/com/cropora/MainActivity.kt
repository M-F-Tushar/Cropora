package com.cropora

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.cropora.network.PredictionResponse

class MainActivity : ModeAwareActivity(), AdvancedNavigator {
    private var setupDialog: AlertDialog? = null
    private var currentTab = AdvancedTab.HOME
    private val tabSlots = mutableMapOf<AdvancedTab, View>()

    private val backToHome = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = openTab(AdvancedTab.HOME)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (advanced) {
            setUpAdvanced(savedInstanceState)
        } else {
            // Tabs restored from an Advanced session have no container in the Normal layout.
            val restored = supportFragmentManager.fragments
            if (restored.isNotEmpty()) {
                supportFragmentManager.beginTransaction().apply { restored.forEach(::remove) }.commitNow()
            }
            setUpNormal()
        }
        if (!UiPreferences.isSetupComplete(this)) {
            showSetup(savedInstanceState)
        }
    }

    private fun setUpNormal() {
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.buttonOpenScan).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }
        findViewById<Button>(R.id.buttonOpenResult).setOnClickListener {
            startActivity(ResultActivity.createIntent(this, samplePrediction(this)))
        }
        findViewById<Button>(R.id.buttonOpenHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.buttonOpenLibrary).setOnClickListener {
            startActivity(Intent(this, DiseaseLibraryActivity::class.java))
        }
        findViewById<Button>(R.id.buttonOpenSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.buttonOpenAnalytics).setOnClickListener {
            startActivity(Intent(this, AnalyticsActivity::class.java))
        }
    }

    private fun setUpAdvanced(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_main_advanced)
        bindTab(R.id.advTabHome, AdvancedTab.HOME, R.drawable.adv_ic_home, R.string.adv_nav_home)
        bindTab(R.id.advTabScans, AdvancedTab.SCANS, R.drawable.ic_history, R.string.adv_nav_scans)
        bindTab(R.id.advTabLibrary, AdvancedTab.LIBRARY, R.drawable.ic_library, R.string.adv_nav_library)
        bindTab(R.id.advTabInsights, AdvancedTab.INSIGHTS, R.drawable.adv_ic_insights, R.string.adv_nav_insights)
        findViewById<View>(R.id.advNavScan).setOnClickListener { startScan() }
        onBackPressedDispatcher.addCallback(this, backToHome)

        val initialTab = savedInstanceState?.getString(STATE_TAB)?.let(::parseTab)
            ?: parseTab(intent.getStringExtra(EXTRA_TAB))
            ?: AdvancedTab.HOME
        showTab(initialTab)
    }

    private fun bindTab(@IdRes slotId: Int, tab: AdvancedTab, @DrawableRes icon: Int, @StringRes label: Int) {
        val slot = findViewById<View>(slotId)
        slot.findViewById<ImageView>(R.id.advTabIcon).setImageResource(icon)
        slot.findViewById<TextView>(R.id.advTabLabel).setText(label)
        slot.setOnClickListener { openTab(tab) }
        tabSlots[tab] = slot
    }

    private fun showTab(tab: AdvancedTab) {
        currentTab = tab
        val manager = supportFragmentManager
        val transaction = manager.beginTransaction().setReorderingAllowed(true)
        AdvancedTab.values().forEach { candidate ->
            val existing = manager.findFragmentByTag(candidate.name)
            when {
                candidate != tab -> existing?.let(transaction::hide)
                existing == null -> transaction.add(R.id.advFragmentContainer, createTab(candidate), candidate.name)
                else -> transaction.show(existing)
            }
        }
        transaction.commitNowAllowingStateLoss()
        tabSlots.forEach { (slotTab, slot) -> slot.isSelected = slotTab == tab }
        backToHome.isEnabled = tab != AdvancedTab.HOME
    }

    private fun createTab(tab: AdvancedTab): Fragment = when (tab) {
        AdvancedTab.HOME -> AdvancedHomeFragment()
        AdvancedTab.SCANS -> AdvancedScansFragment()
        AdvancedTab.LIBRARY -> AdvancedLibraryFragment()
        AdvancedTab.INSIGHTS -> AdvancedInsightsFragment()
    }

    override fun openTab(tab: AdvancedTab) {
        if (advanced && tab != currentTab) showTab(tab)
    }

    override fun startScan(offline: Boolean) {
        startActivity(Intent(this, ScanActivity::class.java).putExtra(ScanActivity.EXTRA_OFFLINE, offline))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (advanced) parseTab(intent.getStringExtra(EXTRA_TAB))?.let(::showTab)
    }

    private fun parseTab(name: String?): AdvancedTab? =
        AdvancedTab.values().firstOrNull { it.name == name }

    private fun showSetup(savedInstanceState: Bundle?) {
        val setupView = layoutInflater.inflate(R.layout.dialog_setup, null)
        val languageGroup = setupView.findViewById<RadioGroup>(R.id.groupSetupLanguage)
        val modeGroup = setupView.findViewById<RadioGroup>(R.id.groupSetupMode)
        languageGroup.check(if (resources.configuration.locales[0].language == "bn") {
            R.id.radioSetupBangla
        } else {
            R.id.radioSetupEnglish
        })
        modeGroup.check(if (UiPreferences.isAdvanced(this)) {
            R.id.radioSetupAdvanced
        } else {
            R.id.radioSetupSimple
        })
        savedInstanceState?.getInt("setup_language_choice", 0)?.takeIf { it != 0 }?.let(languageGroup::check)
        savedInstanceState?.getInt("setup_mode_choice", 0)?.takeIf { it != 0 }?.let(modeGroup::check)
        setupDialog = AlertDialog.Builder(this)
            .setTitle(R.string.setup_title)
            .setView(setupView)
            .setCancelable(false)
            .setPositiveButton(R.string.setup_continue) { _, _ ->
                val chooseAdvanced = modeGroup.checkedRadioButtonId == R.id.radioSetupAdvanced
                val language = if (languageGroup.checkedRadioButtonId == R.id.radioSetupBangla) "bn" else "en"
                val languageChanged = resources.configuration.locales[0].language != language
                UiPreferences.setAdvanced(this, chooseAdvanced)
                UiPreferences.completeSetup(this)
                UiPreferences.setLanguage(this, language)
                // A language change recreates the activity already; otherwise apply the mode here.
                if (!languageChanged && chooseAdvanced != advanced) recreate()
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        setupDialog?.findViewById<RadioGroup>(R.id.groupSetupLanguage)?.let {
            outState.putInt("setup_language_choice", it.checkedRadioButtonId)
        }
        setupDialog?.findViewById<RadioGroup>(R.id.groupSetupMode)?.let {
            outState.putInt("setup_mode_choice", it.checkedRadioButtonId)
        }
        if (advanced) outState.putString(STATE_TAB, currentTab.name)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        setupDialog?.dismiss()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TAB = "extra_tab"
        private const val STATE_TAB = "state_advanced_tab"

        fun tabIntent(context: Context, tab: AdvancedTab): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_TAB, tab.name)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun samplePrediction(context: Context) = PredictionResponse(
            modelLabel = context.getString(R.string.sample_model_label),
            disease = "Tomato Healthy",
            confidence = 0.96f,
            uncertain = false,
            guidanceAvailable = true,
            symptoms = context.getString(R.string.sample_symptoms),
            treatment = context.getString(R.string.sample_treatment),
            prevention = context.getString(R.string.sample_prevention)
        )
    }
}
