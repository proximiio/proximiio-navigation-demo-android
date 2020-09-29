package io.proximi.navigationdemo.ui.main

import android.content.res.ColorStateList
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.android.gestures.RotateGestureDetector
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import io.proximi.navigationdemo.R
import io.proximi.navigationdemo.utils.deviceHasCompassCapability
import io.proximi.navigationdemo.utils.getColorFromAttr

/**
 * Helper class that handles map mode switching.
 */
class MapModeHelper(
    private val map: MapboxMap,
    private val locationButton: FloatingActionButton,
    private val orientationButton: FloatingActionButton,
    private val activity: MainActivity,
    private val viewModel: MainActivityViewModel
) {

    /** Mapbox location component stale status */
    var stale = false
        set (value) { field = value; applyState() }

    /** Mapbox location component is activated */
    var locationComponentActivated = false
        set (value) { field = value; if (value) onLocationComponentActivated(); }

    /** Evaluate if device has compass capabilites */
    private val hasCompassCapabilities = deviceHasCompassCapability(locationButton.context)
    /** Flag if we compass heading or 'route' heading should be used for navigation */
    private var useCompassHeadingForNavigation = false
    /** Flag if navigation is in progress currently */
    private var isNavigating = false
    /** Current map settings */
    private var currentState = States.TRACKING_BEARING
        set(value) { if (field != value ) { field = value; applyState(); } }

    init {
        locationButton.setOnClickListener { showCurrentLocation() }
        orientationButton.setOnClickListener { toggleMapOrientation() }
        map.addOnMoveListener(object: MapboxMap.OnMoveListener {
            override fun onMove(detector: MoveGestureDetector) {}
            override fun onMoveEnd(detector: MoveGestureDetector) {}
            override fun onMoveBegin(detector: MoveGestureDetector) {
                currentState = when (currentState) {
                    States.TRACKING_NORTH   -> States.CUSTOM_NORTH
                    States.TRACKING_BEARING -> States.CUSTOM_CUSTOM
                    States.TRACKING_CUSTOM  -> States.CUSTOM_CUSTOM
                    States.CUSTOM_NORTH     -> States.CUSTOM_CUSTOM
                    States.CUSTOM_CUSTOM    -> States.CUSTOM_CUSTOM
                }
            }
        })
        map.addOnRotateListener(object: MapboxMap.OnRotateListener {
            override fun onRotate(detector: RotateGestureDetector) {}
            override fun onRotateEnd(detector: RotateGestureDetector) {}
            override fun onRotateBegin(detector: RotateGestureDetector) {
                currentState = when (currentState) {
                    States.TRACKING_NORTH   -> States.TRACKING_CUSTOM
                    States.TRACKING_BEARING -> States.TRACKING_CUSTOM
                    States.TRACKING_CUSTOM  -> States.TRACKING_CUSTOM
                    States.CUSTOM_NORTH     -> States.CUSTOM_CUSTOM
                    States.CUSTOM_CUSTOM    -> States.CUSTOM_CUSTOM
                }
            }
        })
        currentState = States.TRACKING_BEARING
    }

    /**
     * Should be called when navigation starts to adjust map view behaviour.
     */
    fun onNavigationStart() {
        if (!isNavigating) {
            isNavigating = true
            showCurrentLocation()
        }
    }

    /**
     * Should be called when navigation ends to adjust map view behaviour.
     */
    fun onNavigationEnd() {
        if (isNavigating) {
            isNavigating = false
            currentState = States.TRACKING_BEARING
            applyState()
        }
    }

    /**
     * Toggle between using compass heading or route heading (i.e. direction based on current navigation route)
     * to orient map location component.
     */
    fun setUseCompassHeadingForNavigation(useCompassHeading: Boolean) {
        useCompassHeadingForNavigation = useCompassHeading
        applyState()
    }

    /**
     * Process current state ([currentState]) and apply [CameraMode] and [RenderMode].
     * Also updates orientation button view to reflect current state.
     */
    private fun applyState() {
        if (map.locationComponent.isLocationComponentActivated && stale) {
            map.locationComponent.cameraMode = CameraMode.TRACKING
            map.locationComponent.renderMode = RenderMode.NORMAL
        } else if (map.locationComponent.isLocationComponentActivated) {
            map.locationComponent.cameraMode = if (isNavigating && !useCompassHeadingForNavigation) currentState.navigationCameraMode else currentState.mapCameraMode
            applyRenderMode()
        }
        val color = if (currentState.orientationEnabled) {
            orientationButton.setImageResource(R.drawable.ic_compass)
            orientationButton.context.getColorFromAttr(R.attr.fabBackgroundColor)
        } else {
            orientationButton.setImageResource(R.drawable.ic_compass_disabled)
            orientationButton.context.getColorFromAttr(R.attr.fabBackgroundDisabledColor)
        }
        orientationButton.backgroundTintList = ColorStateList.valueOf(color)
    }

    /**
     * Evalues current state and (safely) applies appropriate [RenderMode].
     */
    private fun applyRenderMode() {
        if (map.locationComponent.isLocationComponentActivated) {
            map.locationComponent.renderMode =
                if (isNavigating && (!useCompassHeadingForNavigation || !hasCompassCapabilities))
                    RenderMode.GPS
                else if (hasCompassCapabilities)
                    RenderMode.COMPASS
                else
                    RenderMode.NORMAL
        }
    }

    /**
     * Shows (moves to) current location on map. Also swiches map mode to follow current location.
     */
    private fun showCurrentLocation() {
        activity.checkPhoneLocationEnabled { activity.checkSupportedPlace() }
        map.getStyle {
            if (map.locationComponent.isLocationComponentActivated) {
                zoomToLocation()
                currentState = when (currentState) {
                    States.CUSTOM_CUSTOM -> States.TRACKING_BEARING
                    States.CUSTOM_NORTH -> States.TRACKING_NORTH
                    else -> {
                        applyState(); currentState
                    }
                }
                viewModel.setDisplayLevel(viewModel.userLevelLiveData.value!!)
            }
        }
    }

    /**
     * Zoom to current location on map.
     */
    private fun zoomToLocation() {
        if (map.locationComponent.lastKnownLocation != null) {
            val location = map.locationComponent.lastKnownLocation!!
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 20.0))
        }
    }

    /***
     * Toggles map orientation  between north and following heading.
     */
    private fun toggleMapOrientation() {
        if (locationComponentActivated) {
            if (hasCompassCapabilities) {
                currentState = when (currentState) {
                    States.TRACKING_NORTH   -> States.TRACKING_BEARING
                    States.TRACKING_BEARING -> States.TRACKING_NORTH
                    States.TRACKING_CUSTOM  -> States.TRACKING_BEARING
                    States.CUSTOM_NORTH     -> States.TRACKING_BEARING
                    States.CUSTOM_CUSTOM    -> States.TRACKING_BEARING
                }
            } else {
                when (currentState) {
                    States.TRACKING_CUSTOM  -> currentState = States.TRACKING_NORTH
                    States.CUSTOM_CUSTOM    -> currentState = States.CUSTOM_NORTH
                }
            }
        }
    }

    /**
     * Call when location component is activated. Will put map into 'initial' state by zooming to current location and following user.
     */
    private fun onLocationComponentActivated() {
        applyState()
        map.locationComponent.zoomWhileTracking(20.0)
    }

    /**
     * List of desired map states for easier manipulation.
     */
    private enum class States(val mapCameraMode: Int, val navigationCameraMode: Int, val orientationEnabled: Boolean) {
        TRACKING_NORTH(CameraMode.TRACKING_GPS_NORTH, CameraMode.TRACKING_GPS_NORTH, false),
        TRACKING_CUSTOM(CameraMode.TRACKING, CameraMode.TRACKING, false),
        TRACKING_BEARING(CameraMode.TRACKING_COMPASS, CameraMode.TRACKING_GPS, true),
        CUSTOM_NORTH(CameraMode.NONE, CameraMode.TRACKING_GPS_NORTH, false),
        CUSTOM_CUSTOM(CameraMode.NONE, CameraMode.NONE, false);
    }
}