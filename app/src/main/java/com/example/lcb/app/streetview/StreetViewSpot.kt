package com.example.lcb.app.streetview

import android.content.Context
import com.example.lcb.app.R
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

/**
 * 根据当前语言重新生成附近街景的展示文本，同时保留接口返回的原始字段。
 * Activity 因语言切换重建时 ViewModel 仍会保留，因此不能只在首次搜索时本地化。
 */
fun StreetViewSpot.localizedForNearby(context: Context): StreetViewSpot {
    return when (source) {
        StreetViewSource.GOOGLE -> copy(
            title = context.getString(R.string.street_view_marker_title),
            subtitle = date?.let { context.getString(R.string.sv_date_prefix, it) }
                ?: context.getString(R.string.sv_date_unknown)
        )
        StreetViewSource.MAPILLARY -> copy(
            title = context.getString(
                if (isPano) R.string.mapillary_pano_title else R.string.mapillary_street_title
            ),
            subtitle = buildString {
                append(
                    context.getString(if (isPano) R.string.sv_pano else R.string.sv_street)
                )
                date?.let { append(" · $it") }
                creator?.let { append(" · @$it") }
            }
        )
    }
}

fun StreetViewPanorama.toSpot(origin: LatLng, context: Context): StreetViewSpot {
    val distance = haversineMeters(origin, position)
    return StreetViewSpot(
        title = context.getString(R.string.street_view_marker_title),
        subtitle = date?.let { context.getString(R.string.sv_date_prefix, it) }
            ?: context.getString(R.string.sv_date_unknown),
        position = position,
        date = date,
        distanceMeters = distance,
        panoId = panoId
    ).localizedForNearby(context)
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
