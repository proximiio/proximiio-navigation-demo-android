package io.proximi.navigationdemo.ui.main

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.*
import com.mapbox.mapboxsdk.maps.MapboxMap
import io.proximi.mapbox.data.model.Amenity
import io.proximi.mapbox.data.model.Feature
import io.proximi.mapbox.library.ProximiioSearchFilter
import io.proximi.mapbox.library.Route
import io.proximi.mapbox.library.RouteCallback
import io.proximi.mapbox.library.RouteConfiguration
import io.proximi.navigationdemo.navigationservice.NavigationService
import io.proximi.navigationdemo.ui.CustomMarker
import io.proximi.navigationdemo.utils.CustomLocationComponentActivator
import io.proximi.proximiiolibrary.ProximiioGeofence
import io.proximi.proximiiolibrary.ProximiioPlace

/**
 * [ViewModel] implementation for [MainActivity]. Creates a layer between main activity and service
 * that holds all necessary data and handles service connection.
 */
class MainActivityViewModel(application: Application) : AndroidViewModel(application) {

    /** Navigation service reference */
    private var navigationService: NavigationService? = null

    /* ------------------------------------------------------------------------------------------ */
    /* State */

    /* Private LiveData that can be modified */
    private val route: MutableLiveData<Route?> = MutableLiveData()
    private val routeEvent: MutableLiveData<NavigationService.RouteEvent?> = MutableLiveData()
    private val currentHazardFeature = MutableLiveData<Feature?>()
    private val currentSegmentFeature = MutableLiveData<Feature?>()
    private val amenityList = MutableLiveData<List<Amenity>>().apply { postValue(listOf()) }
    private val featureList = MutableLiveData<List<Feature>>().apply { postValue(listOf()) }
    private val poiList = MutableLiveData<List<Feature>>().apply { postValue(listOf()) }
    private val displayLevel = MutableLiveData<Int>().apply { value = 0 }
    private val userLevel = MutableLiveData<Int>().apply { value = 0 }
    private val userPlace = MutableLiveData<ProximiioPlace?>()
    private val userLocation = MutableLiveData<Location?>()
    private val enteredGeofenceList =
        MutableLiveData<List<ProximiioGeofence>>().apply { postValue(listOf()) }
    private val markers =
        MutableLiveData<List<CustomMarker>>().apply { postValue(listOf()) }

    /* Public access for live data provided to activity. */
    val routeLiveData: LiveData<Route?> get() = route
    val routeEventLiveData: LiveData<NavigationService.RouteEvent?> get() = routeEvent
    val currentHazardFeatureLiveData: LiveData<Feature?> get() = currentHazardFeature
    val currentSegmentFeatureLiveData: LiveData<Feature?> get() = currentSegmentFeature
    val amenitiesLiveData: LiveData<List<Amenity>> get() = amenityList
    val featuresLiveData: LiveData<List<Feature>> get() = featureList
    val poisLiveData: LiveData<List<Feature>> get() = poiList
    val displayLevelLiveData: LiveData<Int> get() = displayLevel
    val userLevelLiveData: LiveData<Int> get() = userLevel
    val userLocationLiveData: LiveData<Location?> get() = userLocation
    val userPlaceLiveData: LiveData<ProximiioPlace?> = userPlace
    val enteredGeofenceListLiveData: LiveData<List<ProximiioGeofence>> get() = enteredGeofenceList
    val markersLiveData: LiveData<List<CustomMarker>> get() = markers

    /** Callback reference to start navigation. Necessary due to the service binding which might happen after activity is resumed and route start invoked. */
    private var routeStart: (() -> Unit)? = null

    /* ------------------------------------------------------------------------------------------ */
    /* Interface */

    /** Callback reference to initialize map. Necessary due to the service binding which might happen after activity is resumed and map created. */
    private var mapInitTask: (() -> Unit)? = null

    /**
     * Activity lifecycle callback. Service is bound to activity when called.
     */
    fun onActivityStart() {
        bindService()
    }

    /**
     * Activity lifecycle callback. Service is unbound to activity when called.
     */
    fun onActivityStop() {
        navigationService?.onActivityStop()
        unbindService()
    }

    /**
     * Activity lifecycle callback.
     */
    fun onActivityDestroy() {
        navigationService?.onActivityDestroy()
    }

    /**
     * Called when [ViewModel] is being destroyed (it is no longer used).
     */
    override fun onCleared() {
        super.onCleared()
        navigationService?.onActivityDestroy()
    }

    /**
     * Change map display level.
     */
    fun setDisplayLevel(level: Int) {
        navigationService?.setDisplayLevel(level)
    }

    /**
     * Cancels current navigation route.
     */
    fun routeCancel() {
        routeStart = null
        navigationService?.routeCancel()
    }

    /**
     * Should be called after mapbox map reference is obtained. It is then passed to Proximi.io mapbox SDK
     * which adds custom features to the map and handles location and map states.
     */
    fun onMapReady(mapboxMap: MapboxMap, activator: CustomLocationComponentActivator) {
        mapInitTask = {
            navigationService!!.onMapReady(mapboxMap, activator)
        }
        if (navigationService != null) {
            mapInitTask!!.invoke()
            mapInitTask = null
        }
    }

    /**
     * Starts navigation route.
     */
    fun routeFind(toPoiId: String, waypointList: List<RouteConfiguration.Waypoint> = listOf()) {
        routeStart = {
            Log.d("NAVIGATION_LOOP", "ViewModel preparation (callable)")
            routeStart = null
//            navigationService?.routeCancel()
            navigationService?.routeFind(toPoiId, waypointList)
        }
        navigationService?.let { routeStart?.invoke() }
    }

