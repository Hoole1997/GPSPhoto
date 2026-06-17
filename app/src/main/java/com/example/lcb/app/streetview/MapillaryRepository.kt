package com.example.lcb.app.streetview

import com.example.lcb.app.BuildConfig
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fetches Mapillary street-level imagery points within a geographic bounding
 * box in a single request (Mapillary API v4).
 *
 * Unlike Google (which only answers per-coordinate), Mapillary supports true
 * region queries via the `bbox` parameter, so we can pull a whole visible area
 * at once. Free with a client access token.
 */
class MapillaryRepository {

    /**
     * @param bounds visible map bounds to search within
     * @param limit max points to return (API hard cap applies too)
     */
    suspend fun fetchInBounds(
        bounds: LatLngBounds,
        limit: Int = 200
    ): Result<List<StreetViewSpot>> = withContext(Dispatchers.IO) {
        val token = BuildConfig.MAPILLARY_TOKEN
        if (token.isBlank()) {
            return@withContext Result.failure(IllegalStateException("缺少 Mapillary token"))
        }

        // bbox = west,south,east,north
        val west = bounds.southwest.longitude
        val south = bounds.southwest.latitude
        val east = bounds.northeast.longitude
        val north = bounds.northeast.latitude
        val bbox = "$west,$south,$east,$north"

        val url = URL(
            "https://graph.mapillary.com/images" +
                "?access_token=" + URLEncoder.encode(token, "UTF-8") +
                "&fields=id,computed_geometry,captured_at,thumb_256_url,is_pano,camera_type,creator" +
                "&bbox=" + URLEncoder.encode(bbox, "UTF-8") +
                "&limit=$limit"
        )

        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            requestMethod = "GET"
        }

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(
                    IllegalStateException("Mapillary 请求失败：HTTP ${connection.responseCode}")
                )
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            Result.success(parse(body))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): List<StreetViewSpot> {
        val json = JSONObject(body)
        val data = json.optJSONArray("data") ?: return emptyList()
        val spots = ArrayList<StreetViewSpot>(data.length())
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val geometry = item.optJSONObject("computed_geometry") ?: continue
            val coords = geometry.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue
            val lng = coords.optDouble(0)
            val lat = coords.optDouble(1)
            val capturedAt = item.optLong("captured_at", 0L)
            val dateStr = if (capturedAt > 0) {
                DATE_FORMAT.format(Date(capturedAt))
            } else null
            val thumbUrl = item.optString("thumb_256_url").takeIf { it.isNotBlank() }
            val isPano = item.optBoolean("is_pano", false)
            val creator = item.optJSONObject("creator")?.optString("username")
                ?.takeIf { it.isNotBlank() }

            val subtitleText = buildString {
                append(if (isPano) "360° 全景" else "街景")
                if (dateStr != null) append(" · $dateStr")
                if (creator != null) append(" · @$creator")
            }

            spots.add(
                StreetViewSpot(
                    title = if (isPano) "Mapillary 全景" else "Mapillary 街景",
                    subtitle = subtitleText,
                    position = LatLng(lat, lng),
                    date = dateStr,
                    distanceMeters = null,
                    source = StreetViewSource.MAPILLARY,
                    mapillaryId = id,
                    thumbUrl = thumbUrl,
                    isPano = isPano,
                    creator = creator
                )
            )
        }
        return spots
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM", Locale.US)
    }
}
