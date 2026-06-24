package com.example.lcb.app.streetview

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/**
 * Forward geocoding via Nominatim (OpenStreetMap), free and key-less.
 *
 * Usage policy notes:
 * - must send an identifying User-Agent
 * - max ~1 request/second (callers should debounce)
 */
class GeocodeRepository {

    /**
     * @param query free-form text to search
     * @param bias optional map bounds to bias results toward the current area
     */
    suspend fun search(
        query: String,
        bias: LatLngBounds? = null,
        limit: Int = 12
    ): Result<List<PlaceResult>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList())

        val sb = StringBuilder("https://nominatim.openstreetmap.org/search")
        sb.append("?q=").append(URLEncoder.encode(query, "UTF-8"))
        sb.append("&format=json&addressdetails=1&limit=$limit")
        if (bias != null) {
            // viewbox = left,top,right,bottom（经度,纬度）；bounded=0 表示仅偏向、不强制
            val left = bias.southwest.longitude
            val right = bias.northeast.longitude
            val top = bias.northeast.latitude
            val bottom = bias.southwest.latitude
            sb.append("&viewbox=$left,$top,$right,$bottom&bounded=0")
        }

        val connection = (URL(sb.toString()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            requestMethod = "GET"
            // 遵守 Nominatim 使用政策：提供可标识的 User-Agent
            setRequestProperty("User-Agent", "StreetViewExplorer/1.0 (Android)")
            setRequestProperty("Accept-Language", "zh-CN,zh,en")
        }

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(
                    IllegalStateException("Search failed: HTTP ${connection.responseCode}")
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

    private fun parse(body: String): List<PlaceResult> {
        val arr = JSONArray(body)
        val results = ArrayList<PlaceResult>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val lat = item.optString("lat").toDoubleOrNull() ?: continue
            val lon = item.optString("lon").toDoubleOrNull() ?: continue
            val name = item.optString("name").takeIf { it.isNotBlank() }
                ?: item.optString("display_name").substringBefore(",")
            val display = item.optString("display_name")
            // 去掉首段（name）后的地址部分，更简洁
            val address = if (display.startsWith(name)) {
                display.removePrefix(name).trimStart(',', ' ')
            } else display
            results.add(
                PlaceResult(
                    name = name,
                    address = address,
                    position = LatLng(lat, lon),
                    type = item.optString("type").takeIf { it.isNotBlank() }
                )
            )
        }
        return results
    }
}
