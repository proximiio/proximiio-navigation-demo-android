package io.proximi.navigationdemo.ui.main.fragments.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.proximi.navigationdemo.R
import kotlinx.android.synthetic.main.fragment_search_nearby_item.view.*

/**
 * Adapter for static list of explore nearby categories.
 */
class ExploreNearbyAdapter(private val searchFragment: SearchFragment) : RecyclerView.Adapter<ExploreNearbyAdapter.ExploreNearbyViewHolder>() {

    private var collapsed = true
        set(value) { field = value; updateList(); }
    private var currentList = listOf<ExploreNearbyItem>() // .apply { addAll(exploreNearbyItemList.filter { it.type.displayWhenCollapsed() }) }

    init {
        updateList()
    }

    private fun updateList() {
        currentList =
            if (collapsed) {
                EXPLORE_NEARBY_CATEGORIES.filter { it.type.displayWhenCollapsed() }
            } else {
                EXPLORE_NEARBY_CATEGORIES.filter { it.type.displayWhenExpanded() }
            }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExploreNearbyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_search_nearby_item, parent, false)
        return ExploreNearbyViewHolder(view)
    }

    override fun getItemCount(): Int {
        return currentList.size
    }

    override fun onBindViewHolder(holder: ExploreNearbyViewHolder, position: Int) {
        holder.loadItem(currentList[position])
    }

    inner class ExploreNearbyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun loadItem(item: ExploreNearbyItem) {
            itemView.exploreNearbyImageView.setImageResource(item.drawableId)
            itemView.exploreNearbyTextView.setText(item.nameStringId)
            itemView.setOnClickListener {
                when (item.type) {
                    ExploreNearbyItemType.MORE -> collapsed = false
                    ExploreNearbyItemType.LESS -> collapsed = true
                    else                       -> searchFragment.exploreNearbyItem = item
                }
            }
        }
    }
}

/**
 * Class representing explore nearby category item.
 */
data class ExploreNearbyItem(
    /** Icon drawable resource identifier to show in menu. */
    val drawableId: Int,
    /** Attributed color resource for color assigned for this category. */
    val colorAttrId: Int?,
    /** String resource identifier to use as name for this category. */
    val nameStringId: Int,
    /** Proximi.io amenity category identifier to match with this explore nearby category. */
    val amenityCategoryId: String?,
    /** Type of item that determines its behaviour in explore nearby list. */
    val type: ExploreNearbyItemType = ExploreNearbyItemType.ITEM
)

/**
 * Type of [ExploreNearbyItem] that determines its behaviour.
 */
enum class ExploreNearbyItemType {

    /** Basic item, should be shown even in collapsed state. */
    ITEM,
    /** Basic item, should NOT be shown even in collapsed state, only when expanded. */
    EXPANDED_ITEM,
    /** Toggle button to expand items. */
    MORE,
    /** Toggle button to collapse items. */
    LESS;

    /**
     * Helper method that returns whether the item should be shown when collapsed.
     */
    fun displayWhenCollapsed(): Boolean { return this == ITEM || this == MORE }

    /**
     * Helper method that returns whether the item should be shown when expanded.
     */
    fun displayWhenExpanded(): Boolean { return this != MORE }
}

/**
 * Static list of explore nearby categories.
 */
val EXPLORE_NEARBY_CATEGORIES = listOf(
    ExploreNearbyItem(R.drawable.explorenearby_cafeteria, R.attr.exploreNearbyServiceCafeteriaColor, R.string.explorenearby_cafeteria, "cef3a774-27e3-4df3-b4ec-a72ee15bed55"),
    ExploreNearbyItem(R.drawable.explorenearby_lift, R.attr.exploreNearbyServiceLiftColor, R.string.explorenearby_lift, "07bef616-619b-4a34-99ab-362dd4bc0075"),
    ExploreNearbyItem(R.drawable.explorenearby_washrooms, R.attr.exploreNearbyServiceWashroomsColor, R.string.explorenearby_washrooms, "16b509c5-aa6d-48f8-98bf-5a88b6c1e4fb"),
    ExploreNearbyItem(R.drawable.explorenearby_reception, R.attr.exploreNearbyServiceReceptionColor, R.string.explorenearby_reception,"0368ed37-9f49-45d4-8c30-0a4a03badf6e"),
    ExploreNearbyItem(R.drawable.explorenearby_offices, R.attr.exploreNearbyServiceOfficesColor, R.string.explorenearby_offices,"f6fd8bff-d04e-4e2a-8ea2-8980e1c9b326"),
    ExploreNearbyItem(R.drawable.explorenearby_more, null, R.string.explorenearby_more,null, ExploreNearbyItemType.MORE),
    ExploreNearbyItem(R.drawable.explorenearby_meeting_room, R.attr.exploreNearbyServiceMeetingRoomColor, R.string.explorenearby_meeting_room,"e23fd1da-de48-4dca-8038-1c9ef1ebdd1b", ExploreNearbyItemType.EXPANDED_ITEM),
    ExploreNearbyItem(R.drawable.explorenearby_entrance, R.attr.exploreNearbyServiceEntranceColor, R.string.explorenearby_entrance,"c55b7222-153d-4afa-8824-8be2f0d92aa3", ExploreNearbyItemType.EXPANDED_ITEM),
    ExploreNearbyItem(R.drawable.explorenearby_more, null, R.string.explorenearby_less,null, ExploreNearbyItemType.LESS)
)