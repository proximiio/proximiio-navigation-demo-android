package io.proximi.navigationdemo.ui.main.fragments.navigation

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.lifecycle.Observer
import io.proximi.navigationdemo.R
import io.proximi.navigationdemo.utils.UnitHelper
import io.proximi.navigationdemo.utils.getDrawable
import io.proximi.navigationdemo.ui.main.MainActivity
import io.proximi.navigationdemo.ui.main.MainActivityViewModel
import io.proximi.mapbox.data.model.Feature
import io.proximi.mapbox.library.RouteUpdateData
import io.proximi.mapbox.library.RouteUpdateType
import kotlinx.android.synthetic.main.fragment_navigation.*

/**
 * [Fragment] that displays current route navigation information.
 *
 * Use the [NavigationFragment.newInstance] factory method to create an instance of this fragment.
 */
class NavigationFragment : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance() = NavigationFragment()
    }

    private var activity: MainActivity? = null
    private lateinit var viewModel: MainActivityViewModel

    /**
     * Initiates viewmodel (from [MainActivity]) and sets up livedata observers.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = activity!!.viewModel
        viewModel.routeEventLiveData.observe(this@NavigationFragment, Observer { it?.let { routeEvent(it.eventType, it.text, it.additionalText, it.data)} })
        viewModel.currentHazardFeatureLiveData.observe(this@NavigationFragment, Observer { hazardUpdated(it) })
        viewModel.currentSegmentFeatureLiveData.observe(this@NavigationFragment, Observer { segmentUpdated(it) })
    }

    /**
     * Enforce it is attached only to supported activity. Obtain activity reference.
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context !is MainActivity) error("Can be only attached to MainActivity")
        activity = context
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_navigation, container, false)
        view.viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigationCancelButton.setOnClickListener { (activity?.routeCancel()) }
    }

    override fun onDestroyView() {
        view?.viewTreeObserver?.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        super.onDestroyView()
    }

    override fun onDetach() {
        activity = null
        super.onDetach()
    }

    /**
     * Update UI with current hazard feature information.
     */
    fun hazardUpdated(hazardFeature: Feature?) {
        if (hazardFeature != null) {
            navigationHazardTextView.visibility = View.VISIBLE
            navigationHazardTextView.text = getString(R.string.hazard_warning, hazardFeature.getTitle())
        } else {
            navigationHazardTextView.visibility = View.GONE
        }
    }

    /**
     * Update UI with current segment feature information.
     */
    fun segmentUpdated(segmentFeature: Feature?) {
        if (segmentFeature != null) {
            navigationSegmentTextView.visibility = View.VISIBLE
            navigationSegmentTextView.text = getString(R.string.navigation_segment, segmentFeature.getTitle())
        } else {
            navigationSegmentTextView.visibility = View.GONE
        }
    }

    /**
     * Update UI with current navigation / guidance information.
     */
    fun routeEvent(eventType: RouteUpdateType, text: String, additionalText: String?, data: RouteUpdateData?) {
        when(eventType) {
            RouteUpdateType.CALCULATING         -> navigationBasicStatus(text)
            RouteUpdateType.RECALCULATING       -> navigationBasicStatus(text)
            RouteUpdateType.ROUTE_NOT_FOUND     -> navigationRouteEnd(eventType, text)
            RouteUpdateType.FINISHED            -> navigationRouteEnd(eventType, text)
            RouteUpdateType.CANCELED            -> navigationRouteEnd(eventType, text)
            RouteUpdateType.DIRECTION_IMMEDIATE -> navigationImmediateUpdate(text, data!!)
            RouteUpdateType.DIRECTION_SOON      -> navigationImmediateUpdate(text, data!!)
            else                                -> navigationUpdate(text, data!!)
        }
    }

    /**
     * Show status type navigation information.
     */
    private fun navigationBasicStatus(text: String) {
        navigationProgressBar.visibility = View.VISIBLE
        navigationHeadingBar.visibility = View.GONE
        navigationStepsLeftImageView.visibility = View.GONE
        navigationNextStepImageView.visibility = View.GONE
        navigationCurrentTextView.visibility = View.VISIBLE
        navigationCurrentTextView.text = text
    }

    /**
     * Update UI with generic navigation update information about next step.
     */
    private fun navigationUpdate(text: String, data: RouteUpdateData) {
        navigationProgressBar.visibility = View.GONE
        navigationHeadingBar.visibility = View.VISIBLE
        navigationStepsLeftImageView.visibility = View.VISIBLE
        navigationNextStepImageView.visibility = View.VISIBLE
        navigationCurrentTextView.visibility = View.VISIBLE
        val distanceLeft = UnitHelper.getDistanceLeftInPreferenceUnit(
                data.stepDistance,
                requireContext()
            )
        navigationCurrentTextView.text = getString(R.string.navigation_label_then, distanceLeft)
        navigationNextStepImageView.setImageResource(if (data.nextStepDirection == null) R.drawable.ic_destination else data.nextStepDirection!!.getDrawable())
        navigationUpdateDirectionData(data.stepBearing)
    }

    /**
     * Updates UI with compass-heading with current route bearing.
     */
    private fun navigationUpdateDirectionData(bearing: Double) {
        var stringId: Int
        var rotation: Float
        when {
            ( -22.5..  22.5).contains(bearing) -> { stringId = R.string.cardinal_point_n; rotation = 0f }
            (  22.5..  67.5).contains(bearing) -> { stringId = R.string.cardinal_point_ne; rotation = 45f }
            (  67.5.. 112.5).contains(bearing) -> { stringId = R.string.cardinal_point_e; rotation = 90f }
            ( 112.5.. 157.5).contains(bearing) -> { stringId = R.string.cardinal_point_se; rotation = 135f }
            ( -67.5.. -22.5).contains(bearing) -> { stringId = R.string.cardinal_point_nw; rotation = -45f }
            (-112.5.. -67.5).contains(bearing) -> { stringId = R.string.cardinal_point_w; rotation = -90f }
            (-157.5..-112.5).contains(bearing) -> { stringId = R.string.cardinal_point_sw; rotation = -135f }
            else -> { stringId = R.string.cardinal_point_s; rotation = 180f }
        }
        navigationHeadingTextView.text = getString(R.string.cardinal_point_head, getString(stringId))
        navigationHeadingImageView.setImageResource(R.drawable.ic_turn_straight)
        navigationHeadingImageView.rotation = rotation
    }

    /**
     * Display UI for route ended event.
     */
    private fun navigationRouteEnd(type: RouteUpdateType, text: String) {
        navigationProgressBar.visibility = View.GONE
        navigationHeadingBar.visibility = View.GONE
        navigationStepsLeftImageView.visibility = View.VISIBLE
        navigationNextStepImageView.visibility = View.VISIBLE
        navigationCurrentTextView.visibility = View.VISIBLE
        navigationNextStepImageView.setImageResource(if (type == RouteUpdateType.FINISHED) R.drawable.ic_destination else R.drawable.ic_cancel)
        navigationCurrentTextView.text = text
    }

    /**
     * Show important upcoming route event information.
     */
    private fun navigationImmediateUpdate(text: String, data: RouteUpdateData) {
        navigationProgressBar.visibility = View.GONE
        navigationHeadingBar.visibility = View.VISIBLE
        navigationStepsLeftImageView.visibility = View.VISIBLE
        navigationNextStepImageView.visibility = View.VISIBLE
        navigationCurrentTextView.visibility = View.VISIBLE
        navigationCurrentTextView.text = getString(R.string.navigation_label_then, text)
        navigationNextStepImageView.setImageResource(data.stepDirection.getDrawable())
        navigationUpdateDirectionData(data.stepBearing)
    }

    /**
     * Listener for global layout changes in order to update [MainActivity]'s UI items that should float above
     * this fragment.
     */
    private val onGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        Log.d("NavigationFragment", "global layout")
        updateActivityBottomOffset()
    }

    /**
     * Move activity's UI elements bound to bottom of the screen to prevent hiding them behind fragment.
     */
    private fun updateActivityBottomOffset() {
        if (activity != null && view != null) {
            activity!!.setBottomOffset(view!!.height - navigationTopView.top, true)
        }
    }
}
