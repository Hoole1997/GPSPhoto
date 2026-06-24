package com.example.lcb.app.streetview

import com.google.android.gms.maps.model.LatLng

/**
 * A geocoding search result from Nominatim (OpenStreetMap).
 */
data class PlaceResult(
    val name: String,
    val address: String,
    val position: LatLng,
    val type: String?
)
