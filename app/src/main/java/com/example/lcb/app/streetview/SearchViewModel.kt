package com.example.lcb.app.streetview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lcb.app.R
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = GeocodeRepository()
    private val prefs = app.getSharedPreferences("street_view_search", android.content.Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<SearchState>(SearchState.History(loadHistory()))
    val state: StateFlow<SearchState> = _state.asStateFlow()

    /** 推荐（即热门地标），无输入时与历史一起展示 */
    val recommendations: List<StreetViewSpot> = buildRecommendations(app)

    private var searchJob: Job? = null
    private var viewBias: LatLngBounds? = null

    fun setBias(bounds: LatLngBounds?) {
        viewBias = bounds
    }

    fun onQueryChanged(raw: String) {
        val query = raw.trim()
        searchJob?.cancel()
        if (query.isEmpty()) {
            _state.value = SearchState.History(loadHistory())
            return
        }
        searchJob = viewModelScope.launch {
            delay(450) // 防抖，遵守 Nominatim ~1 req/s
            _state.value = SearchState.Loading
            repository.search(query, viewBias)
                .onSuccess { list ->
                    _state.value = if (list.isEmpty()) SearchState.Empty else SearchState.Results(list)
                }
                .onFailure { _state.value = SearchState.Error(it.message) }
        }
    }

    fun addHistory(place: PlaceResult) {
        val current = loadHistory().toMutableList()
        current.removeAll { it.name == place.name && it.address == place.address }
        current.add(0, place)
        val trimmed = current.take(MAX_HISTORY)
        saveHistory(trimmed)
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
        _state.value = SearchState.History(emptyList())
    }

    private fun loadHistory(): List<PlaceResult> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                PlaceResult(
                    name = o.optString("name"),
                    address = o.optString("address"),
                    position = com.google.android.gms.maps.model.LatLng(
                        o.optDouble("lat"), o.optDouble("lng")
                    ),
                    type = o.optString("type").takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveHistory(list: List<PlaceResult>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("name", p.name)
                    put("address", p.address)
                    put("lat", p.position.latitude)
                    put("lng", p.position.longitude)
                    put("type", p.type ?: "")
                }
            )
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    sealed interface SearchState {
        data class History(val items: List<PlaceResult>) : SearchState
        data object Loading : SearchState
        data class Results(val items: List<PlaceResult>) : SearchState
        data object Empty : SearchState
        data class Error(val message: String?) : SearchState
    }

    companion object {
        private const val KEY_HISTORY = "search_history"
        private const val MAX_HISTORY = 10

        private fun buildRecommendations(ctx: android.content.Context): List<StreetViewSpot> {
            fun spot(titleRes: Int, subRes: Int, lat: Double, lng: Double, cover: String) =
                StreetViewSpot(
                    title = ctx.getString(titleRes),
                    subtitle = ctx.getString(subRes),
                    position = com.google.android.gms.maps.model.LatLng(lat, lng),
                    date = null,
                    distanceMeters = null,
                    coverUrl = cover
                )
            return listOf(
                spot(R.string.hot_eiffel_title, R.string.hot_eiffel_sub, 48.8584, 2.2945, "https://loremflickr.com/400/300/eiffeltower?lock=1"),
                spot(R.string.hot_times_title, R.string.hot_times_sub, 40.7580, -73.9855, "https://loremflickr.com/400/300/timessquare?lock=2"),
                spot(R.string.hot_bigben_title, R.string.hot_bigben_sub, 51.5007, -0.1246, "https://loremflickr.com/400/300/bigben?lock=3"),
                spot(R.string.hot_colosseum_title, R.string.hot_colosseum_sub, 41.8902, 12.4922, "https://loremflickr.com/400/300/colosseum?lock=4"),
                spot(R.string.hot_sydney_title, R.string.hot_sydney_sub, -33.8568, 151.2153, "https://loremflickr.com/400/300/sydneyoperahouse?lock=5"),
                spot(R.string.hot_shibuya_title, R.string.hot_shibuya_sub, 35.6595, 139.7004, "https://loremflickr.com/400/300/shibuya?lock=6"),
                spot(R.string.hot_goldengate_title, R.string.hot_goldengate_sub, 37.8199, -122.4783, "https://loremflickr.com/400/300/goldengatebridge?lock=7"),
                spot(R.string.hot_sagrada_title, R.string.hot_sagrada_sub, 41.4036, 2.1744, "https://loremflickr.com/400/300/sagradafamilia?lock=8")
            )
        }
    }
}
