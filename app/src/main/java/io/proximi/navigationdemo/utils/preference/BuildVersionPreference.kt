package io.proximi.navigationdemo.utils.preference

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import io.proximi.navigationdemo.BuildConfig
import io.proximi.navigationdemo.BuildConfig.BUILD_TIME
import java.text.SimpleDateFormat

/**
 * Custom preference that displays current build version and date as summary.
 */
class BuildVersionPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs), Preference.OnPreferenceClickListener {

    @SuppressLint("SimpleDateFormat")
    override fun getSummary(): CharSequence {
        return "${BuildConfig.VERSION_NAME} (${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(BUILD_TIME)})"
    }

    override fun onPreferenceClick(preference: Preference?): Boolean {
        return true
    }

}