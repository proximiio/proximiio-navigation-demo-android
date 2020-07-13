package io.proximi.navigationdemo.navigationservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/**
 * [BroadcastReceiver] that is called from [NavigationService] notification action.
 * When called, it forces the service to stop.
 */
class StopNavigationServiceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        context.stopService(Intent(context, NavigationService::class.java))
        NotificationManagerCompat.from(context).cancel(NavigationService.NOTIFICATION_ID)
    }
}
