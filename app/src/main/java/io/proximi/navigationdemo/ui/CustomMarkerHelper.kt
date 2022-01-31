package io.proximi.navigationdemo.ui

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import io.proximi.mapbox.library.ProximiioMapbox
import io.proximi.navigationdemo.R

private val MARKER_ID = "marker.icon.id"
private val MAPBOX_BASE_LAYER = "proximiio-pois-icons"

class CustomMarkerHelper(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val mapboxMap: MapboxMap,
    private val proximiioMapbox: ProximiioMapbox,
    private var features: LiveData<List<Feature>>
) {
    private val style: LiveData<String?> get() = proximiioMapbox.style

    private val observerMarker = Observer<List<Feature>> {
        mapboxMap.getStyle { style ->
            val source = style.getSource("source.$MARKER_ID") as? GeoJsonSource
                ?: GeoJsonSource("source.$MARKER_ID").apply { style.addSource(this) }

            val layer = style.getLayer("layer.$MARKER_ID") ?: SymbolLayer(
                "layer.$MARKER_ID",
                "source.$MARKER_ID"
            ).withProperties(
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                PropertyFactory.iconImage(Expression.get("image")),
                PropertyFactory.iconAllowOverlap(true)
            ).apply {
                this.minZoom = 0.0f
                this.maxZoom = 24.0f
                style.addLayerBelow(this, MAPBOX_BASE_LAYER)
            }
            layer.setProperties(
                // TODO: introduce logic for level handling
                PropertyFactory.visibility(Property.VISIBLE)
            )

            if (style.getImage("image.$MARKER_ID") == null) {
                style.addImage(
                    "image.$MARKER_ID",
                    ResourcesCompat.getDrawable(
                        context.resources,
                        R.drawable.ic_marker,
                        null
                    )!!
                )
            }

            it.forEach { feature ->
                feature.addStringProperty("image", "image.$MARKER_ID")
            }

            source.setGeoJson(FeatureCollection.fromFeatures(it))
        }
    }

    init {
        style.observe(lifecycleOwner) {
            if (it != null) {
                observerMarker.onChanged(features.value)
            }
        }
        features.observe(lifecycleOwner, observerMarker)
    }
}