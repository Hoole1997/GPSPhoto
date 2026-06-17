package com.example.lcb.app.streetview

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem

/**
 * Wraps a [StreetViewSpot] so it can be fed into the clustering manager.
 */
class StreetViewClusterItem(val spot: StreetViewSpot) : ClusterItem {
    override fun getPosition(): LatLng = spot.position
    override fun getTitle(): String = spot.title
    override fun getSnippet(): String? = spot.subtitle
    override fun getZIndex(): Float = 0f
}
