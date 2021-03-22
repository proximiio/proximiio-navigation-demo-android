package io.proximi.navigationdemo.utils

import android.content.Context
import android.content.res.Resources
import androidx.preference.PreferenceManager
import io.proximi.mapbox.library.UnitConversion
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
        val converter = if (preferences.getString(SettingsActivity.ROUTE_UNITS, SettingsActivity.ROUTE_UNITS_STEP)!! == SettingsActivity.ROUTE_UNITS_STEP) {
            STEPS
        } else {
            METERS
        }
        val convertedDistance = converter.convert(distanceInMeters)
        val plural = when (convertedDistance.unitName) {
            "steps" -> R.plurals.navigation_steps
            "meters" -> R.plurals.navigation_meters
            "kilometers" -> R.plurals.navigation_kilometers
            else -> error("Unsupported unit!")
        }
        return context.resources.getQuantityString(plural, convertedDistance.value.roundToInt(), convertedDistance.valueString)
    }

    /**
     * Create distance string for 'distance left' with desired units (set in settings by user).
     */
    fun getDistanceLeftInPreferenceUnit(distanceInMeters: Double, context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val converter = if (preferences.getString(SettingsActivity.ROUTE_UNITS, SettingsActivity.ROUTE_UNITS_STEP)!! == SettingsActivity.ROUTE_UNITS_STEP) {
            STEPS
        } else {
            METERS
        }
        val convertedDistance = converter.convert(distanceInMeters)
        val plural = when (convertedDistance.unitName) {
            "steps" -> R.plurals.steps_left
            "meters" -> R.plurals.meters_left
            "kilometers" -> R.plurals.kilometers_left
            else -> error("Unsupported unit!")
        }
        return context.resources.getQuantityString(plural, convertedDistance.value.roundToInt(), convertedDistance.valueString)
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

    /**
     * UnitConversion for meters. Passed to ProximiioMapbox to ensure navigation contains required units.
     */
    val METERS = UnitConversion.Builder()
        .addStage("meters", 1.0)
        .addStage("kilometers", 0.001   , 1000.0, 1)
        .addStage("kilometers", 0.001   , 2000.0, 0)
        .build()

    /**
     * UnitConversion for Steps. Passed to ProximiioMapbox to ensure navigation contains required units.
     */
    val STEPS = UnitConversion.Builder()
        .addStage(SettingsActivity.ROUTE_UNITS_STEP, METER_TO_STEP_COEFFICIENT)
        .addStage("kilometers", 0.001   , 1000.0, 1)
        .addStage("kilometers", 0.001   , 2000.0, 0)
        .build()
}