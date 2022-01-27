package io.proximi.navigationdemo.navigationservice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.os.*
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import com.mapbox.mapboxsdk.maps.MapboxMap
import io.proximi.mapbox.data.model.Amenity
import io.proximi.mapbox.data.model.Feature
import io.proximi.mapbox.library.*
import io.proximi.navigationdemo.ProximiioAuthToken
import io.proximi.navigationdemo.R
import io.proximi.navigationdemo.ui.SettingsActivity
import io.proximi.navigationdemo.ui.SettingsActivity.Companion.SIMULATE_ROUTE
import io.proximi.navigationdemo.ui.main.MainActivity
import io.proximi.navigationdemo.utils.CustomLocationComponentActivator
import io.proximi.navigationdemo.utils.RouteConfigurationHelper
import io.proximi.navigationdemo.utils.UnitHelper
import io.proximi.navigationdemo.utils.getDrawable
import io.proximi.proximiiolibrary.*
import io.proximi.proximiiolibrary.routesnapping.database.PxWayfindingRoutable
import java.util.*


/**
 * Service to handle 'background' functionality (navigation) and creates an extra layer holding
 * necessary data.
 */
class NavigationService : LifecycleService() {

    private val TAG = NavigationService::class.java.simpleName
    private lateinit var proximiioMapbox: ProximiioMapbox
    private lateinit var proximiioAPI: ProximiioAPI
    private lateinit var sharedPreferences: SharedPreferences
    private var tts: TextToSpeech? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var mapboxMap: MapboxMap? = null
    private var handler = Handler()

    companion object {
        const val NOTIFICATION_ID = 631
        private const val NOTIFICATION_CHANNEL_ID_LOW = "notification_navigation_low_id"
        private const val NOTIFICATION_CHANNEL_ID_HIGH = "notification_navigation_high_id"
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Location state */

    /* Mutable livedata (private) to hold and update values */
    private val displayLevel = MutableLiveData<Int>().apply { postValue(0) }
    private val enteredGeofenceList =
        MutableLiveData<List<ProximiioGeofence>>().apply { postValue(listOf()) }
    private val userLevel = MutableLiveData<Int>().apply { postValue(0) }
    private val userPlace = MutableLiveData<ProximiioPlace?>().apply { postValue(null) }
    private val userLocation = MutableLiveData<Location?>().apply { postValue(null) }

    /* Non-mutable livedata to observe state externaly */
    val displayLevelLiveData: LiveData<Int> get() = displayLevel
    val enteredGeofenceListLiveData: LiveData<List<ProximiioGeofence>> get() = enteredGeofenceList
    val userLevelLiveData: LiveData<Int> get() = userLevel
    val userLocationLiveData: LiveData<Location?> get() = userLocation
    val userPlaceLiveData: LiveData<ProximiioPlace?> = userPlace

    /* ------------------------------------------------------------------------------------------ */
    /* Navigation state */

    private val currentHazardFeature = MutableLiveData<Feature?>().apply { postValue(null) }
    private val currentSegmentFeature = MutableLiveData<Feature?>().apply { postValue(null) }
    private val route: MutableLiveData<Route?> = MutableLiveData<Route?>().apply { postValue(null) }
    private val routeEvent: MutableLiveData<RouteEvent?> =
        MutableLiveData<RouteEvent?>().apply { postValue(null) }

    val currentHazardFeatureLiveData: LiveData<Feature?> get() = currentHazardFeature
    val currentSegmentFeatureLiveData: LiveData<Feature?> get() = currentSegmentFeature
    val routeLiveData: LiveData<Route?> get() = route
    val routeEventLiveData: LiveData<RouteEvent?> get() = routeEvent

    /* ------------------------------------------------------------------------------------------ */
    /* Map data */

    private val poiListTypes = listOf(
        ProximiioFeatureType.POI,
        ProximiioFeatureType.ELEVATOR,
        ProximiioFeatureType.ESCALATOR,
        ProximiioFeatureType.STAIRCASE
    )
    private val poiList = MutableLiveData<List<Feature>>().apply { postValue(listOf()) }

    val amenitiesLiveData: LiveData<List<Amenity>> get() = proximiioMapbox.amenities
    val featuresLiveData: LiveData<List<Feature>> get() = proximiioMapbox.features
    val poisLiveData: LiveData<List<Feature>> get() = poiList

    /* ------------------------------------------------------------------------------------------ */
    /* Service lifecycle callbacks */

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        setupTts()
        setupMapSdk()
        setupLocationSdk()
        loadMapSdkSettings()
        updateNotification(null)
        setupWakelock()
        // Setup livedata observer to filter out POI lists from all features
        featuresLiveData.observe(this, Observer { updatePoiList(it) })
        userPlaceLiveData.observe(this, Observer { updatePoiList(featuresLiveData.value!!) })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return Service.START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        proximiioAPI.destroyService(false)
        proximiioMapbox.onDestroy()
        if (route.value != null) {
            proximiioMapbox.routeCancel()
        }
        Log.d(TAG, "Service destroyed.")
        super.onDestroy()
    }

