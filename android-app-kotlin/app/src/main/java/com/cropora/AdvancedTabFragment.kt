package com.cropora

import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cropora.database.AppDatabase
import com.cropora.database.ScanRecord
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Base for the tabs hosted by [MainActivity] in Advanced mode. */
abstract class AdvancedTabFragment(@LayoutRes layout: Int) : Fragment(layout) {

    protected val navigator: AdvancedNavigator
        get() = requireActivity() as AdvancedNavigator

    /** Delivers saved scans (newest first) while the view is at least started. */
    protected fun observeScans(onScans: (List<ScanRecord>) -> Unit) {
        val owner = viewLifecycleOwner
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDatabase.getInstance(requireContext().applicationContext)
                    .scanDao()
                    .observeAllScans()
                    .catch { emit(emptyList()) }
                    .collect { onScans(it) }
            }
        }
    }
}
