package io.proximi.navigationdemo.utils

import android.content.Context
import androidx.preference.PreferenceManager
import io.proximi.navigationdemo.ui.SettingsActivity
import io.proximi.mapbox.library.RouteOptions

/**
 * Helper class to create proximi.io [RouteOptions] object with user's preferences set in settings.
 */
object RouteOptionsHelper {

    fun create(context: Context): RouteOptions {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return RouteOptions(
            false,
            preferences.getBoolean(SettingsActivity.ROUTE_AVOID_ELEVATORS, false),
            preferences.getBoolean(SettingsActivity.ROUTE_AVOID_ESCALATORS, false),
            preferences.getBoolean(SettingsActivity.ROUTE_AVOID_NARROW_PATHS, false),
            false,
            preferences.getBoolean(SettingsActivity.ROUTE_AVOID_REVOLVING_DOORS, false),
            preferences.getBoolean(SettingsActivity.ROUTE_AVOID_STAIRS, false),
            false
        )
    }

}