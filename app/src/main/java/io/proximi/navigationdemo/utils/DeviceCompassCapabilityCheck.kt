package io.proximi.navigationdemo.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import com.mapbox.mapboxsdk.location.CompassEngine

/**
 * Check if device supports compass.
 *
 * This check is based on default implementation of mapbox's default [CompassEngine].
 */
fun deviceHasCompassCapability(context: Context): Boolean {
    ContextCompat.getSystemService(context, SensorManager::class.java)?.apply {
        var compassSensor = getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        var gravitySensor: Sensor? = null
        var magneticFieldSensor: Sensor? = null
        if (compassSensor == null) {
            if (getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
                compassSensor = getDefaultSensor(Sensor.TYPE_ORIENTATION)
            } else {
                gravitySensor = getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                magneticFieldSensor = getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            }
        }
        return compassSensor != null || (gravitySensor != null && magneticFieldSensor != null)
    }
    return false
}