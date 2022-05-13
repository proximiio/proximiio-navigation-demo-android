package io.proximi.navigationdemo.ui.main

import android.animation.ValueAnimator
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.maps.MapboxMap
import io.proximi.mapbox.library.ProximiioMapbox
import io.proximi.mapbox.library.RouteUpdateType
import io.proximi.navigationdemo.ProximiioAuthToken
import io.proximi.navigationdemo.R
import io.proximi.navigationdemo.navigationservice.NavigationService
import io.proximi.navigationdemo.ui.CustomMarker
import io.proximi.navigationdemo.ui.CustomMarkerHelper
import io.proximi.navigationdemo.ui.MARKER_ID
import io.proximi.navigationdemo.ui.SettingsActivity
import io.proximi.navigationdemo.ui.SettingsActivity.Companion.ACCESSIBILITY_HAND_MODE
import io.proximi.navigationdemo.ui.SettingsActivity.Companion.ACCESSIBILITY_HELP_BUTTON
import io.proximi.navigationdemo.ui.SettingsActivity.Companion.ACCESSIBILITY_ZOOM
import io.proximi.navigationdemo.ui.main.dialogs.HelpDialogFragment
import io.proximi.navigationdemo.ui.main.dialogs.LocationNotCoveredDialogFragment
import io.proximi.navigationdemo.ui.main.fragments.navigation.NavigationFragment
import io.proximi.navigationdemo.ui.main.fragments.routepreview.RoutePreviewFragment
import io.proximi.navigationdemo.ui.main.fragments.search.SearchFragment
import io.proximi.navigationdemo.ui.searchitem.SearchItemDetailActivity
import io.proximi.navigationdemo.utils.CustomLocationComponentActivator
import io.proximi.navigationdemo.utils.ScaledContextActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main_appbar.*
import kotlinx.android.synthetic.main.activity_main_content.*
import java.util.*

class MainActivity : ScaledContextActivity() {

    /** Override default theme resource ID */
    override val defaultTheme = R.style.AppTheme_NoActionBar

    /** Override high contrast theme resource ID */
    override val highContrastTheme = R.style.HighContrastTheme_NoActionBar

    companion object {
        private val TAG = MainActivity::class.java.simpleName

        // Phone Location settings request
        private const val REQUEST_CHECK_LOCATION_SETTINGS = 789

        // Opening search POI
        private const val SEARCH_CODE = 101
        const val EXTRA_LATITUDE_EXTRA = "RESULT_LATITUDE_EXTRA"
        const val EXTRA_LONGITUDE_EXTRA = "RESULT_LONGITUDE_EXTRA"
        const val EXTRA_LEVEL_EXTRA = "RESULT_LEVEL_EXTRA"
        const val EXTRA_TITLE_EXTRA = "RESULT_TITLE_EXTRA"
        const val EXTRA_POI_ID = "RESULT_POI_ID_EXTRA"
    }


    // View model reference
    lateinit var viewModel: MainActivityViewModel

    /** Mapbox map reference */
    private var map: MapboxMap? = null

    /** Flag if map location was set, this is used to zoom to location on first location update */
    private var mapLocationInitialized = false

    /* TODO */
    private var mapSetup: (() -> Unit)? = null

    /** Helper that handles map behavior toggles based on current state. */
    private var mapModeHelper: MapModeHelper? = null

    /** Flag if activity is started. */
    private var started = false

    private lateinit var customMarkerHelper: CustomMarkerHelper


    /* ------------------------------------------------------------------------------------------ */
    /* Activity lifecycle */

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivityHelpers.onCreate()
        viewModel = MainActivityViewModel(application)

        super.onCreate(savedInstanceState)
        setupDataObservers()
        Mapbox.getInstance(this, ProximiioAuthToken.TOKEN)
        setContentView(R.layout.activity_main)
        // Setup button click listeners
        helpButton.setOnClickListener { onHelpButtonClicked() }
        leftSettingsButton.setOnClickListener { SettingsActivity.start(this) }
        rightSettingsButton.setOnClickListener { SettingsActivity.start(this) }
        zoomInFab.setOnClickListener { map?.moveCamera(CameraUpdateFactory.zoomIn()) }
        zoomOutFab.setOnClickListener { map?.moveCamera(CameraUpdateFactory.zoomOut()) }

