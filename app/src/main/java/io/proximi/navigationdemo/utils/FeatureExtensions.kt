package io.proximi.navigationdemo.utils

import android.location.Location
import android.net.Uri
import com.google.gson.JsonObject
import com.mapbox.geojson.Point
import io.proximi.mapbox.data.model.Feature
import io.proximi.navigationdemo.BuildConfig
import io.proximi.navigationdemo.ProximiioAuthToken
import io.proximi.navigationdemo.R
import io.proximi.mapbox.library.ProximiioFeatureType

/**
 * Get list of URLs for feature images. Returns empty list if no URLs are present.
 */
val Feature.imageUrlList: List<String>
    get() {
        return getImageUrlList(ProximiioAuthToken.TOKEN) ?: listOf()
    }

/**
 * Convert Point mapbox geometry to android's location object.
 */
fun Point.toLocation(): Location = Location("").also { it.latitude = this.latitude(); it.longitude = this.longitude() }