    private fun updatePoiList(featureList: List<Feature>) {
        val newPoiList = featureList
            .filter { poiListTypes.contains(it.getType()) }
            .filter { it.getPlaceId() == userPlaceLiveData.value?.id }
            .sortedBy { it.getTitle() }
        poiList.postValue(newPoiList)
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Service bind callbacks */

    private var isBound = false
    private val SERVICE_STOP_TIMEOUT = 120000L
    private var serviceStopTimer: Timer? = null

    /**
     * On service bind, pass service reference to the binder.
     */
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        isBound = true
        cancelServiceStop()
        return LocalBinder()
    }

    /**
     * On unbind, schedule service end if possible (i.e. if navigation is in progress, service won't stop).
     */
    override fun onUnbind(intent: Intent?): Boolean {
        super.onUnbind(intent)
        isBound = false
        if (routeEvent.value == null || routeEvent.value!!.eventType == RouteUpdateType.CALCULATING || routeEvent.value!!.eventType.isRouteEnd()) {
            scheduleServiceStop()
        }
        return false
    }

    private fun scheduleServiceStop() {
        cancelServiceStop()
        serviceStopTimer = Timer()
        serviceStopTimer?.schedule(ServiceStopTask(), SERVICE_STOP_TIMEOUT)
    }

    private fun cancelServiceStop() {
        serviceStopTimer?.cancel()
        serviceStopTimer?.purge()
        serviceStopTimer = null
    }

    inner class LocalBinder : Binder() {
        fun getService(): NavigationService = this@NavigationService
    }

    private inner class ServiceStopTask : TimerTask() {
        override fun run() {
            if (routeEvent.value?.eventType == null || routeEvent.value?.eventType == RouteUpdateType.CALCULATING || routeEvent.value?.eventType?.isRouteEnd() == true) {
                Log.d(TAG, "service stopping")
                stopSelf()
            } else {
                Log.d(TAG, "service kept alive")
                scheduleServiceStop()
            }
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Nitofication Management */

    private var notificationLastAlertNodeIndex = 0

    /**
     * Update notification with information about current navigation guidance data, or show
     * generic navigation if not navigating.
     */
    private fun updateNotification(routeEvent: RouteEvent?) {
        val intent = PendingIntent.getActivity(
            baseContext,
            0,
            Intent(baseContext, MainActivity::class.java),
            FLAG_IMMUTABLE
        )

        createNotificationChannelHigh()
        createNotificationChannelLowPriority()
        val notificationBuilder =
            if (routeEvent == null || isBound || routeEvent.eventType == RouteUpdateType.CALCULATING) {

                // Basic notification (NOT navigating)
                NotificationCompat.Builder(
                    this,
                    NOTIFICATION_CHANNEL_ID_LOW
                )
                    .setSmallIcon(R.drawable.ic_start_walking)
                    .setContentTitle(getString(R.string.notification_navigation_title))
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(intent)
            } else {
                if (routeEvent.eventType.isRouteEnd()) {
                    Handler().postDelayed({ updateNotification(null) }, 2000)
                }
                // Navigation guidance notification (navigating)
                NotificationCompat.Builder(
                    this,
                    NOTIFICATION_CHANNEL_ID_HIGH
                )
                    .setSmallIcon(getNotificationIcon(routeEvent))
                    .setLargeIcon(getNotificationImage(routeEvent))
                    .setColor(
                        ContextCompat.getColor(
                            this,
                            R.color.colorNotification
                        )
                    )
                    .setColorized(true)
                    .setContentTitle(routeEvent.text)
                    .setContentText(routeEvent.additionalText)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setOnlyAlertOnce(!notificationShouldAlert(routeEvent))
                    .setContentIntent(intent)
            }
        // If service is not bound, add action to cancel navigation
        if (!isBound) {
            val pi = PendingIntent.getBroadcast(
                baseContext,
                0,
                Intent(baseContext, StopNavigationServiceReceiver::class.java),
                FLAG_IMMUTABLE
            )
            notificationBuilder.addAction(
                R.id.cancelRouteButton,
                getString(R.string.notification_action_turn_off),
                pi
            )
        }
        val notification = notificationBuilder.build()
        startForeground(NOTIFICATION_ID, notification)
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    /**
     * Create low priority notification channel (basic notification or when app in foreground == service is bound).
     */
    private fun createNotificationChannelLowPriority() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_navigation_channel_name)
            val descriptionText = getString(R.string.notification_navigation_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID_LOW, name, importance).apply {
                description = descriptionText
                this.enableVibration(false)
                this.enableLights(false)
            }
            // Register the channel with the system
            NotificationManagerCompat.from(this).createNotificationChannel(channel)
        }
    }

