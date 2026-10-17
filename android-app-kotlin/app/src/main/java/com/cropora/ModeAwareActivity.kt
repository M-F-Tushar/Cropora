package com.cropora

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Screens that have a Normal and an Advanced layout. The mode is fixed when the
 * activity is created (Advanced also uses its own theme), and the activity is
 * recreated when the preference changes while it is in the back stack.
 */
abstract class ModeAwareActivity : AppCompatActivity() {
    protected var advanced: Boolean = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        advanced = UiPreferences.isAdvanced(this)
        if (advanced) setTheme(R.style.Theme_Cropora_Advanced)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (advanced != UiPreferences.isAdvanced(this)) {
            onModeChanging()
            recreate()
        }
    }

    /** Called right before the activity is recreated for the other mode. */
    protected open fun onModeChanging() = Unit
}
