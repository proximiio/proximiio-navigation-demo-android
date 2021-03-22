package io.proximi.navigationdemo.ui.main.fragments.routepreview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.mapbox.geojson.Point
import io.proximi.mapbox.data.model.Feature
import io.proximi.mapbox.library.ProximiioFeatureType
import io.proximi.navigationdemo.R
import io.proximi.navigationdemo.ui.main.MainActivityViewModel
import io.proximi.navigationdemo.utils.SearchFilter
import io.proximi.proximiiolibrary.ProximiioFloor
import kotlinx.android.synthetic.main.fragment_dialog_waypoint.*
import kotlinx.android.synthetic.main.fragment_dialog_waypoint.view.*
import kotlinx.android.synthetic.main.fragment_dialog_waypoint.view.acceptButton
import kotlinx.android.synthetic.main.fragment_help_dialog.view.*
import kotlinx.android.synthetic.main.fragment_map_demo_search_item.*
import kotlinx.android.synthetic.main.fragment_map_demo_search_item.view.*
import kotlin.math.max
import kotlin.math.roundToInt

class WaypointDialogFragment(
    private val viewModel: MainActivityViewModel,
    private val callback: Callback
): DialogFragment() {

    private var featureAdapter = FeatureAdapter()
    private var selectedFeatureList = mutableListOf<Feature>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view =  inflater.inflate(R.layout.fragment_dialog_waypoint, container, false)
        view.searchEditText.addTextChangedListener(featureAdapter.textWatcher)
        view.featureRecyclerView.adapter = featureAdapter
        view.featureRecyclerView.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        view.acceptButton.setOnClickListener {
            callback.onWaypointsSelected(selectedFeatureList)
            dismiss()
        }
        view.cancelButton.setOnClickListener {
            dismiss()
        }
        dialog?.window?.let {
            it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            it.setGravity(Gravity.CENTER)
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.requestFeature(Window.FEATURE_NO_TITLE)
        }
        observeFeatures()
        observePlace()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let {
            it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            it.setGravity(Gravity.CENTER)
        }
    }

    private fun observeFeatures() {
        viewModel.poisLiveData.observe(viewLifecycleOwner, Observer {
            updateFeatures()
        })
    }

    private fun observePlace() {
        viewModel.userPlaceLiveData.observe(viewLifecycleOwner, Observer {
            updateFeatures()
        })
    }

    private fun updateFeatures() {
        val filter = SearchFilter()
        filter.locationPoint = viewModel.userPlaceLiveData.value?.let { Point.fromLngLat(it.lon, it.lat) }
        featureAdapter.featureList = viewModel.featuresLiveData.value!!
            .filter { filter.filterItem(it, null, null) && it.getTitle() != null }
            .sortedBy { it.getTitle() }
    }

    private inner class FeatureAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_ITEM = 0
        private val TYPE_PLACEHOLDER = 1

        var floorList = listOf<ProximiioFloor>()
            set(value) { field = value; updateFilteredList() }

        var featureList = listOf<Feature>()
            set(value) { field = value; updateFilteredList() }

        val textWatcher = object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter = s?.toString() ?: ""
            }
        }

        private var filter: String = ""
            set(value) { if (field != value) { field = value; updateFilteredList() } }

        private var filteredList = listOf<Feature>()
            set(value) { field = value; notifyDataSetChanged() }


        private fun updateFilteredList() {
            filteredList = featureList.filter { it.getTitle()?.contains(filter, true) == true }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                TYPE_ITEM -> FeatureVH(LayoutInflater.from(parent.context).inflate(R.layout.fragment_map_demo_search_item, parent, false))
                else      -> PlaceholderVH(LayoutInflater.from(parent.context).inflate(R.layout.fragment_map_demo_search_item_empty, parent, false))
            }
        }

        override fun getItemCount(): Int {
            return max(filteredList.size, 1)
        }

        override fun getItemViewType(position: Int): Int {
            return if (filteredList.isNotEmpty()) TYPE_ITEM else TYPE_PLACEHOLDER
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is FeatureVH) holder.load(filteredList[position], floorList)
        }
    }

    private val amenityImageMap = mutableMapOf<String, Bitmap?>()

    private inner class PlaceholderVH(itemView: View): RecyclerView.ViewHolder(itemView)

    private inner class FeatureVH(itemView: View): RecyclerView.ViewHolder(itemView) {
        fun load(feature: Feature, floorList: List<ProximiioFloor>) {

            itemView.checkBox.isChecked = selectedFeatureList.firstOrNull { it.id == feature.id } != null
            val image = feature.amenity?.icon?.let { icon ->
                if (!amenityImageMap.containsKey(feature.amenityId)) {
                    val base64ImageData = icon.split(",".toRegex()).toTypedArray()[1]
                    val decodedString = Base64.decode(base64ImageData, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                    amenityImageMap.put(feature.amenityId!!, bitmap)
                }
                amenityImageMap[feature.amenityId]
            }

            val floor = floorList.firstOrNull { it.id == feature.getFloorId() }
            val place = floor?.place
            val locationString = if (floor != null) {
                StringBuilder().apply {
                    append(floor.name)
                    if (place != null) {
                        append(" - ")
                        append(place.name ?: "")
                    }
                }.toString()
            } else {
                val userPosition = viewModel.userLocationLiveData.value
                val distance = if (userPosition != null) {
                    feature.pointToPointDistance(Point.fromLngLat(userPosition.longitude, userPosition.latitude)) ?: Double.POSITIVE_INFINITY
                } else {
                    Double.POSITIVE_INFINITY
                }
                itemView.context.getString(R.string.featureBackupDescription, feature.getLevel() ?: 0, distance.roundToInt())
            }

            itemView.setOnClickListener {
                if (itemView.checkBox.isChecked) {
                    itemView.checkBox.isChecked = false
                    selectedFeatureList.removeIf { it.id == feature.id }
                } else {
                    itemView.checkBox.isChecked = true
                    selectedFeatureList.add(feature)
                }
            }
            itemView.checkBox.setOnClickListener {
                if (itemView.checkBox.isChecked) {
                    selectedFeatureList.add(feature)
                } else {
                    selectedFeatureList.removeIf { it.id == feature.id }
                }
            }
            itemView.nameTextView.text = feature.getTitle()
            itemView.locationTextView.text = locationString
            itemView.locationTextView.isSelected = true
            if (image != null) {
                itemView.imageView.setImageBitmap(image)
            } else {
                itemView.imageView.setImageDrawable(null)
            }
        }
    }

    interface Callback {
        fun onWaypointsSelected(featureList: List<Feature>)
    }

    companion object {
        fun getInstance(mainActivityViewModel: MainActivityViewModel, callback: Callback) = WaypointDialogFragment(mainActivityViewModel, callback)
    }
}