    /**
     * Create high priority notification channel (for background navigation guidance).
     */
    private fun createNotificationChannelHigh() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_navigation_channel_name)
            val descriptionText = getString(R.string.notification_navigation_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel =
                NotificationChannel(NOTIFICATION_CHANNEL_ID_HIGH, name, importance).apply {
                    description = descriptionText

                }
            // Register the channel with the system
            NotificationManagerCompat.from(this).createNotificationChannel(channel)
        }
    }

    /**
     * Translate navigation guidance event into Drawable IDs.
     */
    private fun getNotificationIcon(routeEvent: RouteEvent): Int {
        return when (routeEvent.eventType) {
            RouteUpdateType.DIRECTION_IMMEDIATE -> routeEvent.data!!.stepDirection.getDrawable()
            RouteUpdateType.DIRECTION_SOON -> routeEvent.data!!.stepDirection.getDrawable()
            RouteUpdateType.FINISHED -> R.drawable.ic_preview_destination
            RouteUpdateType.CANCELED -> R.drawable.ic_cancel
            RouteUpdateType.RECALCULATING -> R.drawable.ic_my_location
            else -> R.drawable.ic_start_walking
        }
    }

    /**
     * Translate notification event into Bitmap.
     */
    private fun getNotificationImage(routeEvent: RouteEvent): Bitmap? {
        val imageId = when (routeEvent.eventType) {
            RouteUpdateType.DIRECTION_IMMEDIATE -> routeEvent.data!!.stepDirection.getDrawable()
            RouteUpdateType.DIRECTION_SOON -> routeEvent.data!!.stepDirection.getDrawable()
            RouteUpdateType.FINISHED -> R.drawable.ic_preview_destination
            RouteUpdateType.CANCELED -> R.drawable.ic_cancel
            else -> null
        }
        return imageId?.let {
            val drawable = resources.getDrawable(it)
            DrawableCompat.setTint(drawable, Color.WHITE)
            drawable.toBitmap()
        }
    }

    /**
     * Evaluate if notification should alert (ping) user.
     */
    private fun notificationShouldAlert(routeEvent: RouteEvent): Boolean {
        val alert = (
                routeEvent.eventType == RouteUpdateType.DIRECTION_SOON
                        || routeEvent.eventType == RouteUpdateType.DIRECTION_IMMEDIATE
                ) && notificationLastAlertNodeIndex != routeEvent.data!!.nodeIndex
        if (alert) {
            notificationLastAlertNodeIndex = routeEvent.data?.nodeIndex ?: 0
        }
        return alert
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Activity interface */

    fun searchPois(
        filter: ProximiioSearchFilter,
        text: String?,
        amenityCategoryId: String?
    ): List<io.proximi.mapbox.data.model.Feature> {
        return proximiioMapbox.search(filter, text, amenityCategoryId)
    }

    fun onActivityStart() {
        proximiioMapbox.onStart()
        isBound = true
        updateNotification(routeEvent.value)
    }

    fun onActivityStop() {
        isBound = false
//        if (routeEvent.value?.eventType?.isRouteEnd() == true) routeEvent.postValue(null)
        if (routeEvent.value?.eventType?.isRouteEnd() == false) {
            updateNotification(routeEvent.value)
        } else {
            updateNotification(null)
        }
        proximiioMapbox.onStop()
    }

    fun onActivityDestroy() {
        proximiioMapbox.onDestroy()
        mapboxMap = null
    }

    fun onMapReady(mapboxMap: MapboxMap, activator: CustomLocationComponentActivator) {
        if (this.mapboxMap === mapboxMap) return
        this.mapboxMap = mapboxMap
        proximiioMapbox.onMapReady(mapboxMap, activator)
        mapboxMap.getStyle {
            proximiioMapbox.updateDisplayLevel(displayLevel.value!!)
            proximiioMapbox.updateUserLevel(displayLevel.value!!)
        }
    }

    fun routeFind(toPoiId: String, waypointList: List<RouteConfiguration.Waypoint>) {
        Log.d("NAVIGATION_LOOP", "Service starting route")
        val destination = featuresLiveData.value!!.first { it.id == toPoiId }
        val configuration = RouteConfigurationHelper.create(baseContext, destination, waypointList)
        proximiioMapbox.routeFindAndPreview(configuration, routeUpdateListener)
    }

    fun startRoute() {
        proximiioMapbox.routeStart()
    }

    fun routeCancel() {
        proximiioMapbox.routeCancel()
    }

    fun setDisplayLevel(level: Int) {
        displayLevel.postValue(level)
    }

    fun routeCalculate(toPoiId: String, routeCallback: RouteCallback) {
        val destination = featuresLiveData.value!!.first { it.id == toPoiId }
        val configuration = RouteConfigurationHelper.create(baseContext, destination, listOf())
        proximiioMapbox.routeCalculate(configuration, routeCallback)
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        proximiioAPI.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Location SDK */

    /**
     * Setup location SDK with no notification (we have custom one).
     */
    private fun setupLocationSdk() {
        // Setup options
        val options = ProximiioOptions().apply {
            notificationMode = ProximiioOptions.NotificationMode.DISABLED
        }

        // Create Proximi.io API
        proximiioAPI = ProximiioAPI(TAG, this, options).apply {
            setAuth(ProximiioAuthToken.TOKEN, true)
            setListener(apiListener)
//            pdrCorrectionThreshold(4.0)
            snapToRouteEnabled(true)
            snapToRouteThreshold(20.0)
        }
    }

    /**
     * Proximiio API listener.
     */
    private val apiListener = object : ProximiioListener() {

        /**
         * Update level values when location changed to different floor.
         * Also updates ProximiioPlace based on current floor.
         */
        override fun changedFloor(floor: ProximiioFloor?) {
            val floorLevel = floor?.floorNumber ?: 0

            // Update display level if it is currently also shown on map
            if (userLevel.value == displayLevel.value) {
                displayLevel.postValue(floorLevel)
            }

            // Update user level
            userLevel.postValue(floorLevel)

            // Update place if it has changed
            if (userPlace.value != floor?.place) userPlace.postValue(floor?.place)

            simulationProcessorRoutes()
        }

        /**
         * Store entered geofences.
         */
        override fun geofenceEnter(geofence: ProximiioGeofence) {
            val newList = mutableListOf(geofence)
            newList.addAll(enteredGeofenceList.value!!)
            enteredGeofenceList.postValue(newList)
        }

        /**
         * Remove geofence from entered list on exit.
         */
        override fun geofenceExit(geofence: ProximiioGeofence, dwellTime: Long?) {
            enteredGeofenceList.postValue(enteredGeofenceList.value!!.filter { it.id != geofence.id })
        }

        /**
         * Update user position.
         */
        override fun position(location: Location) {
            // Update user locationchangedFloor
            userLocation.postValue(location)
        }

        override fun positionExtended(
            lat: Double,
            lon: Double,
            accuracy: Double,
            type: ProximiioGeofence.EventType?
        ) {
            super.positionExtended(lat, lon, accuracy, type)
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Map SDK */

    /**
     * Create and setup ProximiioMapbox SDK.
     */
    private fun setupMapSdk() {
        proximiioMapbox =
            ProximiioMapbox.getInstance(baseContext, ProximiioAuthToken.TOKEN, null).apply {
                setFloorPlanVisibility(false)
                hazardCallback(hazardCallback)
                segmentCallback(segmentCallback)
                setUserLocationToRouteSnappingEnabled(true)
                setUserLocationToRouteSnappingThreshold(4.0)
                setRouteFinishThreshold(2.0)
                setStepImmediateThreshold(2.5)
                setStepPreparationThreshold(5.0)
                setRoutePathFixDistance(1.0)
            }
        // Setup observers to pass data updates to ProximiioMapbox SDK
        displayLevel.observe(this, Observer { proximiioMapbox.updateDisplayLevel(it) })
        userLocation.observe(this, Observer { it?.let { proximiioMapbox.updateUserLocation(it) } })
        proximiioMapbox.syncStatus.observe(this, Observer {
            if (it == SyncStatus.INITIAL_NETWORK_ERROR || it == SyncStatus.INITIAL_ERROR) {
                handler.postDelayed({ proximiioMapbox.startSyncNow() }, 1000)
            }
        })
        userLevel.observe(this, Observer { proximiioMapbox.updateUserLevel(it) })
        routeEvent.observe(this, Observer {
            updateNotification(it)
            // On route end schedule service stop if not bound
            if (routeEvent.value == null || routeEvent.value!!.eventType.isRouteEnd()) {
                if (!isBound) scheduleServiceStop()
            }
        })
    }

    /**
     * Load ProximiioMap SDK settings.
     */
    fun loadMapSdkSettings() {
        val preferences = sharedPreferences
        // Set TTS
        if (preferences.getBoolean(SettingsActivity.TTS_ENABLED, true)) {
            if (tts == null) {
                proximiioMapbox.ttsDisable()
                Toast.makeText(
                    baseContext,
                    R.string.tts_not_available, Toast.LENGTH_LONG
                ).show()
                Log.d(TAG, "Route start: TTS was not available.")
            } else {
                proximiioMapbox.ttsEnable(tts!!)
                Log.d(TAG, "Route start: TTS was available and ready.")
            }
        } else {
            Log.d(TAG, "Route start: TTS disabled in settings.")
            proximiioMapbox.ttsDisable()
        }
        // Load selected metadata
        val metadataKey =
            preferences.getString(SettingsActivity.ACCESSIBILITY_TTS_DISABILITY, "6")!!.toInt()
        val metadataKeys = if (metadataKey == 6) listOf<Int>() else listOf(metadataKey)
        val segmentAlert = preferences.getBoolean(SettingsActivity.TTS_SEGMENT_ENABLED, true)
        proximiioMapbox.apply {
            // TTS settings
            ttsHeadingCorrectionEnabled(
                preferences.getBoolean(
                    SettingsActivity.TTS_HEADING_CORRECTION,
                    true
                )
            )
            ttsDecisionAlert(
                preferences.getBoolean(SettingsActivity.TTS_DECISION_ENABLED, false),
                metadataKeys
            )
            ttsHazardAlert(
                preferences.getBoolean(SettingsActivity.TTS_HAZARD_ENABLED, true),
                metadataKeys
            )
            ttsLandmarkAlert(
                preferences.getBoolean(SettingsActivity.TTS_LANDMARK_ENABLED, true),
                metadataKeys
            )
            ttsSegmentAlert(segmentAlert, segmentAlert, metadataKeys)
            ttsLevelChangerMetadataKeys(metadataKeys)
            ttsReassuranceInstructionEnabled(
                preferences.getBoolean(
                    SettingsActivity.TTS_REASSURANCE_ENABLED,
                    false
                )
            )
            ttsReassuranceInstructionDistance(
                preferences.getString(
                    SettingsActivity.TTS_REASSURANCE_DISTANCE,
                    "15"
                )!!.toDouble()
            )
            ttsDestinationMetadataKeys(metadataKeys)
            // Set desired units
            if (preferences.getString(
                    SettingsActivity.ROUTE_UNITS,
                    SettingsActivity.ROUTE_UNITS_STEP
                )!! == SettingsActivity.ROUTE_UNITS_STEP
            ) {
                setUnitConversion(UnitHelper.STEPS)
            } else {
                setUnitConversion(UnitHelper.METERS)
            }
            setRouteSimulationEnabled(
                sharedPreferences.getBoolean(
                    SIMULATE_ROUTE,
                    false
                )
            ) { level, _, finished ->
                if (this@NavigationService.displayLevel.value != level) {
                    this@NavigationService.displayLevel.postValue(level)
                }
                if (this@NavigationService.userLevel.value != level) {
                    this@NavigationService.userLevel.postValue(level)
                }
            }
        }
        // Initiate vibration service if vibration is enabled
        vibrator = if (preferences.getBoolean(SettingsActivity.ACCESSIBILITY_HAPTIC, false)) {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        } else {
            null
        }
    }

    // Simulation processor logic
    private fun simulationProcessorRoutes(currentRoute: Route? = null) {
        val currentFloor = userLevel.value

        val routesForCurrentFloor = currentRoute?.nodeList?.filter { it.level == currentFloor }
            ?.mapNotNull { it.lineStringFeatureTo }
            ?.map { PxWayfindingRoutable.fromGeoJsonFeature(it, null) }?.toCollection(ArrayList())
    }

    /**
     * Listener for route navigation updates.
     */
    private val routeUpdateListener = object : RouteCallback {

        /**
         * Remember segment number where vibration was last triggered to prevent repeats.
         */
        private var vibratedSegmentNode: Int? = null

        /**
         * Route was (re)calculated updated.
         */
        override fun onRoute(newRoute: Route?) {
            route.postValue(newRoute)
            simulationProcessorRoutes(newRoute)
        }

        /**
         * Route navigation event callbacks,
         */
        override fun routeEvent(
            eventType: RouteUpdateType,
            text: String,
            additionalText: String?,
            data: RouteUpdateData?
        ) {
            if (eventType.isRouteEnd()) {
                vibratedSegmentNode = null
                currentHazardFeature.postValue(null)
                currentSegmentFeature.postValue(null)
                route.postValue(null)
                simulationProcessorRoutes()
            }
            if (
                (eventType == RouteUpdateType.DIRECTION_IMMEDIATE && data!!.nodeIndex != vibratedSegmentNode)
                || eventType.isRouteEnd()
            ) {
                vibrate()
                vibratedSegmentNode = data?.nodeIndex
            }
            routeEvent.postValue(
                RouteEvent(
                    eventType,
                    text,
                    additionalText,
                    data
                )
            )
        }
    }

    /**
     * Proximiio mapbox callback for hazards during navigation.
     */
    private val hazardCallback = object : NavigationInterface.HazardCallback {
        override fun enteredHazardRange(hazard: Feature) {
            currentHazardFeature.postValue(hazard)
        }

        override fun exitedHazardRange(hazard: Feature) {
            if (currentHazardFeature.value == hazard) {
                currentHazardFeature.postValue(null)
            }
        }
    }

    /**
     * Proximiio mapbox callback for segments during navigation.
     */
    private val segmentCallback = object : NavigationInterface.SegmentCallback {
        private var currentSegmentList = listOf<Feature>()

        override fun onSegmentEntered(segment: Feature) {
            currentSegmentList = mutableListOf<Feature>().apply {
                addAll(currentSegmentList)
                add(segment)
            }.sortedByDescending { it.getSegmentPriority() }
            currentSegmentFeature.postValue(currentSegmentList.firstOrNull())
        }

        override fun onSegmentLeft(segment: Feature) {
            currentSegmentList = currentSegmentList.filter { it != segment }
            currentSegmentFeature.postValue(currentSegmentList.firstOrNull())
        }
    }

    /**
     * Wrapper class for route event for easier storage for live data.
     */
    data class RouteEvent(
        val eventType: RouteUpdateType,
        val text: String,
        val additionalText: String?,
        val data: RouteUpdateData?
    )

    /**
     * trigger vibration. Will not vibrate if vibrator is null (i.e. user disabled in app preferences).
     */
    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(75, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            //deprecated in API 26
            vibrator?.vibrate(75)
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* TTS */

    /**
     * Initialize TTS engine.
     */
    private fun setupTts() {
        Log.d(TAG, "TTS setup: starting.")
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.ERROR) {
                Log.d(TAG, "TTS setup: error status thrown.")
                tts = null
            }
            val stringId = when (status) {
                TextToSpeech.ERROR -> R.string.tts_start_error
                TextToSpeech.SUCCESS -> R.string.tts_start_success
                else -> R.string.tts_start_unknown
            }
            Log.d(TAG, "TTS setup: success ready. ($status)")
            if (status != TextToSpeech.SUCCESS) Toast.makeText(
                baseContext,
                stringId,
                Toast.LENGTH_LONG
            ).show()
        }

    }

    /* ------------------------------------------------------------------------------------------ */
    /* Wakelock */

    /**
     * Engage wakelock based on navigation status (i.e. wakelock when navigation is in progress).
     */
    private fun setupWakelock() {
        route.observe(this, Observer {
            if (it != null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    NavigationService::class.java.simpleName
                )
                wakeLock!!.acquire(15 * 60 * 1000)
            } else {
                if (wakeLock?.isHeld == true) wakeLock?.release()
            }
        })
    }
}