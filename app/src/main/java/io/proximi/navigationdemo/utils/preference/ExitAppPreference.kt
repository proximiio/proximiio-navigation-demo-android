package io.proximi.navigationdemo.utils.preference

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import androidx.core.app.NotificationManagerCompat
import androidx.preference.Preference
import io.proximi.navigationdemo.navigationservice.NavigationService

/**
 * Custom preference class that closes the application (i.e. closes the application UI and stops the service).
 */
abstract class ExitAppPreference(context: Context, attrs: AttributeSet?) :
    Preference(context, attrs), Preference.OnPreferenceClickListener {

    init {
        this.onPreferenceClickListener = this
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        context.stopService(Intent(context, NavigationService::class.java))
        NotificationManagerCompat.from(context).cancel(NavigationService.NOTIFICATION_ID)
        val homeIntent = Intent(Intent.ACTION_MAIN)
        homeIntent.addCategory(Intent.CATEGORY_HOME)
        homeIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        context.startActivity(homeIntent)
        return true
    }
}