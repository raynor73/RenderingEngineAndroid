package ilapin.renderingengine.android

import android.content.Context
import ilapin.renderingengine.DisplayMetricsRepository

class AndroidDisplayMetricsRepository(private val context: Context) : DisplayMetricsRepository {

    override fun getPixelDensityFactor(): Float {
        return context.resources.displayMetrics.density
    }
}