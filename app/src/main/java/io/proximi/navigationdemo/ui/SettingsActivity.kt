package io.proximi.navigationdemo.ui

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE
import androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.proximi.navigationdemo.R
import io.proximi.navigationdemo.ui.main.MainActivity
import io.proximi.navigationdemo.utils.ScaledContextActivity

/**
 * Activity with application settings. Uses Android preferences.
 */
class SettingsActivity : ScaledContextActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override val defaultTheme = R.style.AppTheme_Settings
    override val highContrastTheme = R.style.HighContrastTheme_Settings

    companion object {

        /* -------------------------------------------------------------------------------------- */
        /* Preference keys */

        /* Display */
        public const val DISPLAY_LARGE_MODE = "display_large_mode"
        public const val DISPLAY_HIGH_CONTRAST_MODE = "display_high_contract_mode"
        public const val DISPLAY_HEADING = "display_heading"
        public const val DISPLAY_HEADING_PATH = "path"
        public const val DISPLAY_HEADING_COMPASS = "compass"

        /* Route options */
        public const val ROUTE_AVOID_STAIRS = "route_avoid_stairs"
        public const val ROUTE_AVOID_ESCALATORS = "route_avoid_escalators"
        public const val ROUTE_AVOID_ELEVATORS = "route_avoid_elevators"
        public const val ROUTE_AVOID_REVOLVING_DOORS = "route_avoid_revolving_doors"
        public const val ROUTE_AVOID_NARROW_PATHS = "route_avoid_narrow"
        public const val ROUTE_UNITS = "route_units"
        public const val ROUTE_UNITS_STEP = "steps"
        public const val ROUTE_UNITS_METER = "meters"

        /* Voice guidance */
        public const val TTS_ENABLED = "tts_enabled"
        public const val TTS_HEADING_CORRECTION = "tts_headingCorrectionEnabled"
        public const val TTS_DECISION_ENABLED = "tts_decisionPointEnabled"
        public const val TTS_HAZARD_ENABLED = "tts_hazardEnabled"
        public const val TTS_LANDMARK_ENABLED = "tts_landmarkEnabled"
        public const val TTS_SEGMENT_ENABLED = "tts_segmentEnabled"
        public const val TTS_REASSURANCE_ENABLED = "tts_reassuranceEnabled"
        public const val TTS_REASSURANCE_DISTANCE = "tts_reassuranceDistance"

        /* Accessibility */
        public const val ACCESSIBILITY_HAPTIC = "accessibility_haptic"
        public const val ACCESSIBILITY_ZOOM = "accessibility_zoom"
        public const val ACCESSIBILITY_HAND_MODE = "accessibility_hand_mode"
        public const val ACCESSIBILITY_HAND_MODE_LEFT = "left"
        public const val ACCESSIBILITY_HAND_MODE_RIGHT = "right"
        public const val ACCESSIBILITY_HELP_BUTTON = "accessibility_help_button"
        public const val ACCESSIBILITY_TTS_DISABILITY = "accessibility_tts_disability"

        /* Development */
        public const val SIMULATE_ROUTE = "simulate_route"

        fun start(activity: Activity) {
            activity.startActivity(Intent(activity.baseContext, SettingsActivity::class.java))
        }
    }

    /**
     * Overrides [onSupportNavigateUp] to ensure popping fragment stack instead of closing activity.
     */
    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            return false
        } else {
            val intent = Intent(baseContext, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
            return true
        }
    }

    /**
     * Overrides [onPreferenceStartFragment] to start nested preference fragment.
     */
    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment =
            supportFragmentManager.fragmentFactory.instantiate(classLoader, pref.fragment!!)
        fragment.arguments = args
        fragment.setTargetFragment(caller, 0)
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(pref.fragment)
            .setTransition(TRANSIT_FRAGMENT_OPEN)
            .setTransitionStyle(R.style.AppTheme)
            .commit()
        return true
    }

    /**
     * Overrides [onCreate] to:
     * - inject base settings fragment as default
     * - enable up navigation button
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        supportFragmentManager
            .beginTransaction()
            .replace(
                R.id.settings,
                SettingsFragment()
            )
            .setTransition(TRANSIT_FRAGMENT_FADE)
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * Root settings fragment implementation.
     */
    class SettingsFragment : PreferenceFragmentCompat(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }

        override fun onResume() {
            super.onResume()
            requireActivity().title = getString(R.string.title_activity_settings)
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            onSharedPreferenceChanged(preferenceManager.sharedPreferences!!, ROUTE_AVOID_STAIRS)
            onSharedPreferenceChanged(preferenceManager.sharedPreferences!!, ROUTE_AVOID_ELEVATORS)
        }

        override fun onPause() {
            preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
            super.onPause()
        }

        /**
         * Callback of [SharedPreferences.OnSharedPreferenceChangeListener].
         * Ensures that user cannot enable mutually exclusive preferences.
         */
        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
            if (key == ROUTE_AVOID_STAIRS) {
                val disable = sharedPreferences.getBoolean(key, false)
                disablePreferenceAndValue(ROUTE_AVOID_ELEVATORS, disable)
            } else if (key == ROUTE_AVOID_ELEVATORS) {
                val disable = sharedPreferences.getBoolean(key, false)
                disablePreferenceAndValue(ROUTE_AVOID_STAIRS, disable)
            }
        }

        /**
         * Set preference to disabled / enabled by preference key.
         */
        private fun disablePreferenceAndValue(preferenceKey: String, disable: Boolean) {
            val preference = findPreference<SwitchPreference>(preferenceKey)!!
            if (disable) preference.isChecked = false
            preference.isEnabled = !disable
        }
    }

    /**
     * Preference fragment with voice guidance settings.
     */
    class VoiceGuidanceFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.voice_preferences, rootKey)
        }

        override fun onResume() {
            super.onResume()
            requireActivity().title = getString(R.string.settings_voice_guidance_modify)
        }
    }
}