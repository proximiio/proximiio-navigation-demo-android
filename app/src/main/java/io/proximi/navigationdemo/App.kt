package io.proximi.navigationdemo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import io.proximi.navigationdemo.navigationservice.NavigationService

/**
 * Application class override.
 * - multidex is required due to mapbox
 * - observe application lifecycle (foreground and background) to manage service lifecycle (bind).
 */
class App: MultiDexApplication(), LifecycleObserver {

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Observe lifecycle and bind service to keep it alive when app is foreground */

    /** Service reference */
    private var navigationService: NavigationService? = null

    /** Android service connection callbacks */
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as NavigationService.LocalBinder
            navigationService = binder.getService()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            navigationService = null
        }
    }

    /**
     * Create and bind service when app goes into foreground.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onEnterForeground() {
        Intent(this, NavigationService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    /**
     * Unbind service when app goes into background.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onEnterBackground() {
        if (navigationService != null) {
            unbindService(connection)
        }
    }

}