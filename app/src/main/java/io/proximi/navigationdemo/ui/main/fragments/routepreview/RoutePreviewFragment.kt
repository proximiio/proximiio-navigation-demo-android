package io.proximi.navigationdemo.ui.main.fragments.routepreview


import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.constraintlayout.widget.Group
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import io.proximi.mapbox.data.model.Feature
import io.proximi.mapbox.library.Route
import io.proximi.mapbox.library.RouteConfiguration
import io.proximi.navigationdemo.R
import io.proximi.navigationdemo.ui.main.MainActivity
import io.proximi.navigationdemo.ui.main.MainActivityViewModel
import io.proximi.navigationdemo.utils.RouteConfigurationHelper
import io.proximi.navigationdemo.utils.UnitHelper
import kotlinx.android.synthetic.main.fragment_route_preview.*
import kotlin.math.min


/**
 * [Fragment] to display information for 'previewed' route.
 * Use the [RoutePreviewFragment.newInstance] factory method to create an instance of this fragment.
 */
class RoutePreviewFragment : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance() = RoutePreviewFragment()
    }

    private var activity: MainActivity? = null
    private lateinit var adapter: RouteStepsAdapter
    private lateinit var viewModel: MainActivityViewModel

    /**
     * Initiates viewmodel (from [MainActivity]) and sets up livedata observers.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = activity!!.viewModel
        viewModel.routeLiveData.observe(this, Observer { routeUpdated(it) })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_route_preview, container, false)
        view.viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
        return view
    }

    /**
     * Initial UI set up after base view was created.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startRouteButton.setOnClickListener { startRoute() }
        moreButton.setOnClickListener { toggleDirections() }
        cancelRouteButton.setOnClickListener { activity?.routeCancel() }
        addWaypoint.setOnClickListener {showSelectWaypointsDialog() }
        removeWayPoint1.setOnClickListener { removeWaypointAtIndex(0) }
        removeWayPoint2.setOnClickListener { removeWaypointAtIndex(1) }
        removeWayPoint3.setOnClickListener { removeWaypointAtIndex(2) }
        adapter = RouteStepsAdapter()
        directionsRecyclerView.adapter = adapter
    }

    /**
     * Enforce it is attached only to supported activity. Obtain activity reference.
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context !is MainActivity) error("Can only be attached to MainActivity!")
        activity = context
    }

    override fun onDestroy() {
        view?.viewTreeObserver?.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        super.onDestroy()
    }

    override fun onDetach() {
        activity = null
        super.onDetach()
    }

    private fun addWaypoint(newWaypoint: RouteConfiguration.Waypoint) {
        // Reset UI to calculating
        routeUpdated(null)
        // Copy old and add new waypoint
        val waypoints = mutableListOf<RouteConfiguration.Waypoint>()
        viewModel.routeLiveData.value?.configuration?.waypointList?.let { waypoints.addAll(it) }
        waypoints.add(newWaypoint)
        viewModel.routeFind(viewModel.routeLiveData.value!!.destinationId!!, waypoints)
    }

    private fun removeWaypointAtIndex(filteredIndex: Int) {
        viewModel.routeLiveData.value?.let { route ->
            // Reset UI to calculating
            routeUpdated(null)
            // Calculate new route
            viewModel.routeLiveData.value?.configuration?.let {
                val waypointList = it.waypointList.filterIndexed { index, _ -> index != filteredIndex  }
                viewModel.routeFind(it.destination.id()!!, waypointList)
                onWaypointUpdate(waypointList)
            }
        }
    }

    /**
     * Called on route livedata observe.
     */
    private fun routeUpdated(route: Route?) {
        adapter.updateRoute(route)
        if (route != null) {
            onWaypointUpdate(route.configuration.waypointList)
            val distanceInMeters = route.nodeList.fold(0.0) { acc, node -> acc + node.distanceFromLastNode }
            destinationTextView.text = route.destinationTitle
            stepsTextView.text = UnitHelper.getDistanceInPreferenceUnit(
                distanceInMeters,
                requireContext()
            )
            timeTextView.text = UnitHelper.distanceToTimeString(distanceInMeters, resources)
        } else {
            stepsTextView.text = getString(R.string.route_preview_calculating)
            timeTextView.text = getString(R.string.route_preview_calculating)
        }
    }

    private fun onWaypointUpdate(waypointList: List<RouteConfiguration.Waypoint>) {
        // Set default state if no waypoints are set
        addWaypoint.visibility = View.VISIBLE
        waypoint1ViewGroup.visibility = View.GONE
        waypoint2ViewGroup.visibility = View.GONE
        waypoint3ViewGroup.visibility = View.GONE
        // Show waypoints
        if (waypointList.size > 0) {
            handleWaypointViewGroup(waypoint1ViewGroup, waypoint1TextView, waypointList[0])
        }
        if (waypointList.size > 1) {
            handleWaypointViewGroup(waypoint2ViewGroup, waypoint2TextView, waypointList[1])
        }
        if (waypointList.size > 2) {
            handleWaypointViewGroup(waypoint3ViewGroup, waypoint3TextView, waypointList[2])
            addWaypoint.visibility = View.GONE
        }
    }

    private fun handleWaypointViewGroup(group: Group, textView: TextView, waypoint: RouteConfiguration.Waypoint?) {
        if (waypoint != null) {
            group.visibility = View.VISIBLE
            val features = if (waypoint is RouteConfiguration.VariableWaypoint) {
                (waypoint as RouteConfiguration.VariableWaypoint).features
            } else {
                listOf((waypoint as RouteConfiguration.SimpleWaypoint).feature)
            }
            textView.text = features.map { it.getTitle() }.joinToString(separator = ", ")
        } else {
            group.visibility = View.GONE
        }
    }

    /**
     * Start navigation for found route.
     */
    private fun startRoute() {
        viewModel.routeStart()
    }

    private fun showSelectWaypointsDialog() {
        WaypointDialogFragment.getInstance(viewModel, object: WaypointDialogFragment.Callback {
            override fun onWaypointsSelected(featureList: List<Feature>) {
                if (featureList.isEmpty()) {
                    return
                }
                val newWaypoint = if (featureList.size == 1) {
                    RouteConfiguration.SimpleWaypoint(featureList[0])
                } else {
                    RouteConfiguration.VariableWaypoint(featureList)
                }
                addWaypoint(newWaypoint)
            }
        }).show(childFragmentManager, null)
    }

    /**
     * Toggle visibility of trip steps preview.
     */
    private fun toggleDirections() {
        if (directionsView.visibility != View.VISIBLE) {
            directionsView.visibility = View.VISIBLE

            fromImageView.visibility = View.GONE
            fromTextView.visibility = View.GONE
            currentLocationTextView.visibility = View.GONE
            toImageView.visibility = View.GONE
            toTextView.visibility = View.GONE
            destinationTextView.visibility = View.GONE
            waypoint1ViewGroup.visibility = View.GONE
            waypoint1ViewGroup.visibility = View.GONE
            waypoint2ViewGroup.visibility = View.GONE
            waypoint3ViewGroup.visibility = View.GONE
            addWaypoint.visibility = View.GONE
            moreButton.text = getString(R.string.route_preview_steps_less)
        } else {
            directionsView.visibility = View.GONE
            fromImageView.visibility = View.VISIBLE
            fromTextView.visibility = View.VISIBLE
            currentLocationTextView.visibility = View.VISIBLE
            toImageView.visibility = View.VISIBLE
            toTextView.visibility = View.VISIBLE
            destinationTextView.visibility = View.VISIBLE
            viewModel.routeLiveData.value?.configuration?.let { onWaypointUpdate(it.waypointList) }
            moreButton.text = getString(R.string.route_preview_steps_more)
        }
        updateActivityOffset()
    }

    /**
     * Listener for global layout changes in order to update [MainActivity]'s UI items that should float above
     * this fragment.
     */
    private val onGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        Log.d("Route preview", "global layout")
        updateActivityOffset()
    }

    /**
     * Move activity's UI elements bound to bottom of the screen to prevent hiding them behind fragment.
     */
    private fun updateActivityOffset() {
        if (activity != null && view != null) {
            activity!!.setBottomOffset(
                this,
                view!!.height - min(summaryView.top, summaryView.top),
                true
            )
        }
    }

}
