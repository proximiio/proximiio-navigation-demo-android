package io.proximi.navigationdemo.ui.main.fragments.search

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mapbox.geojson.Point
import io.proximi.navigationdemo.*
import io.proximi.navigationdemo.ui.main.MainActivityViewModel
import io.proximi.mapbox.library.RouteCallback
import io.proximi.mapbox.library.RouteUpdateData
import io.proximi.mapbox.library.RouteUpdateType
import io.proximi.mapbox.library.Route
import com.squareup.picasso.Picasso
import io.proximi.navigationdemo.utils.*
import io.proximi.mapbox.data.model.Feature
import kotlinx.android.synthetic.main.fragment_search_item.view.*
import kotlinx.android.synthetic.main.fragment_search_item_count.view.*
import kotlinx.android.synthetic.main.fragment_search_nearby.view.*
import java.lang.RuntimeException
import kotlin.math.min

/** Item with 'Explore nearby categories' recyclerview */
private const val TYPE_NEARBY = 0
/** Item with search results counter. */
private const val TYPE_COUNT = 1
/** Item showing a POI feature */
private const val TYPE_ITEM = 2
/** Item displayed to mark end of POI list. */
private const val TYPE_END = 3

/**
 *Custom recycler view adapter that manages a list of POI features.
 */
