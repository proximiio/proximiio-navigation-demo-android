package io.proximi.navigationdemo.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import io.proximi.navigationdemo.R
import io.proximi.navigationdemo.ui.SettingsActivity

@SuppressLint("Registered")
open class ScaledContextActivity: AppCompatActivity() {

    /** Shared preferences reference */
    private lateinit var sharedPreferences: SharedPreferences
    /** Large mode enabled */
    private var largeMode = false
    /** High contrast mode enabled */
    private var highContrastMode = false

    /** Resource ID for default theme */
    internal open val defaultTheme = R.style.AppTheme
    /** Resource ID for high contrast theme */
    internal open val highContrastTheme = R.style.HighContrastTheme

    /**
     * Overrides [attachBaseContext] to load preferences and create preference listener.
     */
    override fun attachBaseContext(newBase: Context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(newBase)
        largeMode = isLargeMode(sharedPreferences)
        highContrastMode = isHighContrastMode(sharedPreferences)
        super.attachBaseContext(ScaledContextWrapper.wrap(newBase, largeMode))
        Log.d("ScalingActivity", "largeMode = $largeMode")
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    /**
     * Overrides [onCreate] to force load theme based on preferences.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        val highContrastMode = sharedPreferences.getBoolean(SettingsActivity.DISPLAY_HIGH_CONTRAST_MODE, false)
        setTheme(if (highContrastMode) highContrastTheme else defaultTheme)
        super.onCreate(savedInstanceState)
    }

    /**
     * Overrides [onDestroy] to unregister preference listener.
     */
    override fun onDestroy() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onDestroy()
    }

    /**
     * Preference listener to listenr for changes in theme and scaling settings.
     */
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        Log.d("ScalingActivity", "changed: $key")
        if (key == SettingsActivity.DISPLAY_LARGE_MODE && isLargeMode(sharedPreferences) != largeMode) {
            recreate()
        } else if (key == SettingsActivity.DISPLAY_HIGH_CONTRAST_MODE && isHighContrastMode(sharedPreferences) != highContrastMode) {
            recreate()
        }
    }

    /**
     * Returns current largeMode preference value.
     */
    private fun isLargeMode(sharedPreferences: SharedPreferences): Boolean {
        val value = sharedPreferences.getBoolean(SettingsActivity.DISPLAY_LARGE_MODE, false)
        Log.d("ScalingActivity", "largeMode = $value")
        return value
    }

    /**
     * Returns current highContrast preference value.
     */
    private fun isHighContrastMode(sharedPreferences: SharedPreferences): Boolean {
        val value = sharedPreferences.getBoolean(SettingsActivity.DISPLAY_HIGH_CONTRAST_MODE, false)
        Log.d("ScalingActivity", "highContrastMode = $value")
        return value
    }

}