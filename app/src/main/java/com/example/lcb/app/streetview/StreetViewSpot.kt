package com.example.lcb.app.streetview

import com.google.android.gms.maps.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A street view entry shown in the bottom-sheet list (nearby or hot).
 */
data class StreetViewSpot(
    val title: String,
    val subtitle: String,
    val position: LatLng,
    val date: String?,
    val distanceMeters: Double?,
    val panoId: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val source: StreetViewSource = StreetViewSource.GOOGLE,
    val mapillaryId: String? = null,
    val thumbUrl: String? = null,
    val isPano: Boolean = false,
    val creator: String? = null
)

enum class StreetViewSource { GOOGLE, MAPILLARY }

fun StreetViewPanorama.toSpot(origin: LatLng): StreetViewSpot {
    val distance = haversineMeters(origin, position)
    return StreetViewSpot(
        title = "街景点",
        subtitle = date?.let { "拍摄于 $it" } ?: "拍摄日期未知",
        position = position,
        date = date,
        distanceMeters = distance,
        panoId = panoId
    )
}

private fun haversineMeters(a: LatLng, b: LatLng): Double {
    return distanceMetersBetween(a, b)
}

fun distanceMetersBetween(a: LatLng, b: LatLng): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * earthRadius * atan2(sqrt(h), sqrt(1 - h))
}
