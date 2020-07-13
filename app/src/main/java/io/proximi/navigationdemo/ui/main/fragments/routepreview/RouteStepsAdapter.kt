package io.proximi.navigationdemo.ui.main.fragments.routepreview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.proximi.navigationdemo.R
import io.proximi.navigationdemo.utils.getPreviewDrawable
import io.proximi.navigationdemo.utils.getString
import io.proximi.navigationdemo.utils.UnitHelper
import io.proximi.mapbox.library.Route
import kotlinx.android.synthetic.main.fragment_route_preview_item.view.*

/**
 * [RecyclerView.Adapter] used in [RoutePreviewFragment]. This adapter servers to display a list of
 * route steps.
 */
class RouteStepsAdapter: RecyclerView.Adapter<RouteStepsAdapter.RouteStepViewHolder>() {

    var nodeList = listOf<Route.RouteNode>()
        set(value) { field = value; notifyDataSetChanged(); }

    /**
     * Update with route information.
     */
    fun updateRoute(route: Route?) {
        if (route == null) {
            nodeList = listOf()
        } else {
            nodeList = route.nodeList
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteStepViewHolder {
        val layout = if (viewType == 0) R.layout.fragment_route_preview_item else R.layout.fragment_route_preview_item_no_tint
        return RouteStepViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun getItemCount(): Int {
        return nodeList.size
    }

    override fun onBindViewHolder(holder: RouteStepViewHolder, position: Int) {
        holder.load(nodeList[position])
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0 || position == nodeList.size - 1) return 1 else 0
    }

    /**
     * ViewHolder for individual steps of route.
     */
    inner class RouteStepViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        fun load(routeNode: Route.RouteNode) {
            itemView.stepTextView.text = itemView.context.getString(routeNode.direction.getString())
            itemView.stepImageView.setImageResource(routeNode.direction.getPreviewDrawable(itemView.context))
            if (routeNode.distanceFromLastNode != 0.0) {
                itemView.stepCountTextView.visibility = View.VISIBLE
                itemView.stepCountTextView.text = UnitHelper.getDistanceInPreferenceUnit(routeNode.distanceFromLastNode, itemView.context)
            } else {
                itemView.stepCountTextView.visibility = View.GONE
            }
        }
    }
}