package com.cropora

/** Tabs of the Advanced home screen, in bottom-bar order. */
enum class AdvancedTab {
    HOME, SCANS, LIBRARY, INSIGHTS
}

/** Implemented by the Advanced host activity so tabs can navigate without knowing about each other. */
interface AdvancedNavigator {
    fun openTab(tab: AdvancedTab)
    fun startScan(offline: Boolean = false)
}
