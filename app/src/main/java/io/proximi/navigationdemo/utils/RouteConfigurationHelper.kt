package io.proximi.navigationdemo.utils

import android.content.Context
import androidx.preference.PreferenceManager
import io.proximi.mapbox.data.model.Feature
import io.proximi.mapbox.library.RouteConfiguration
import io.proximi.navigationdemo.ui.SettingsActivity

/**
 * Helper class to create proximi.io [RouteOptions] object with user's preferences set in settings.
 */
object RouteConfigurationHelper {

    fun create(
        context: Context,
        destination: Feature,
        waypointList: List<RouteConfiguration.Waypoint>
    ): RouteConfiguration {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return RouteConfiguration.Builder()
            .setDestination(destination)
            .addWaypoints(*waypointList.toTypedArray())
            .setAvoidElevators(preferences.getBoolean(SettingsActivity.ROUTE_AVOID_ELEVATORS, false))
            .setAvoidEscalators(preferences.getBoolean(SettingsActivity.ROUTE_AVOID_ESCALATORS, false))
            .setAvoidNarrowPaths(preferences.getBoolean(SettingsActivity.ROUTE_AVOID_NARROW_PATHS, false))
            .setAvoidRevolvingDoors(preferences.getBoolean(SettingsActivity.ROUTE_AVOID_REVOLVING_DOORS, false))
            .setAvoidStaircases(preferences.getBoolean(SettingsActivity.ROUTE_AVOID_STAIRS, false))
            .build()
    }
}