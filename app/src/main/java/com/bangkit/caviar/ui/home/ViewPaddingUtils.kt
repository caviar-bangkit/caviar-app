package com.bangkit.caviar.ui.home

import android.content.res.Configuration
import android.content.res.Resources
import com.mapbox.maps.EdgeInsets

object ViewPaddingUtils {
    private val pixelDensity = Resources.getSystem().displayMetrics.density

    private val overviewPadding: EdgeInsets by lazy {
        EdgeInsets(
            140.0 * pixelDensity,
            40.0 * pixelDensity,
            120.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }

    private val landscapeOverviewPadding: EdgeInsets by lazy {
        EdgeInsets(
            30.0 * pixelDensity,
            380.0 * pixelDensity,
            110.0 * pixelDensity,
            20.0 * pixelDensity
        )
    }

    private val followingPadding: EdgeInsets by lazy {
        EdgeInsets(
            180.0 * pixelDensity,
            40.0 * pixelDensity,
            150.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }

    private val landscapeFollowingPadding: EdgeInsets by lazy {
        EdgeInsets(
            30.0 * pixelDensity,
            380.0 * pixelDensity,
            110.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }

    fun getOverviewPadding(resources: Resources): EdgeInsets {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            landscapeOverviewPadding
        } else {
            overviewPadding
        }
    }

    fun getFollowingPadding(resources: Resources): EdgeInsets {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            landscapeFollowingPadding
        } else {
            followingPadding
        }
    }

}