package com.example.lcb.app.streetview

import com.example.lcb.app.BuildConfig
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos

/**
 * Discovers usable Street View panoramas around a coordinate.
 *
 * Google does not offer a "list all panoramas in an area" endpoint. The only
 * way to know whether a point has Street View is the Street View metadata API,
 * which is free (no charge). So we sample a grid of points around the center
 * and ask the metadata endpoint for each (the API snaps to the nearest pano),
 * then de-duplicate by pano id.
 *
 * Stability measures:
 * - bounded concurrency (semaphore) so we never fire dozens of sockets at once
 * - one retry per request before giving up on a point
 * - partial failures never fail the whole batch; we return whatever we found
 *
 * Requires "Street View Static API" enabled on the Cloud project for the key.
 */
class StreetViewRepository {

    /**
     * @param center the location to search around
     * @param radiusMeters how far out the grid extends from the center
     * @param step number of samples per axis (step x step total probes)
     */
    suspend fun findNearbyPanoramas(
        center: LatLng,
        radiusMeters: Double = 280.0,
        step: Int = 7
    ): Result<List<StreetViewPanorama>> = withContext(Dispatchers.IO) {
        val key = BuildConfig.MAPS_API_KEY
        if (key.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Missing Maps API key"))
        }

        val samples = buildGrid(center, radiusMeters, step)
        val semaphore = Semaphore(MAX_CONCURRENCY)

        try {
            val results = coroutineScope {
                samples.map { point ->
                    async {
                        semaphore.withPermit { fetchMetadataWithRetry(point, key) }
                    }
                }.map { it.await() }
            }

            val deduped = LinkedHashMap<String, StreetViewPanorama>()
            var denied = false
            var anyNetworkError = false
            for (outcome in results) {
                when (outcome) {
                    is MetadataOutcome.Found ->
                        deduped.putIfAbsent(outcome.panorama.panoId, outcome.panorama)
                    is MetadataOutcome.Denied -> denied = true
                    MetadataOutcome.NetworkError -> anyNetworkError = true
                    MetadataOutcome.Empty -> Unit
                }
            }

            when {
                deduped.isNotEmpty() -> Result.success(deduped.values.toList())
                denied -> Result.failure(
                    IllegalStateException("Street View request denied. Enable Street View Static API for the project")
                )
                anyNetworkError -> Result.failure(
                    IllegalStateException("Network error, street view search failed")
                )
                else -> Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchMetadataWithRetry(point: LatLng, key: String): MetadataOutcome {
        var last = fetchMetadata(point, key)
        if (last == MetadataOutcome.NetworkError) {
            // 单点失败重试一次，缓解弱网下的偶发超时
            last = fetchMetadata(point, key)
        }
        return last
    }

    private fun fetchMetadata(point: LatLng, key: String): MetadataOutcome {
        val url = URL(
            "https://maps.googleapis.com/maps/api/streetview/metadata" +
                "?location=${point.latitude},${point.longitude}&key=$key"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            requestMethod = "GET"
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                MetadataOutcome.NetworkError
            } else {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parse(body)
            }
        } catch (e: Exception) {
            MetadataOutcome.NetworkError
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): MetadataOutcome {
        val json = JSONObject(body)
        return when (json.optString("status")) {
            "OK" -> {
                val location = json.optJSONObject("location")
                val panoId = json.optString("pano_id")
                if (location != null && panoId.isNotBlank()) {
                    MetadataOutcome.Found(
                        StreetViewPanorama(
                            panoId = panoId,
                            position = LatLng(
                                location.optDouble("lat"),
                                location.optDouble("lng")
                            ),
                            date = json.optString("date").takeIf { it.isNotBlank() }
                        )
                    )
                } else {
                    MetadataOutcome.Empty
                }
            }
            "REQUEST_DENIED", "OVER_QUERY_LIMIT" -> MetadataOutcome.Denied
            else -> MetadataOutcome.Empty // ZERO_RESULTS / NOT_FOUND 等
        }
    }

    private fun buildGrid(center: LatLng, radiusMeters: Double, step: Int): List<LatLng> {
        val latDegPerMeter = 1.0 / 111_320.0
        val lngDegPerMeter = 1.0 / (111_320.0 * cos(center.latitude * PI / 180.0))

        val points = ArrayList<LatLng>(step * step)
        val half = (step - 1) / 2.0
        for (i in 0 until step) {
            for (j in 0 until step) {
                val dLatMeters = (i - half) / half.coerceAtLeast(1.0) * radiusMeters
                val dLngMeters = (j - half) / half.coerceAtLeast(1.0) * radiusMeters
                points.add(
                    LatLng(
                        center.latitude + dLatMeters * latDegPerMeter,
                        center.longitude + dLngMeters * lngDegPerMeter
                    )
                )
            }
        }
        return points
    }

    private sealed interface MetadataOutcome {
        data class Found(val panorama: StreetViewPanorama) : MetadataOutcome
        data object Denied : MetadataOutcome
        data object NetworkError : MetadataOutcome
        data object Empty : MetadataOutcome
    }

    companion object {
        private const val MAX_CONCURRENCY = 6
    }
}
