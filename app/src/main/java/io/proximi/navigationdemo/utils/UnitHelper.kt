package io.proximi.navigationdemo.utils

import android.content.Context
import android.content.res.Resources
import androidx.preference.PreferenceManager
import io.proximi.navigationdemo.R
import io.proximi.navigationdemo.ui.SettingsActivity
import kotlin.math.roundToInt


/**
 * Length of one step in meters
 */
private val STEP_LENGTH = 0.65 // meters

/**
 * Average walking speed in meters per second
 */
private val WALKING_SPEED = 1.4 // meters per second

/**
 * Coeficient to convert meters to steps.
 */
val METER_TO_STEP_COEFFICIENT get() = 1.0 / STEP_LENGTH

/**
 * Collection of helper methods for conversion of distance into desired string formats
 * and calculating of estimated time to walk distance.
 */
object UnitHelper {

    /**
     * Create distance string for 'distance left' with desired units (set in settings by user).
     */
    fun getDistanceInPreferenceUnit(distanceInMeters: Double, context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return if (preferences.getString(SettingsActivity.ROUTE_UNITS, "step")!! == "step") {
            val steps = convertMetersToStep(distanceInMeters)
            context.resources.getQuantityString(R.plurals.step_count, steps, steps)
        } else {
            val metersRounded = distanceInMeters.roundToInt()
            context.resources.getQuantityString(R.plurals.navigation_meters, metersRounded, metersRounded)
        }
    }

    /**
     * Create distance string for 'distance left' with desired units (set in settings by user).
     */
    fun getDistanceLeftInPreferenceUnit(distanceInMeters: Double, context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return if (preferences.getString(SettingsActivity.ROUTE_UNITS, "step")!! == "step") {
            val steps = convertMetersToStep(distanceInMeters)
            context.resources.getQuantityString(R.plurals.steps_left, steps, steps)
        } else {
            val metersRoundend = distanceInMeters.roundToInt()
            context.resources.getQuantityString(R.plurals.meters_left, metersRoundend, metersRoundend)
        }
    }

    /**
     * Convert distance to string with time estimate of route.
     */
    fun distanceToTimeString(distance: Double, resources: Resources): String {
        val secondsRemaining = distanceToSeconds(distance)
        val minutesRemaining = (secondsRemaining / 60) + (if (secondsRemaining % 60 > 0) 1 else 0)
        return "$minutesRemaining " + resources.getQuantityString(R.plurals.navigation_minutes, minutesRemaining)
    }

    /**
     * Convert meters to steps.
     */
    private fun convertMetersToStep(meters: Double): Int {
        return (meters / STEP_LENGTH).roundToInt()
    }

    /**
     * Calculate seconds to walk given distance
     */
    private fun distanceToSeconds(distance: Double): Int {
        return (distance / WALKING_SPEED).roundToInt()
    }
}