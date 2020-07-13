package io.proximi.navigationdemo.ui.searchitem

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import com.mapbox.geojson.Point
import io.proximi.navigationdemo.*
import io.proximi.navigationdemo.ui.main.MainActivity
import io.proximi.navigationdemo.utils.*
import io.proximi.mapbox.data.model.Feature
import io.proximi.mapbox.library.*
import io.proximi.mapbox.library.Route
import kotlinx.android.synthetic.main.activity_search_item_detail.*
import java.util.*
import java.util.Calendar.DAY_OF_WEEK

/**
 * Activity to show detailed information for single search item (feature).
 */
class SearchItemDetailActivity : ScaledContextActivity() {

    companion object {
        private const val EXTRA_FEATURE = "extra_feature"

        /**
         * Start this activity for result from fragment.
         */
        fun startForResult(fragment: Fragment, resultCode:Int, feature: Feature) {
            val intent = Intent(fragment.context, SearchItemDetailActivity::class.java)
            intent.putExtra(EXTRA_FEATURE, feature)
            fragment.startActivityForResult(intent, resultCode)
        }

        /**
         * Start this activity for result from activity.
         */
        fun startForResult(activity: Activity, resultCode:Int, feature: Feature) {
            val intent = Intent(activity, SearchItemDetailActivity::class.java)
            intent.putExtra(EXTRA_FEATURE, feature)
            activity.startActivityForResult(intent, resultCode)
        }
    }

    /**
     * Currently displayed feature
     */
    private lateinit var feature: Feature

    /**
     * Proximi.io Mapbox SDK reference.
     */
    private lateinit var proximiioMapbox: ProximiioMapbox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_item_detail)

        // Get Proximi.io mapbox instance.
        proximiioMapbox = ProximiioMapbox.getInstance(baseContext, ProximiioAuthToken.TOKEN)
        // Extract feature from starting intent
        feature = intent.getParcelableExtra<Feature>(EXTRA_FEATURE) ?: error("Feature not received!")

        // Setup image gallery recycler
        setupImageGalleryRecyclerView()

        // Set feature data
        title = feature.getTitle()
        recyclerView.adapter = SearchItemDetailImageAdapter(feature.imageUrlList)
        featureNameBottomTextView.text = feature.getTitle()
        levelTextView.text = feature.levelString
        val featureDescription = feature.description
        val descriptionVisibility = if (featureDescription != null && featureDescription.isNotEmpty()) {
            descriptionTextView.text = feature.description
            View.VISIBLE
        } else {
            View.GONE
        }
        descriptionImageView.visibility = descriptionVisibility
        descriptionTextView.visibility = descriptionVisibility
        descriptionLabelTextView.visibility = descriptionVisibility
        openHoursTextView.text = feature.openHours
        loadDistanceEstimates()

        // Set navigation start onclick listener
        tripButtonLayout.setOnClickListener { startNavigation() }
    }

    /**
     * Setup [androidx.recyclerview.widget.RecyclerView] for feature's images.
     */
    private fun setupImageGalleryRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(baseContext, LinearLayoutManager.HORIZONTAL, false)
        LinearSnapHelper().apply { attachToRecyclerView(recyclerView) }
        val typedValue = TypedValue()
        resources.getValue(R.dimen.search_item_detail_recycler_ratio, typedValue, true)
        val recyclerRatio = typedValue.float
        resources.getValue(R.dimen.search_item_detail_recycler_image_ratio, typedValue, true)
        val imageViewRatio = typedValue.float
        val padding = (resources.displayMetrics.widthPixels.toDouble() / 4.0 * (recyclerRatio - imageViewRatio)).toInt()
        recyclerView.setPadding(padding, 0, padding, 0)
    }

    /**
     * Estimate distance to the feature from current location and update the information in the UI.
     */
    private fun loadDistanceEstimates() {
        val accessibleOptions = RouteOptionsHelper.create(baseContext)
        proximiioMapbox.routeCalculate((feature.featureGeometry as Point).toLocation(), feature.getLevel() ?: 0, feature.getTitle(), accessibleOptions, FeatureDistanceCalculationCallback(routeAccessibleTextView))
    }

    /**
     * Finish activity and return result with information about the feature.
     */
    private fun startNavigation() {
        Intent().also { result ->
            result.putExtra(MainActivity.EXTRA_LATITUDE_EXTRA, (feature.featureGeometry as Point).latitude())
            result.putExtra(MainActivity.EXTRA_LONGITUDE_EXTRA, (feature.featureGeometry as Point).longitude())
            result.putExtra(MainActivity.EXTRA_LEVEL_EXTRA, feature.getLevel() ?: 0)
            result.putExtra(MainActivity.EXTRA_TITLE_EXTRA, feature.getTitle())
            result.putExtra(MainActivity.EXTRA_POI_ID, feature.id)
            setResult(Activity.RESULT_OK, result)
        }
        finish()
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Feature property helpers */

    /**
     * Obtain description from feature metadata.
     */
    private val Feature.description: String?
        get() {
            return if (
                getMetadata() != null
                && getMetadata()!!.has("description")
                && getMetadata()!!["description"].isJsonObject
                && getMetadata()!!["description"].asJsonObject.has(Locale.getDefault().language))
            {
                getMetadata()!!["description"].asJsonObject[Locale.getDefault().language].asString
            } else {
                null
            }
        }

    /**
     * Convert feature level attribute to human readable string.
     */
    private val Feature.levelString: String
        get() = getString(when (getLevel()) {
            0   -> R.string.floor_0
            1   -> R.string.floor_1
            2   -> R.string.floor_2
            3   -> R.string.floor_3
            4   -> R.string.floor_4
            5   -> R.string.floor_5
            else -> R.string.floor_0
        })

    /**
     * Obtain 'open hours' from feature metadata.
     */
    private val Feature.openHours: String
        get() {
            if (
                getMetadata() != null
                && getMetadata()!!.has("openHours")
                && getMetadata()!!["openHours"].isJsonObject
            ) {
                val hours = getMetadata()!!["openHours"].asJsonObject
                if (hours.size() == 7) {
                    val dayOfWeek = (Calendar.getInstance().get(DAY_OF_WEEK) - 1).toString()
                    if (hours.has(dayOfWeek) && hours[dayOfWeek].isJsonObject) {
                        val hoursForDay = hours[dayOfWeek].asJsonObject
                        if (hoursForDay.has(Locale.getDefault().language)) {
                            return hoursForDay[Locale.getDefault().language].asString
                        }
                    }
                }
            }
            return getString(R.string.search_item_detail_open_hours_missing)
        }

    /* ------------------------------------------------------------------------------------------ */
    /* Other helpers */

    /**
     * Helper class to process [ProximiioMapbox.routeCalculate] result.
     */
    inner class FeatureDistanceCalculationCallback(private val textView: TextView): RouteCallback {
        override fun routeEvent(eventType: RouteUpdateType, text: String, additionalText: String?, data: RouteUpdateData?) {}
        override fun onRoute(route: Route?) {
            textView.text = if (route == null) {
                getString(R.string.search_item_detail_calculating_steps_error)
            } else {
                val distanceInMeters = route.nodeList.fold(0.0) { acc, node -> acc + node.distanceFromLastNode }
                UnitHelper.getDistanceInPreferenceUnit(distanceInMeters, baseContext)
            }
            stepsTextView.text = textView.text
        }
    }
}
