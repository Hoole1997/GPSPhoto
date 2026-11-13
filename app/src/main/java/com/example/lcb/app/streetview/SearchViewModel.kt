package com.example.lcb.app.streetview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val recommendations: List<StreetViewSpot> = HotSpotCatalog.build(app)

    private var searchJob: Job? = null
    private var latestQuery: String = ""
    private var viewBias: LatLngBounds? = null

    fun setBias(bounds: LatLngBounds?) {
        viewBias = bounds
    }

    fun onQueryChanged(raw: String) {
        val query = raw.trim()
        latestQuery = query
        searchJob?.cancel()
        searchJob = null
        if (query.isEmpty()) {
            _state.value = SearchState.History(loadHistory())
            return
        }
        searchJob = viewModelScope.launch {
            delay(450) // 防抖，遵守 Nominatim ~1 req/s
            _state.value = SearchState.Loading
            val result = repository.search(query, viewBias)
            // HttpURLConnection 是阻塞调用；即使旧请求未及时响应协程取消，也不能让
            // 它在输入已清空或变更后覆盖最新的页面状态。
            if (query != latestQuery) return@launch
            result
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
    }
}
