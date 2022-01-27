package io.proximi.navigationdemo.ui.main

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import io.proximi.navigationdemo.ui.SettingsActivity
import io.proximi.navigationdemo.ui.main.dialogs.DialogFragmentFactory
import io.proximi.proximiiolibrary.ProximiioAPI
import io.proximi.proximiiolibrary.ProximiioAPI.BLUETOOTH_REQUEST
import io.proximi.proximiiolibrary.ProximiioAPI.BLUETOOTH_REQUEST_S

/**
 * Helper class to that handles:
 * - required permissions checks and requests,
 * - bluetooth status and requests to enable bluetooth,
 * - dialog about accessibility options
 * on application startup.
 */
object MainActivityHelpers {

    private const val DIALOG_LOCATION_PERMISSION = "LOCATION_PERMISSION_DIALOG"
    private const val DIALOG_BLUETOOTH = "BLUETOOTH_DIALOG"
    private const val DIALOG_ACCESSIBILITY = "ACCESSIBILITY_DIALOG"
    private const val FIRST_STARTUP_KEY = "preference_first_startup"

    /**
     * Flag if permissions were checked to prevent repeats.
     */
    private var permissionsChecked = false

    fun onCreate() {
        permissionsChecked = false
    }

    /**
     * Start permission check routine. Will not do anything if this was called previously.
     */
    fun initiateChecks(mainActivity: MainActivity) {
        if (permissionsChecked) return
        permissionsChecked = true
        checkAndRequestLocationPermission(mainActivity)
    }

    /**
     * Checks if location permission was granted. If not, requests the permission from user.
     */
    private fun checkAndRequestLocationPermission(mainActivity: MainActivity) {
        if (!hasLocationPermission(mainActivity)) {
            ActivityCompat.requestPermissions(
                mainActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                ProximiioAPI.PERMISSION_REQUEST
            )
        } else {
            checkAndRequestBluetooth(mainActivity)
        }
    }

    /**
     * Test if location permission was already granted.
     */
    private fun hasLocationPermission(mainActivity: MainActivity): Boolean {
        return ActivityCompat.checkSelfPermission(
            mainActivity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if bluetooth is enabled and request to enable it if not.
     */
    private fun checkAndRequestBluetooth(mainActivity: MainActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    mainActivity,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    mainActivity,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ),
                    BLUETOOTH_REQUEST_S
                )
                return
            }

        } else {
            if (!isBluetoothEnabled()) {
                ActivityCompat.startActivityForResult(
                    mainActivity,
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                    BLUETOOTH_REQUEST,
                    null
                )
                return
            }
        }
        checkAndRequestAccessibility(mainActivity)
    }

    /**
     * Test if bluetooth is enabled.
     */
    private fun isBluetoothEnabled(): Boolean {
        return BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
    }

    /**
     * Process and check permission results.
     */
    fun onPermissionResult(
        mainActivity: MainActivity,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        val locationPermissionIndex =
            permissions.indexOfFirst { it == Manifest.permission.ACCESS_FINE_LOCATION }
        if (locationPermissionIndex == -1) return
        if (grantResults[locationPermissionIndex] == PackageManager.PERMISSION_DENIED) {
            DialogFragmentFactory.getLimitedFunctionalityLocationPermissionInstance(
                {
                    checkAndRequestLocationPermission(mainActivity)
                    it.dismiss()
                },
                {
                    checkAndRequestBluetooth(mainActivity)
                    it.dismiss()
                }
            ).show(mainActivity.supportFragmentManager, DIALOG_LOCATION_PERMISSION)
        } else {
            checkAndRequestBluetooth(mainActivity)
        }
    }

    /**
     * Process enable-bluetooth request result.
     */
    fun onBluetoothRequestResult(mainActivity: MainActivity, requestCode: Int, resultCode: Int) {
        if (requestCode != ProximiioAPI.BLUETOOTH_REQUEST) return
        if (resultCode == Activity.RESULT_OK) {
            checkAndRequestAccessibility(mainActivity)
        } else {
            DialogFragmentFactory.getLimitedFunctionalityBluetoothInstance(
                {
                    checkAndRequestBluetooth(mainActivity)
                    it.dismiss()
                },
                {
                    checkAndRequestAccessibility(mainActivity)
                    it.dismiss()
                }
            ).show(mainActivity.supportFragmentManager, DIALOG_BLUETOOTH)
        }
    }

    /**
     * Check and open accessibility dialog. Accessibility dialog is shown only once (on first application opening).
     */
    private fun checkAndRequestAccessibility(mainActivity: MainActivity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        val firstStartup = prefs.getBoolean(FIRST_STARTUP_KEY, true)
        if (firstStartup) {
            prefs.edit().putBoolean(FIRST_STARTUP_KEY, false).apply()
            showAccessibilityDialog(mainActivity)
        }
    }

    /**
     * Shows accessibility dialog.
     */
    private fun showAccessibilityDialog(mainActivity: MainActivity) {
        DialogFragmentFactory.getAccessibilitySettingsInstance(
            {
                SettingsActivity.start(mainActivity)
                it.dismiss()
            },
            {
                it.dismiss()
            }
        ).show(
            mainActivity.supportFragmentManager,
            DIALOG_ACCESSIBILITY
        )
    }
}