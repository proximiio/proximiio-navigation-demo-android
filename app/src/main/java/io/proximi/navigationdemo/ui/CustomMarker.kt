package io.proximi.navigationdemo.ui

import com.mapbox.geojson.Feature
import com.mapbox.geojson.Geometry

data class CustomMarker(val id: String, val customData: Int, val location: Geometry) {
    val feature: Feature = Feature.fromGeometry(location, null, id)
}