        ArrayAdapter.createFromResource(
            this,
            R.array.main_floor_spinner_names,
            R.layout.floor_spinner_item_selected
        ).also { adapter ->
            adapter.setDropDownViewResource(R.layout.floor_spinner_item)
            floorSpinner.adapter = adapter
        }
        floorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                viewModel.setDisplayLevel(position)
            }
        }

        // Setup Map
        mapSetup = {
            setupMap(savedInstanceState)
            mapSetup = null
        }


    }

    /**
     * Cancel current navigation route.
     */
    fun routeCancel() {
        viewModel.routeCancel()
    }

    override fun onStart() {
        super.onStart()
        started = true
        viewModel.onActivityStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        loadUserInterfaceSettings()
        MainActivityHelpers.initiateChecks(this@MainActivity)
        mapSetup?.invoke()
        processNavigationStartIntent()
    }

    /**
     * Overrides [onBackPressed] to manage fragments.
     * - If route navigation or route preview is opened, cancel it and reset to default state.
     * - If [SearchFragment] bottom sheet is opened, close it.
     * Otherwise closes the activity.
     */
    override fun onBackPressed() {
        if (currentFragment is NavigationFragment || currentFragment is RoutePreviewFragment) {
            routeCancel()
        } else if (currentFragment is SearchFragment && (currentFragment as SearchFragment).collapseBottomSheet()) {
            // collapseBottomSheet closed it
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        viewModel.onActivityStop()
        started = false
        super.onStop()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        viewModel.onActivityDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Process Bluetooth enable request result
        MainActivityHelpers.onBluetoothRequestResult(this, requestCode, resultCode)
        // Process navigation request data
//        if (requestCode == SEARCH_CODE && resultCode == Activity.RESULT_OK) {
//            data?.let { startNavigation(it) }
//        }
    }

    /**
     * Process permission request results.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        MainActivityHelpers.onPermissionResult(this, permissions, grantResults)
        viewModel.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    /* ------------------------------------------------------------------------------------------ */
    /* FAB button offset from bottom */

    private var bottomAnimator: ValueAnimator? = null

    /**
     * Set pixel offset for FABs from the bottom of the screen.
     */
    fun setBottomOffset(origin: Fragment, px: Int, animate: Boolean = false) {
        if (origin != currentFragment) return
        val targetTranslation = -px.toFloat()
        Log.d(TAG, "targetTranslation = $targetTranslation")
        bottomAnimator?.cancel()
        bottomAnimator = null
        if (animate) {
            bottomAnimator =
                ValueAnimator.ofFloat(fabsWrapper.translationY, targetTranslation).apply {
                    addUpdateListener { update ->
                        val value = update.animatedValue as Float
                        fabsWrapper.translationY = value
                        mapView.translationY = value / 2
//                    Log.d(TAG, "animatedTranslation = $value")
                    }

                    start()
                }
        } else {
            fabsWrapper.translationY = targetTranslation
            mapView.translationY = targetTranslation / 2
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Help button */

    /**
     * Help button click function. Check if user location whether is "on site".
     * If user is on site, show dialog which opens dialer with help number when confirmed.
     * Otherwise show dialog notifying user location is not covered.
     */
    private fun onHelpButtonClicked() {
        checkPhoneLocationEnabled {
            if (checkSupportedPlace()) {
                HelpDialogFragment.newInstance(
                    { dialog -> dialog.dismiss(); callHelpNumber(); },
                    { dialog -> dialog.dismiss() }
                ).show(supportFragmentManager, "helpDialog")
            } else {
                LocationNotCoveredDialogFragment.newInstance { it.dismiss() }
                    .show(supportFragmentManager, null)
            }
        }
    }

    /**
     * Open phone dialer with help number.
     */
    private fun callHelpNumber() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(getString(R.string.emergency_phone))
        startActivity(intent)
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Navigation route start logic */

    /**
     * Store navigation route intent containing data for route destination.
     */
    private var navigationStartIntent: Intent? = null

    /**
     * Start navigation to destination with data stored in the intent.
     */
    fun startNavigation(data: Intent) {
        navigationStartIntent = data
    }

    /**
     * Trigger processing of route navigation intent with destination data.
     * Test if user is 'on site' before starting.
     */
    private fun processNavigationStartIntent() {
        navigationStartIntent?.let { data ->
            navigationStartIntent = null
            if (!checkSupportedPlace()) {
                return
            }
            val poiId = data.extras!!.getString(EXTRA_POI_ID)
            if (poiId != null && poiId.isNotBlank()) {
                Log.d("NAVIGATION_LOOP", "MainActivity starting route")
                viewModel.routeFind(poiId)
            }
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Map */

    /**
     * Get MapboxMap object and configure map as desired.
     * Attaches all necessary data and callbacks.
     */
    private fun setupMap(savedInstanceState: Bundle?) {
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync { mapboxMap ->
            map = mapboxMap

            // Create helper that handles map states based on navigation state and user interaction with map.
            mapModeHelper = MapModeHelper(mapboxMap, myLocationFab, toggleModeFab, this, viewModel)
            // Set mapbox location component heading type
            loadMapCompassHeadingForNavigation()
            // Pass mapbox map reference to Proximi.io map SDK. CustomLocationComponentActivator provides callback when Mapbox LocationComponent was activated
            viewModel.onMapReady(mapboxMap, CustomLocationComponentActivator(baseContext) {
                // Inform mapModeHelper location component is activated
                mapModeHelper?.locationComponentActivated = true
                // Pass location component state status to mapmode helper
                map!!.locationComponent.addOnLocationStaleListener { mapModeHelper?.stale = it }
                // Hide loading overlay
                loadingOverlay.visibility = View.GONE
            })
            // Configure mapbox UI
            map!!.uiSettings.attributionGravity = Gravity.TOP or GravityCompat.END
            map!!.uiSettings.isAttributionEnabled = false
            map!!.uiSettings.isCompassEnabled = false
            map!!.uiSettings.isLogoEnabled = false

            // Add MapboxMap on click listener to be able to click POIs on map
            mapboxMap.addOnMapClickListener { point ->
                // Query features from selected layers only and use first to open detail activity
                mapboxMap.queryRenderedFeatures(
                    mapboxMap.projection.toScreenLocation(point),
                    "proximiio-pois-icons",
                    "proximiio-levelchangers"
                )
                    .map { poi -> viewModel.featuresLiveData.value!!.firstOrNull { poi.id() == it.id } }
                    .firstOrNull()?.let { feature ->
                        SearchItemDetailActivity.startForResult(this, SEARCH_CODE, feature)
                    } != null

                // Query for markers
                mapboxMap.queryRenderedFeatures(
                    mapboxMap.projection.toScreenLocation(point),
                    "layer.${MARKER_ID}"
                )
                    .map { poi -> viewModel.markersLiveData.value!!.firstOrNull { poi.id() == it.id } }
                    .firstOrNull()?.let { marker ->
                        // handle here tap on marker
                        Log.d("MARKER", marker.toString())
                    } != null
            }

            val proximiioMapbox =
                ProximiioMapbox.getInstance(baseContext, ProximiioAuthToken.TOKEN, null)

            customMarkerHelper =
                CustomMarkerHelper(
                    baseContext,
                    this,
                    mapboxMap,
                    proximiioMapbox,
                    viewModel.markersLiveData
                )

            viewModel.set(
                listOf<CustomMarker>(
                    CustomMarker(
                        "m0", 1, Point.fromLngLat(24.921695923476054, 60.1671950369849)
                    ),
                    CustomMarker(
                        "m1", 2, Point.fromLngLat(24.921695923476054, 60.16746993048443)
                    ),
                    CustomMarker(
                        "m2", 3, Point.fromLngLat(24.921695923476054, 60.16714117139544)
                    )
                )
            )
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Handling data updates */

    /**
     * Observe LiveData to update UI accordingly.
     */
    private fun setupDataObservers() {
        viewModel.routeLiveData.observe(this, Observer {
            if (it == null) mapModeHelper?.onNavigationEnd()
        })
        viewModel.routeEventLiveData.observe(this, Observer { processRouteEventLiveData(it) })
        viewModel.userLocationLiveData.observe(this, Observer {
            if (!mapLocationInitialized && map != null) {
                mapLocationInitialized = true
            }
        })
        viewModel.userLevelLiveData.observe(this, Observer { toggleUserLocationMarkerVisiblity() })
        viewModel.displayLevelLiveData.observe(this, Observer {
            updateFloorSpinnerSelectedItem(it)
            toggleUserLocationMarkerVisiblity()
        })
        viewModel.userPlaceLiveData.observe(this, Observer {
            val title = if (it?.name != null) {
                val nameList = it.name.split(';')
                if (nameList.size == 2) {
                    if (Locale.getDefault().language == "ar") {
                        nameList[1]
                    } else {
                        nameList[0]
                    }
                } else {
                    it.name
                }
            } else {
                getString(R.string.main_title_default)
            }
            appBarTitleTextView.text = title.trim()
        })
    }

    /** Reference for current Fragment displayed. */
    private var currentFragment: Fragment? = null

    /** Handler used for delayed fragment changes. */
    private val currentFragmentChangeHandler = Handler()

    /** Runnable used for delayed fragment changes. */
    private var currentFragmentChangeRunnable: Runnable? = null

    /** Time delay used for delayed fragment changes. */
    private val currentFragmentChangeDelay = 3000L

    /**
     * Updates currently selected floor in floor spinner to given display level.
     */
    private fun updateFloorSpinnerSelectedItem(displayLevel: Int) {
        floorSpinner.setSelection(displayLevel)
    }

    /**
     * Call this when display or user level changes. This method tests if current display and user levels are equal.
     * If they are, show current location marker. Otherwise hide it.
     */
    private fun toggleUserLocationMarkerVisiblity() {
        map?.getStyle {
            if (map?.locationComponent?.isLocationComponentActivated == true) {
                map!!.locationComponent.isLocationComponentEnabled =
                    (viewModel.userLevelLiveData.value == viewModel.displayLevelLiveData.value)
            }
        }
    }

    /**
     * Process current route event data and switch current fragment overlay. Switches between:
     * - [SearchFragment] when no route in progress,
     * - [RoutePreviewFragment] when route is previewed, but not navigating,
     * - [NavigationFragment] when navigating on a route.
     */
    private fun processRouteEventLiveData(it: NavigationService.RouteEvent?) {
        if (!started) return
        Log.d(TAG, "route update type: ${it?.eventType}")
        currentFragmentChangeRunnable?.let {
            currentFragmentChangeHandler.removeCallbacks(it)
            currentFragmentChangeRunnable = null
        }
        if (it == null || it.eventType == RouteUpdateType.CANCELED) {
            if (currentFragment !is SearchFragment) {
                currentFragment = SearchFragment.newInstance()
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        android.R.anim.fade_in,
                        android.R.anim.fade_out,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                    )
                    .replace(R.id.fragmentNavigationHostFragment, currentFragment!!)
                    .commit()
            }
        } else if (it.eventType.isRouteEnd()) {
            currentFragmentChangeRunnable = Runnable {
                if (started && currentFragment !is SearchFragment) {
                    currentFragment = SearchFragment.newInstance()
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            android.R.anim.fade_in,
                            android.R.anim.fade_out,
                            android.R.anim.fade_in,
                            android.R.anim.fade_out
                        )
                        .replace(R.id.fragmentNavigationHostFragment, currentFragment!!)
                        .commit()
                }
            }
            currentFragmentChangeHandler.postDelayed(
                currentFragmentChangeRunnable!!,
                currentFragmentChangeDelay
            )
        } else if (it.eventType == RouteUpdateType.CALCULATING) {
            if (currentFragment !is RoutePreviewFragment) {
                currentFragment = RoutePreviewFragment.newInstance()
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        android.R.anim.fade_in,
                        android.R.anim.fade_out,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                    )
                    .replace(R.id.fragmentNavigationHostFragment, currentFragment!!)
                    .commit()
            }
        } else if (it.eventType != RouteUpdateType.CALCULATING) {
            if (currentFragment !is NavigationFragment) {
                currentFragment = NavigationFragment.newInstance()
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        android.R.anim.fade_in,
                        android.R.anim.fade_out,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                    )
                    .replace(R.id.fragmentNavigationHostFragment, currentFragment!!)
                    .commit()
            }
            mapModeHelper?.onNavigationStart()
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* User Interface settings */

    /**
     * Load settings that affect activity's UI.
     */
    private fun loadUserInterfaceSettings() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(baseContext)
        // get preference for left / right hand mode
        val handMode = preferences.getString(ACCESSIBILITY_HAND_MODE, null)
        val fabsSide = when (handMode) {
            SettingsActivity.ACCESSIBILITY_HAND_MODE_LEFT -> Gravity.LEFT
            else -> Gravity.RIGHT
        }
        fabsWrapper.layoutParams = (fabsWrapper.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = fabsSide or Gravity.BOTTOM
        }
        if (fabsSide == Gravity.LEFT) {
            leftSettingsButton.visibility = View.VISIBLE
            rightSettingsButton.visibility = View.GONE
        } else {
            leftSettingsButton.visibility = View.GONE
            rightSettingsButton.visibility = View.VISIBLE
        }
        val helpButtonVisibility =
            if (preferences.getBoolean(ACCESSIBILITY_HELP_BUTTON, true)) View.VISIBLE else View.GONE
        helpButton.visibility = helpButtonVisibility
        val zoomButtonsVisibility =
            if (preferences.getBoolean(ACCESSIBILITY_ZOOM, false)) View.VISIBLE else View.GONE
        zoomInFab.visibility = zoomButtonsVisibility
        zoomOutFab.visibility = zoomButtonsVisibility

        loadMapCompassHeadingForNavigation(preferences)
    }

    /**
     * Load preference for map heading type (compass or route based).
     */
    private fun loadMapCompassHeadingForNavigation(
        preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(
            baseContext
        )
    ) {
        mapModeHelper?.setUseCompassHeadingForNavigation(
            preferences.getString(
                SettingsActivity.DISPLAY_HEADING,
                SettingsActivity.DISPLAY_HEADING_PATH
            ).equals(SettingsActivity.DISPLAY_HEADING_COMPASS)
        )
    }


    /* ------------------------------------------------------------------------------------------ */
    /* Phone location test */

    /** Google API client reference, used for testing if phone location is enabled */
    private var googleApiClient: GoogleApiClient? = null

    /**
     * Checks if location is enabled on device. If enabled, call [onSuccess] callback.
     */
    fun checkPhoneLocationEnabled(onSuccess: () -> Unit) {
        googleApiClient = GoogleApiClient.Builder(this)
            .addApi(LocationServices.API)
            .addConnectionCallbacks(object : GoogleApiClient.ConnectionCallbacks {
                override fun onConnected(p0: Bundle?) {
                    val locationRequest: LocationRequest = LocationRequest.create()
                    locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                    locationRequest.interval = 30 * 1000
                    locationRequest.fastestInterval = 5 * 1000
                    val builder: LocationSettingsRequest.Builder = LocationSettingsRequest.Builder()
                        .addLocationRequest(locationRequest)
                        .setAlwaysShow(true) //this is the key ingredient
                    val resultPi = LocationServices.SettingsApi.checkLocationSettings(
                        googleApiClient,
                        builder.build()
                    )
                    resultPi.setResultCallback { result ->
                        val status: Status = result.status
                        val state: LocationSettingsStates = result.locationSettingsStates
                        when (status.statusCode) {
                            LocationSettingsStatusCodes.SUCCESS -> {
                                Log.d(TAG, "location check: RESOLUTION_REQUIRED")
                                onSuccess.invoke()
                            }
                            LocationSettingsStatusCodes.RESOLUTION_REQUIRED ->  // Location settings are not satisfied. But could be fixed by showing the user a dialog.
                                try {
                                    Log.d(TAG, "location check: RESOLUTION_REQUIRED")
                                    // Show the dialog by calling startResolutionForResult(), and check the result in onActivityResult().
                                    status.startResolutionForResult(
                                        this@MainActivity,
                                        REQUEST_CHECK_LOCATION_SETTINGS
                                    )
                                } catch (e: IntentSender.SendIntentException) {
                                } // Ignore the error.
                            LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                                Log.d(TAG, "location check: SETTINGS_CHANGE_UNAVAILABLE")
                            }
                        }
                        googleApiClient?.disconnect()
                        googleApiClient = null
                    }
                }

                override fun onConnectionSuspended(p0: Int) {
                }

            })
            .build()
        googleApiClient?.connect()
    }

    /* ------------------------------------------------------------------------------------------ */

    /**
     * Test if user is inside a 'covered location' and show dialog if not.
     * @return true if location is supported.
     */
    fun checkSupportedPlace(): Boolean {
//        return if (viewModel.userPlaceLiveData.value == null && viewModel.enteredGeofenceListLiveData.value?.isEmpty() == true) {
//            if (started) {
//                LocationNotCoveredDialogFragment.newInstance { it.dismiss() }
//                    .show(supportFragmentManager, null)
//            }
//            false
//        } else {
//            true
//        }
        return true
    }

}
