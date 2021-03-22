package io.proximi.navigationdemo.utils

import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfMeasurement
import io.proximi.mapbox.library.Route

/**
 * Rough implementation of route replay.
 * Calling the [getNext] method advanced location along the route and  Provides locations based on provided route object.
 */
class RouteReplay(private var route: Route) {

    /**
     * Step length in meters
     */
    private var step = 0.45

    /**
     * Completed meters of route.
     */
    private var completed = 0.0

    fun getNext(): Result? {
        if (route == null || route.nodeList.isEmpty()) return null

        completed += step

        // Find current node
        var nodeSum = 0.0
        var currentNode = route.nodeList.firstOrNull { routeNode ->
            nodeSum += if (routeNode.lineStringFeatureTo != null) routeNode.distanceFromLastNode else 0.0
            return@firstOrNull completed <= nodeSum && routeNode.lineStringFeatureTo != null
        }
        if (currentNode == null) return null

        val positionPoint = TurfMeasurement.along(currentNode.lineStringFeatureTo!!.geometry() as LineString, (currentNode.distanceFromLastNode - (nodeSum - completed)), "meters")
        val nextNode = route.nodeList.getOrNull(route.nodeList.indexOf(currentNode) + 1)


        return Result(
            positionPoint,
            currentNode.level,
            nextNode != null && nextNode.level != currentNode.level && (nodeSum + nextNode.distanceFromLastNode - completed) <= step,
            nextNode?.level
        )
    }

    data class Result(
        val point: Point,
        val level: Int,
        val isLevelChange: Boolean,
        val nextNodeLevel: Int?
    )

}