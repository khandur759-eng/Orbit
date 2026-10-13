package com.orbit.launcher.apps

import com.orbit.launcher.preferences.AppOrdering
import com.orbit.launcher.preferences.OrbitSettings

/**
 * The single rule for which apps the wheel shows and in what order — shared by
 * the main screen, the Quick Picker and the widget so they never disagree.
 */
object WheelApps {

    /**
     * Resolves the apps for the wheel: never-configured means all apps, a saved
     * empty selection means an empty list, otherwise the saved ids, then ordered.
     */
    fun resolve(all: List<InstalledApp>, settings: OrbitSettings): List<InstalledApp> {
        val bySelection = when {
            !settings.selectionInitialized -> all
            settings.selectedApps.isEmpty() -> emptyList()
            else -> all.filter { it.id in settings.selectedApps }
        }
        return order(bySelection, settings)
    }

    fun order(apps: List<InstalledApp>, settings: OrbitSettings): List<InstalledApp> =
        when (settings.ordering) {
            AppOrdering.ALPHABETICAL ->
                apps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            AppOrdering.RECENT ->
                apps.sortedBy { settings.recents.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        }
}
