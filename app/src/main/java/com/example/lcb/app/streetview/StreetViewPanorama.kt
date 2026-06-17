package com.example.lcb.app.streetview

import com.google.android.gms.maps.model.LatLng

/**
 * A usable Street View panorama discovered via the Street View metadata API.
 */
data class StreetViewPanorama(
    val panoId: String,
    val position: LatLng,
    val date: String?
)
