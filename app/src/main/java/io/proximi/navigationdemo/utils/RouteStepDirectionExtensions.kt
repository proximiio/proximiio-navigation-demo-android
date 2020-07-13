package io.proximi.navigationdemo.utils

import android.content.Context
import io.proximi.navigationdemo.R
import io.proximi.mapbox.library.RouteStepDirection

/**
 * Get drawable resource ID for [RouteStepDirection].
 */
fun RouteStepDirection.getDrawable(): Int {
    return when (this) {
        RouteStepDirection.TURN_AROUND -> R.drawable.ic_turn_around
        RouteStepDirection.HARD_LEFT -> R.drawable.ic_turn_sharp_left
        RouteStepDirection.LEFT -> R.drawable.ic_turn_left
        RouteStepDirection.SLIGHT_LEFT -> R.drawable.ic_turn_slight_left
        RouteStepDirection.STRAIGHT -> R.drawable.ic_turn_straight
        RouteStepDirection.SLIGHT_RIGHT -> R.drawable.ic_turn_slight_right
        RouteStepDirection.RIGHT -> R.drawable.ic_turn_right
        RouteStepDirection.HARD_RIGHT -> R.drawable.ic_turn_sharp_right
        RouteStepDirection.UP_ELEVATOR -> R.drawable.ic_stairs_up
        RouteStepDirection.UP_ESCALATOR -> R.drawable.ic_stairs_up
        RouteStepDirection.UP_STAIRS -> R.drawable.ic_stairs_up
        RouteStepDirection.DOWN_ELEVATOR -> R.drawable.ic_stairs_down
        RouteStepDirection.DOWN_ESCALATOR -> R.drawable.ic_stairs_down
        RouteStepDirection.DOWN_STAIRS -> R.drawable.ic_stairs_down
        RouteStepDirection.FINISH -> R.drawable.ic_destination
        else -> error("No drawable available for levelDirection $this")
    }
}

/**
 * Get drawable resource ID [RouteStepDirection] shown in route preview.
 */
fun RouteStepDirection.getPreviewDrawable(context: Context): Int {
    return when (this) {
        RouteStepDirection.START-> context.getDrawableIdFromAttr(R.attr.routePreviewCurrentPositionMarker)
        RouteStepDirection.TURN_AROUND -> R.drawable.ic_turn_around
        RouteStepDirection.HARD_LEFT -> R.drawable.ic_turn_sharp_left
        RouteStepDirection.LEFT -> R.drawable.ic_turn_left
        RouteStepDirection.SLIGHT_LEFT -> R.drawable.ic_turn_slight_left
        RouteStepDirection.STRAIGHT -> R.drawable.ic_turn_straight
        RouteStepDirection.SLIGHT_RIGHT -> R.drawable.ic_turn_slight_right
        RouteStepDirection.RIGHT -> R.drawable.ic_turn_right
        RouteStepDirection.HARD_RIGHT -> R.drawable.ic_turn_sharp_right
        RouteStepDirection.UP_ELEVATOR -> R.drawable.ic_stairs_up
        RouteStepDirection.UP_ESCALATOR -> R.drawable.ic_stairs_up
        RouteStepDirection.UP_STAIRS -> R.drawable.ic_stairs_up
        RouteStepDirection.DOWN_ELEVATOR -> R.drawable.ic_stairs_down
        RouteStepDirection.DOWN_ESCALATOR -> R.drawable.ic_stairs_down
        RouteStepDirection.DOWN_STAIRS -> R.drawable.ic_stairs_down
        RouteStepDirection.FINISH -> R.drawable.ic_preview_destination
        else -> error("No drawable available for direction $this")
    }
}

/**
 * Get description of route step direction as String resource ID.
 */
fun RouteStepDirection.getString(): Int {
    return when (this) {
        RouteStepDirection.START-> R.string.route_preview_start
        RouteStepDirection.TURN_AROUND -> R.string.route_preview_turn_around
        RouteStepDirection.HARD_LEFT -> R.string.route_preview_turn_left_hard
        RouteStepDirection.LEFT -> R.string.route_preview_turn_left
        RouteStepDirection.SLIGHT_LEFT -> R.string.route_preview_turn_left_slight
        RouteStepDirection.STRAIGHT -> R.string.route_preview_turn_straight
        RouteStepDirection.SLIGHT_RIGHT -> R.string.route_preview_turn_right_slight
        RouteStepDirection.RIGHT -> R.string.route_preview_turn_right
        RouteStepDirection.HARD_RIGHT -> R.string.route_preview_turn_right_hard
        RouteStepDirection.UP_ELEVATOR -> R.string.route_preview_up_elevator
        RouteStepDirection.UP_ESCALATOR -> R.string.route_preview_up_escalator
        RouteStepDirection.UP_STAIRS -> R.string.route_preview_up_stairs
        RouteStepDirection.DOWN_ELEVATOR -> R.string.route_preview_down_elevator
        RouteStepDirection.DOWN_ESCALATOR -> R.string.route_preview_down_escalator
        RouteStepDirection.DOWN_STAIRS -> R.string.route_preview_down_stairs
        RouteStepDirection.FINISH -> R.string.route_preview_finish
        else -> error("No drawable available for levelDirection $this")
    }
}