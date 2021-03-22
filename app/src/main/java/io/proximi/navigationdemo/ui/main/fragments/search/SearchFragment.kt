package io.proximi.navigationdemo.ui.main.fragments.search


import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mapbox.geojson.Point
import io.proximi.navigationdemo.*
import io.proximi.navigationdemo.ui.SettingsActivity
import io.proximi.navigationdemo.ui.searchitem.SearchItemDetailActivity
import io.proximi.navigationdemo.ui.main.MainActivity
import io.proximi.navigationdemo.ui.main.MainActivityViewModel
import io.proximi.navigationdemo.utils.SearchFilter
import io.proximi.navigationdemo.utils.TwoStageBottomSheetBehavior
import io.proximi.mapbox.data.model.Feature
import io.proximi.mapbox.data.model.Amenity
import io.proximi.proximiiolibrary.ProximiioPlace
import kotlinx.android.synthetic.main.fragment_search.*
import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


/**
 * A simple [Fragment] subclass.
 * Use the [SearchFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SearchFragment : Fragment(), CoroutineScope {

    companion object {
        @JvmStatic
        fun newInstance() = SearchFragment()
    }

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private var activity: MainActivity? = null
    private var searchFilter = SearchFilter()
    private lateinit var adapter: SearchItemAdapter
    private lateinit var viewModel: MainActivityViewModel
    private lateinit var bottomSheetBehavior: TwoStageBottomSheetBehavior<*>
    private val DETAIL_CODE = 932
    private val VOICE_CODE = 10

    var exploreNearbyItem: ExploreNearbyItem? = null
        set(value) { field = value; nearbyItemUpdated() }

    /* ------------------------------------------------------------------------------------------ */
    /* Lifecycle */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = activity!!.viewModel
        setupObservers()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context !is MainActivity) error("Must be attached to MainActivity!")
        activity = context
    }

    override fun onDetach() {
        activity = null
        super.onDetach()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity()
        val spanCount = getSpanCount()
        adapter = SearchItemAdapter(this, spanCount)
        adapter.viewModel = viewModel
        searchRecyclerView.adapter = adapter
        searchRecyclerView.isNestedScrollingEnabled = false
        (searchRecyclerView.layoutManager as GridLayoutManager).spanCount = spanCount
        (searchRecyclerView.layoutManager as GridLayoutManager).spanSizeLookup = object: GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return adapter.getSpanSize(position)
            }
        }
        searchRecyclerView.isNestedScrollingEnabled = true

        // Bottom sheet callback
        bottomSheet.background.alpha = 0
        bottomSheetBehavior = TwoStageBottomSheetBehavior.from(bottomSheet as View)
        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback)
        bottomSheetBehavior.isFitToContents = false

        // Setup explore nearby bottom sheet recycler
        searchEditText.addTextChangedListener(searchEditTextWatcher)
        searchEditText.setOnFocusChangeListener { _, hasFocus -> searchEditTextFocusChanged(hasFocus) }
        voiceInputButtonRight.setOnClickListener { startVoiceInput() }
        voiceInputButtonLeft.setOnClickListener { startVoiceInput() }
        if (PreferenceManager.getDefaultSharedPreferences(requireContext()).getString(SettingsActivity.ACCESSIBILITY_HAND_MODE, null) == "left") {
            voiceInputButtonLeft.visibility = View.VISIBLE
            voiceInputButtonRight.visibility = View.GONE
        } else {
            voiceInputButtonLeft.visibility = View.GONE
            voiceInputButtonRight.visibility = View.VISIBLE
        }
        nearbyItemFilterChipView.setOnClickListener { exploreNearbyItem = null }
        nearbyItemFilterChipView.setOnCloseIconClickListener { exploreNearbyItem = null }
    }

    override fun onStart() {
        super.onStart()
        view?.viewTreeObserver?.addOnGlobalLayoutListener(onGlobalLayoutListener)
    }

    override fun onStop() {
        view?.viewTreeObserver?.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        super.onStop()
    }

    override fun onDestroyView() {
        bottomSheetBehavior.removeBottomSheetCallback(bottomSheetCallback)
        super.onDestroyView()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && requestCode == DETAIL_CODE) {
            bottomSheetBehavior.state = TwoStageBottomSheetBehavior.STATE_COLLAPSED
            Log.d("NAVIGATION_LOOP", "MainActivity.onActivityResult")
            activity?.startNavigation(data!!)
        }
        if (resultCode == Activity.RESULT_OK && requestCode == VOICE_CODE && data != null) {
            val strings = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (strings != null && strings.size > 0) {
                searchEditText.setText(strings[0])
            }
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Data observers */

    private fun setupObservers() {
        viewModel.userPlaceLiveData.observe(this, userPlaceObserver)
        viewModel.poisLiveData.observe(this, Observer<List<Feature>> { updateSearchItems() })
    }

    private val userPlaceObserver = Observer<ProximiioPlace?> {
        searchFilter.locationPoint = if (it != null) {
            Point.fromLngLat(it.getLon(), it.getLat())
        } else {
            null
        }
        updateSearchItems()
        adapter.resetPoiDistance()
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Bottom sheet behaviour */

    private val bottomSheetCallback = object: TwoStageBottomSheetBehavior.BottomSheetCallback {
        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            if (view != null) {
                val opacity = ((max(slideOffset - 0.9f, 0.0f) * 10f) * 255).toInt()
                bottomSheet.background.alpha = opacity
                searchBackgroundLayout.background.alpha = (255 - opacity)
                updateActivityOffset(bottomSheet)
                val halfHeight = (bottomSheetBehavior.halfExpandedHeight?:0).toFloat()
                val peekHeight = bottomSheetBehavior.peekHeight.toFloat()
                val parentHeight = (bottomSheet.parent as View).height.toFloat()
                val arrowTargetOffset = (halfHeight - peekHeight) / (parentHeight - peekHeight)
                val arrowOpacity = 1f - min(slideOffset / arrowTargetOffset, 1.0f)
                expandMoreImageView.alpha = arrowOpacity
            }
        }

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (isRemoving || isDetached) return
            if (newState == TwoStageBottomSheetBehavior.STATE_EXPANDED) {
                adapter.resetPoiDistance()
                adapter.showPOIs = true
                adapter.showNearby = true
            } else if (newState == TwoStageBottomSheetBehavior.STATE_HALF_EXPANDED || newState == TwoStageBottomSheetBehavior.STATE_COLLAPSED) {
                exploreNearbyItem = null
                setTextFilter("")
                adapter.showPOIs = false
                adapter.showNearby = true
            }
            if (newState == TwoStageBottomSheetBehavior.STATE_COLLAPSED) {
                searchEditText.clearFocus()
            }
        }
    }

    private var oldHalfExpandedHeight = 0
    fun setHalfExpandedHeight(pxHeight: Int) {
        val moreIconHeight = expandMoreImageView.height
        val newHeight = pxHeight + moreIconHeight
        if (pxHeight != 0 && oldHalfExpandedHeight != newHeight) {
            bottomSheetBehavior.halfExpandedHeight = newHeight + searchCardViewWrapper.height
            oldHalfExpandedHeight = newHeight
        }
    }

    fun collapseBottomSheet(): Boolean {
        return if (bottomSheetBehavior.state != TwoStageBottomSheetBehavior.STATE_COLLAPSED) {
            bottomSheetBehavior.state = TwoStageBottomSheetBehavior.STATE_COLLAPSED
            true
        } else {
            false
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Speech input */

    private fun startVoiceInput() {
        bottomSheetBehavior.state = TwoStageBottomSheetBehavior.STATE_EXPANDED
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        startActivityForResult(intent, VOICE_CODE)
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Other */

    private fun nearbyItemUpdated() {
        if (isRemoving || isDetached) return
//        adapter.setNearbyFilter(exploreNearbyItem)
        updateSearchItems()
        if (exploreNearbyItem != null) {
            setTextFilter("")
            nearbyItemFilterChipView.visibility = View.VISIBLE
            nearbyItemFilterChipView.setText(exploreNearbyItem!!.nameStringId)
            bottomSheetBehavior.state = TwoStageBottomSheetBehavior.STATE_EXPANDED
        } else {
            nearbyItemFilterChipView.visibility = View.GONE
        }
    }

    private var searchJob: Job? = null
    private fun updateSearchItems() {
        val searchText = searchEditText.text?.toString()
        val exploreNearbyItem = this.exploreNearbyItem
        // Toggle explore nearby categories
        adapter.showNearby = exploreNearbyItem == null && searchText.isNullOrBlank()
        // Create search block to not stall UI thread
        searchJob?.cancel()
        searchJob = launch(Dispatchers.Default) {
            val featureList = viewModel.searchPois(searchFilter, searchText, exploreNearbyItem?.amenityCategoryId).sortedBy { it.getTitle() }
            withContext(Dispatchers.Main) {
                adapter.featureList = featureList
                searchJob = null
            }
        }
    }


    /**
     * Calculate height to offset main activity fabs by
     */
    private fun updateActivityOffset(bottomSheet: View) {
        if (activity != null && view != null) {
            activity!!.setBottomOffset(this, view!!.height - bottomSheet.top - resources.getDimension(R.dimen.fragment_search_input_margin).roundToInt())
        }
    }

    fun openDetail(feature: Feature) {
        SearchItemDetailActivity.startForResult(this, DETAIL_CODE, feature)
    }

    fun setTextFilter(filter: String?) {
        if (filter?.isNotBlank() == true) {
            bottomSheetBehavior.state = TwoStageBottomSheetBehavior.STATE_EXPANDED
        }
        searchEditText.setText(filter)
    }

    /**
     * Calculate spans (columns) for search based on screen width.
     */
    private fun getSpanCount(): Int {
        val displayMetrics = resources.displayMetrics
        return (displayMetrics.widthPixels / resources.getDimension(R.dimen.search_item_min_width)).toInt()
    }

    /**
     * Listen for layout changes and calculate height to offset main activity's fabs by from
     * search editText and nearby category item height to peak first row.
     */
    private val onGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        var nearbyHeight = 0
        if (searchRecyclerView.childCount > 0) {
            val nearbyWrapper = searchRecyclerView.getChildAt(0)
            if (nearbyWrapper is LinearLayout && nearbyWrapper.childCount >= 2) {
                val title = nearbyWrapper.getChildAt(0)
                val nearby = nearbyWrapper.getChildAt(1)
                if (nearby is RecyclerView) {
                    if (nearby.childCount > 0) {
                        nearbyHeight = title.height + nearby.getChildAt(0).height + expandMoreImageView.height
                    }
                }
            }
        }

        bottomSheetBehavior.peekHeight = searchCardViewWrapper.height + nearbyHeight
        updateActivityOffset(bottomSheet)
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Search input handling */

    private val searchEditTextWatcher = object: TextWatcher {
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        override fun afterTextChanged(p0: Editable?) {
//            adapter.setTextFilter(searchEditText.text.toString())
            updateSearchItems()
        }
    }

    private fun searchEditTextFocusChanged(hasFocus: Boolean) {
        if (hasFocus && bottomSheetBehavior.state == TwoStageBottomSheetBehavior.STATE_COLLAPSED) {
            bottomSheetBehavior.state = TwoStageBottomSheetBehavior.STATE_EXPANDED
            hideKeyboard()
        }
    }

    private fun hideKeyboard() {
        activity?.let { activity ->
            val imm: InputMethodManager = activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            view?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
        }
    }

}
