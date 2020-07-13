package io.proximi.navigationdemo.utils

import com.mapbox.geojson.Point
import io.proximi.mapbox.data.model.Feature
import io.proximi.mapbox.library.ProximiioFeatureType
import io.proximi.mapbox.library.ProximiioSearchFilter

/**
 * Custom implementation of Proximi.io [ProximiioSearchFilter].
 * Providing custom filter to search allows us to override search behaviour when searching features.
 */
class SearchFilter: ProximiioSearchFilter {

    var locationPoint: Point? = null
    private val allowedFeatureTypes = arrayOf(
        ProximiioFeatureType.POI,
        ProximiioFeatureType.ELEVATOR,
        ProximiioFeatureType.ESCALATOR,
        ProximiioFeatureType.STAIRCASE
    )

    /**
     * Tag that will show up in proximi.io analytics tools.
     */
    override fun tag(): String {
        return "search"
    }

    /**
     * Names of inputs used to filter items. This is used to name search values in proximi.io analytics tools.
     */
    override fun inputNames(): Array<String> {
        return arrayOf("title", "amenityCategoryId")
    }

    /**
     * Actual filter method used when searching for features.
     * @param feature feature to test if it fits
     * @param input values to match feature with
     *
     * @return true if feature should be included in search results.
     */
    override fun filterItem(feature: Feature, vararg input: String?): Boolean {
        val name = input[0]
        val amenityCategory = input[1]

        return (
            isAllowedType(feature)
            && (
                name == null
                || feature.getTitle()?.contains(name, true) == true
            )
            && (
                amenityCategory == null
                || amenityCategory == feature.amenity?.categoryId
            )
            && (
                locationPoint == null
                || (feature.featureGeometry is Point && feature.pointToPointDistance(locationPoint!!)!! <= 800.0)
            )
        )
    }

    private fun isAllowedType(it: Feature): Boolean {
        return allowedFeatureTypes.contains(it.getType())
    }
}