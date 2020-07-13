package io.proximi.navigationdemo.utils

import android.content.Context
import android.content.ContextWrapper

/**
 * Custom [ScaledContextWrapper] that overrides density to scale UI.
 */
class ScaledContextWrapper(base: Context) : ContextWrapper(base) {
    companion object {
        private const val LARGE_MODE_SCALE = 1.15
        fun wrap(context: Context, largeMode: Boolean): ScaledContextWrapper {
            val resources = context.resources
            val configuration = resources.configuration
            val metrics = resources.displayMetrics
            if (largeMode) {
                configuration.densityDpi = (metrics.densityDpi * LARGE_MODE_SCALE).toInt()
            } else {
                configuration.densityDpi = (metrics.densityDpi)
            }
            return ScaledContextWrapper(context.createConfigurationContext(configuration))
        }
    }
}