class SearchItemAdapter(
    /** Reference to search fragment this recycler is intended to be used in. */
    private val searchFragment: SearchFragment,
    /** Number of columns device's can fit. */
    private val spanCount: Int
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** List of features. */
    var featureList: List<Feature> = listOf()
        set(value) { field = value; notifyDataSetChanged(); }
    /** View Model reference we need to calculate routes to features. */
    var viewModel: MainActivityViewModel? = null
        set(value) { field = value; notifyDataSetChanged(); }
    /** Flag whether POI features should be shown or hidden. */
    var showPOIs: Boolean = false
        set(value) { field = value; notifyDataSetChanged(); }
    /** Flag whether nearby categories should be shown or hidden. */
    var showNearby: Boolean = true
        set(value) { field = value; notifyDataSetChanged(); }

    /** Adapter for explore nearby category recycler */
    private var exploreNearbyAdapter = ExploreNearbyAdapter(searchFragment)
    /** Map with cached values of calculated distances to POI features. */
    private var distanceStringCache = mutableMapOf<String, String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_NEARBY -> createNearbyHolder(parent)
            TYPE_ITEM   -> createItemHolder(parent)
            TYPE_COUNT  -> createCountHolder(parent)
            else        -> createEndHolder(parent)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ItemViewHolder) holder.cleanUp()
        if (holder is NearbyViewHolder) holder.cleanUp()
    }

    override fun getItemViewType(position: Int): Int {
        return if (showNearby && position == 0) TYPE_NEARBY
        else if (showNearby && position == 1) TYPE_COUNT
        else if (!showNearby && position == 0) TYPE_COUNT
        else if (showNearby && position < featureList.size + 2) TYPE_ITEM
        else if (position < featureList.size + 1) TYPE_ITEM
        else TYPE_END
    }

    override fun getItemCount(): Int {
        return (if (showNearby) 1 else 0) + (if (showPOIs) featureList.size + 2 else 0)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val position = (if (showNearby) position - 1 else position) - 1
        when (holder) {
            is ItemViewHolder   -> holder.setData(featureList[position])
            is NearbyViewHolder -> holder.setup()
            is CountViewHolder  -> holder.load(featureList.size)
        }
    }

    fun getSpanSize(position: Int): Int {
        // POI feature items should not span, rest of them should take full screen width.
        return if (getItemViewType(position) != TYPE_ITEM) {
            spanCount
        } else {
            1
        }
    }

    /**
     * Clears / resets cached POI feature distances.
     */
    fun resetPoiDistance() {
        distanceStringCache.clear()
        val start: Int
        val end: Int
        if (showNearby) {
            start = 1
            end = featureList.size + 1
        } else {
            start = 0
            end = featureList.size
        }
        notifyItemRangeChanged(start, end)
    }

    /* ------------------------------------------------------------------------------------------ */
    /* ItemViewHolder for POI feature */

    fun createItemHolder(parent: ViewGroup): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_search_item, parent, false)
        return ItemViewHolder(view)
    }

    inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        lateinit var feature: Feature
        private var picasso: Picasso? = null

        fun setData(feature: Feature) {
            this.feature = feature
            itemView.itemNameTextView.text = feature.getTitle() ?: "[Unknown]"

            val levelId = when (feature.getLevel()) {
                0 -> R.string.search_item_level_0
                1 -> R.string.search_item_level_1
                2 -> R.string.search_item_level_2
                3 -> R.string.search_item_level_3
                4 -> R.string.search_item_level_4
                5 -> R.string.search_item_level_5
                else -> R.string.search_item_level_0
            }
            itemView.itemFloorTextView.text = itemView.context.getString(levelId)

            // Image
            picasso?.cancelRequest(itemView.itemImageView)
            picasso = Picasso.get()
            val poiImages = feature.imageUrlList
                picasso!!.load(if (poiImages.isNotEmpty()) poiImages[0] else null)
                    .error(R.drawable.ic_broken_image)
                    .into(itemView.itemImageView)

            // Color
            itemView.itemNameTextView.setBackgroundColor(feature.titleBackgroundColor)

            // Distance text
            if (distanceStringCache.containsKey(this.feature.id)) {
                itemView.itemDistanceTextView.text = distanceStringCache[this.feature.id]
            } else {
                itemView.itemDistanceTextView.text = itemView.context.getString(R.string.search_item_distance_estimating)
                try {
                    viewModel?.routeCalculate((feature.featureGeometry as Point).toLocation(), feature.getLevel() ?: 0, feature.getTitle() ?: "",
                        RouteOptionsHelper.create(itemView.context), object: RouteCallback {
                        override fun routeEvent(eventType: RouteUpdateType, text: String, additionalText: String?, data: RouteUpdateData?) {}
                        override fun onRoute(route: Route?) {
                            val text = if (route == null) {
                                itemView.context.getString(R.string.search_item_distance_unknown)
                            } else {
                                val distanceInMeters = route.nodeList.fold(0.0) { acc, node -> acc + node.distanceFromLastNode }
                                UnitHelper.getDistanceInPreferenceUnit(distanceInMeters, itemView.context)
                            }
                            distanceStringCache[feature.id] = text
                            if(this@ItemViewHolder.feature.id == feature.id) {
                                itemView.itemDistanceTextView.text = text
                            }
                        }
                    })
                } catch (e: RuntimeException) {
                    Log.d(SearchItemAdapter::class.java.simpleName, "unable to calculate route")
                    itemView.itemDistanceTextView.text = itemView.context.getString(R.string.search_item_distance_unknown)
                }
            }
            itemView.setOnClickListener { searchFragment.openDetail(feature) }
        }
        fun cleanUp() {
            picasso?.cancelRequest(itemView.itemImageView)
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* NearbyViewHolder */

    private fun createNearbyHolder(parent: ViewGroup): NearbyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_search_nearby, parent, false)
        return NearbyViewHolder(view)
    }

    inner class NearbyViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {

        fun setup() {
            itemView.exploreNearbyRecyclerView.isNestedScrollingEnabled = false
            itemView.exploreNearbyRecyclerView.adapter = exploreNearbyAdapter
            itemView.exploreNearbyRecyclerView.layoutManager = GridLayoutManager(itemView.context, getSpanCount())
            itemView.addOnLayoutChangeListener(layoutChangeListener)
        }

        fun cleanUp() {
            itemView.removeOnLayoutChangeListener(layoutChangeListener)
        }

        private val layoutChangeListener = View.OnLayoutChangeListener { view: View, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int ->
            searchFragment.setHalfExpandedHeight(view.height)
        }

        private fun getSpanCount(): Int {
            val displayMetrics = itemView.context.resources.displayMetrics
            val fittableSpanCount = min(6, (displayMetrics.widthPixels / itemView.context.resources.getDimension(
                R.dimen.nearby_item_min_width
            )).toInt())
            return when {
                fittableSpanCount >= 6 -> 6
                fittableSpanCount >= 3 -> 3
                else                   -> fittableSpanCount
            }
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* EndViewHolder */

    fun createEndHolder(parent: ViewGroup): EndViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_search_item_end, parent, false)
        return EndViewHolder(view)
    }

    inner class EndViewHolder(itemView: View): RecyclerView.ViewHolder(itemView)

    /* ------------------------------------------------------------------------------------------ */
    /* CountViewHolder */

    fun createCountHolder(parent: ViewGroup): CountViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_search_item_count, parent, false)
        return CountViewHolder(view)
    }

    inner class CountViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        fun load(itemCount: Int) {
            itemView.countTextView.text = if (itemCount == 0) itemView.context.getString(
                R.string.search_item_count_zero
            )
                else itemView.context.resources.getQuantityString(R.plurals.search_item_count, itemCount, itemCount)
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Helper methods */

    private val Feature.titleBackgroundColor: Int
        get() {
            val nearbyCategory = EXPLORE_NEARBY_CATEGORIES.firstOrNull { category -> amenity != null && category.amenityCategoryId == amenity!!.categoryId }
            return searchFragment.requireContext().getColorFromAttr(if (nearbyCategory == null) {
                R.attr.exploreNearbyDefaultColor
            } else {
                nearbyCategory.colorAttrId!!
            })
        }


}