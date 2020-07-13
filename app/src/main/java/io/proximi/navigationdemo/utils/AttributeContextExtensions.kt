package io.proximi.navigationdemo.utils

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes

/**
 * Convert attribute value to Color Int value.
 */
fun Context.getColorFromAttr(@AttrRes attrColor: Int, typedValue: TypedValue = TypedValue(), resolveRefs: Boolean = true): Int {
    theme.resolveAttribute(attrColor, typedValue, resolveRefs)
    return typedValue.data
}

/**
 * Convert attribute value to drawale resource ID.
 */
fun Context.getDrawableIdFromAttr(@AttrRes attrDrawable: Int, typedValue: TypedValue = TypedValue(), resolveRefs: Boolean = true): Int {
    theme.resolveAttribute(attrDrawable, typedValue, true)
    return typedValue.resourceId
}