    /**
     * Start a navigation route found with [routeFind] method.
     */
    fun routeStart() {
        navigationService?.startRoute()
    }

    /**
     * Calculate route to given location.
     */
    fun routeCalculate(toPoiId: String, routeCallback: RouteCallback) {
        navigationService?.routeCalculate(toPoiId, routeCallback)
    }

    /**
     * Search for [Feature]s in Proximi.io Mapbox SDK.
     */
    fun searchPois(
        filter: ProximiioSearchFilter,
        text: String?,
        amenityCategoryId: String?
    ): List<Feature> {
        return navigationService?.searchPois(filter, text, amenityCategoryId) ?: listOf()
    }

    /**
     *  Pass permission request results to Proximi.io SDK.
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        navigationService?.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Service */

    /**
     * [NavigationService] connection reference. Provides service connection callback to obtain
     * service object to be able to pass data with the service.
     */
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as NavigationService.LocalBinder
            navigationService = binder.getService()
            navigationService!!.onActivityStart()
            navigationService!!.loadMapSdkSettings()
            mapInitTask?.invoke()
            mapInitTask = null
            setupObservers()
            routeStart?.invoke()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            navigationService = null
        }
    }

    fun set(markers: List<CustomMarker>) {
        this.markers.postValue(markers)
    }

    /**
     * Request a bind to [NavigationService].
     */
    private fun bindService() {
        getApplication<Application>().apply {
            // Bind to LocalService
            Intent(this, NavigationService::class.java).also { intent ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
        }
    }

    /**
     * Unbind [NavigationService] when its no longer needed.
     */
    private fun unbindService() {
        if (navigationService != null) {
            getApplication<Application>().apply { unbindService(connection) }
        }
        removeObservers()
    }

    /**
     * Setup observers to LiveData provided by [NavigationService].
     */
    private fun setupObservers() {
        navigationService?.let { navigationService ->
            navigationService.routeLiveData.observeForever(routeObserver)
            navigationService.routeEventLiveData.observeForever(routeEventObserver)
            navigationService.currentHazardFeatureLiveData.observeForever(
                currentHazardFeatureObserver
            )
            navigationService.currentSegmentFeatureLiveData.observeForever(
                currentSegmentFeatureObserver
            )
            navigationService.amenitiesLiveData.observeForever(amenitiesObserver)
            navigationService.featuresLiveData.observeForever(featuresObserver)
            navigationService.poisLiveData.observeForever(poisObserver)
            navigationService.displayLevelLiveData.observeForever(displayLevelObserver)
            navigationService.userLevelLiveData.observeForever(userLevelObserver)
            navigationService.userLocationLiveData.observeForever(userLocationObserver)
            navigationService.userPlaceLiveData.observeForever(userPlaceObserver)
            navigationService.enteredGeofenceListLiveData.observeForever(geofenceObserver)
        }
    }

    /**
     * Cancel observers to LiveData exposed by [NavigationService].
     */
    private fun removeObservers() {
        navigationService?.let { navigationService ->
            navigationService.routeLiveData.removeObserver(routeObserver)
            navigationService.routeEventLiveData.removeObserver(routeEventObserver)
            navigationService.currentHazardFeatureLiveData.removeObserver(
                currentHazardFeatureObserver
            )
            navigationService.currentSegmentFeatureLiveData.removeObserver(
                currentSegmentFeatureObserver
            )
            navigationService.amenitiesLiveData.removeObserver(amenitiesObserver)
            navigationService.featuresLiveData.removeObserver(featuresObserver)
            navigationService.poisLiveData.removeObserver(poisObserver)
            navigationService.displayLevelLiveData.removeObserver(displayLevelObserver)
            navigationService.userLevelLiveData.removeObserver(userLevelObserver)
            navigationService.userLocationLiveData.removeObserver(userLocationObserver)
            navigationService.userPlaceLiveData.removeObserver(userPlaceObserver)
            navigationService.enteredGeofenceListLiveData.observeForever(geofenceObserver)
        }
    }

    /* Observers for data provided by NavigationService */
    private val routeObserver = Observer<Route?> { route.postValue(it) }
    private val routeEventObserver =
        Observer<NavigationService.RouteEvent?> { routeEvent.postValue(it) }
    private val currentHazardFeatureObserver =
        Observer<Feature?> { currentHazardFeature.postValue(it) }
    private val currentSegmentFeatureObserver =
        Observer<Feature?> { currentSegmentFeature.postValue(it) }
    private val amenitiesObserver = Observer<List<Amenity>> { amenityList.postValue(it) }
    private val featuresObserver = Observer<List<Feature>> { featureList.postValue(it) }
    private val poisObserver = Observer<List<Feature>> { poiList.postValue(it) }
    private val displayLevelObserver = Observer<Int> { displayLevel.postValue(it) }
    private val userLevelObserver = Observer<Int> { userLevel.postValue(it) }
    private val userLocationObserver = Observer<Location?> { userLocation.postValue(it) }
    private val userPlaceObserver = Observer<ProximiioPlace?> { userPlace.postValue(it) }
    private val geofenceObserver =
        Observer<List<ProximiioGeofence>> { enteredGeofenceList.postValue(it) }
}

/**
 * Factory class to create [MainActivityViewModel].
 */
abstract class MainActivityViewModelFactory(private val application: Application) :
    ViewModelProvider.Factory {
//    fun <T : ViewModel?> create(modelClass: Class<T>): T {
//        return MainActivityViewModel(application) as T
//    }
}