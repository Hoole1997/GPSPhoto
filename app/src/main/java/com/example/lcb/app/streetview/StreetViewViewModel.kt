package com.example.lcb.app.streetview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lcb.app.R
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared state between the map activity and the bottom-sheet tab fragments.
 */
class StreetViewViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = StreetViewRepository()
    private val mapillaryRepository = MapillaryRepository(app)

    private val _userLocation = MutableStateFlow<LatLng?>(null)
    val userLocation: StateFlow<LatLng?> = _userLocation.asStateFlow()

    private val _nearby = MutableStateFlow<ListState>(ListState.Idle)
    val nearby: StateFlow<ListState> = _nearby.asStateFlow()

    /**
     * All panoramas discovered so far, keyed by panoId. Only grows (never
     * cleared by a new/empty search), so map markers persist across page
     * switches and re-scans. Markers are rendered from this set.
     */
    private val discovered = LinkedHashMap<String, StreetViewSpot>()
    private val _markers = MutableStateFlow<List<StreetViewSpot>>(emptyList())
    val markers: StateFlow<List<StreetViewSpot>> = _markers.asStateFlow()

    @Volatile
    private var isSearching = false
    private var lastSearchCenter: LatLng? = null

    /** 区域扫描是否进行中（用于地图上的扫描动效）。 */
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _hot = MutableStateFlow(buildHotSpots())
    val hot: StateFlow<List<StreetViewSpot>> = _hot.asStateFlow()

    /** 供搜索页复用的推荐列表（即热门地标）。 */
    fun hotSpots(): List<StreetViewSpot> = buildHotSpots()

    /** A request to move the map to a spot, consumed by the activity. */
    private val _focusRequest = MutableStateFlow<LatLng?>(null)
    val focusRequest: StateFlow<LatLng?> = _focusRequest.asStateFlow()

    /** A request to open the full-screen street view for a spot. */
    private val _openStreetView = MutableStateFlow<StreetViewSpot?>(null)
    val openStreetView: StateFlow<StreetViewSpot?> = _openStreetView.asStateFlow()

    fun openStreetView(spot: StreetViewSpot) {
        _openStreetView.value = spot
    }

    fun consumeOpenStreetView() {
        _openStreetView.value = null
    }

    /** Bottom padding (navigation bar height) for the lists. */
    private val _listBottomInset = MutableStateFlow(0)
    val listBottomInset: StateFlow<Int> = _listBottomInset.asStateFlow()

    fun setListBottomInset(px: Int) {
        _listBottomInset.value = px
    }

    fun setUserLocation(location: LatLng) {
        _userLocation.value = location
    }

    /**
     * 点击地图触发的区域聚合搜索：以点击点为中心，Google 走中心采样、
     * Mapillary 走 bbox 区域查询，结果合并去重进累积集。
     * 扫描期间 [scanning] 为 true，供地图上的扫描动效使用。
     *
     * @param center 点击点（也是 Google 采样中心、列表距离排序基准）
     * @param bounds 扫描范围（用于 Mapillary bbox）
     */
    fun searchArea(center: LatLng, bounds: LatLngBounds?, force: Boolean = false) {
        if (isSearching) return

        isSearching = true
        _scanning.value = true
        lastSearchCenter = center
        _userLocation.value = center
        // 新一轮扫描：清空上一次的结果，避免地图上的点无限堆积
        discovered.clear()
        publishMarkers()
        _nearby.value = ListState.Loading

        viewModelScope.launch {
            var lastError: String? = null

            // Google：中心点周围采样
            repository.findNearbyPanoramas(center)
                .onSuccess { panoramas ->
                    for (p in panoramas) {
                        val spot = p.toSpot(center, getApplication())
                        discovered[keyOf(spot)] = spot
                    }
                }
                .onFailure { lastError = it.message }

            // Mapillary：扫描范围 bbox 批量
            if (bounds != null) {
                mapillaryRepository.fetchInBounds(bounds)
                    .onSuccess { spots ->
                        for (s in spots) {
                            discovered[keyOf(s)] = s
                        }
                    }
                    .onFailure { lastError = it.message }
            }

            if (discovered.isEmpty()) {
                _nearby.value = if (lastError != null) {
                    ListState.Error(lastError)
                } else {
                    ListState.Success(emptyList())
                }
            } else {
                publishMarkers()
                refreshNearbyListFrom(center)
            }
            isSearching = false
            _scanning.value = false
        }
    }

    /** 兼容旧调用：仅按中心搜索（无区域信息）。 */
    fun searchNearby(center: LatLng, force: Boolean = false) {
        searchArea(center, null, force)
    }

    private fun keyOf(spot: StreetViewSpot): String {
        return when (spot.source) {
            StreetViewSource.GOOGLE -> "g:${spot.panoId}"
            StreetViewSource.MAPILLARY -> "m:${spot.mapillaryId}"
        }
    }

    private fun publishMarkers() {
        _markers.value = discovered.values.toList()
    }

    /** 用累积的发现集，按到当前中心的距离重算并刷新附近列表。 */
    private fun refreshNearbyListFrom(center: LatLng) {
        val spots = discovered.values
            .map { spot ->
                spot.copy(distanceMeters = distanceMeters(center, spot.position))
            }
            .sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
        _nearby.value = if (spots.isEmpty()) ListState.Success(emptyList()) else ListState.Success(spots)
    }

    fun requestFocus(location: LatLng) {
        _focusRequest.value = location
    }

    fun consumeFocusRequest() {
        _focusRequest.value = null
    }

    private fun distanceMeters(a: LatLng, b: LatLng): Double = distanceMetersBetween(a, b)

    /** 从字符串资源构建热门地标列表（随语言切换本地化）。 */
    private fun buildHotSpots(): List<StreetViewSpot> {
        val ctx = getApplication<Application>()
        fun spot(
            titleRes: Int, subRes: Int, descRes: Int,
            lat: Double, lng: Double, cover: String
        ) = StreetViewSpot(
            title = ctx.getString(titleRes),
            subtitle = ctx.getString(subRes),
            position = LatLng(lat, lng),
            date = null,
            distanceMeters = null,
            coverUrl = cover,
            description = ctx.getString(descRes)
        )
        return listOf(
            spot(R.string.hot_eiffel_title, R.string.hot_eiffel_sub, R.string.hot_eiffel_desc,
                48.8584, 2.2945, "https://loremflickr.com/400/300/eiffeltower?lock=1"),
            spot(R.string.hot_times_title, R.string.hot_times_sub, R.string.hot_times_desc,
                40.7580, -73.9855, "https://loremflickr.com/400/300/timessquare?lock=2"),
            spot(R.string.hot_bigben_title, R.string.hot_bigben_sub, R.string.hot_bigben_desc,
                51.5007, -0.1246, "https://loremflickr.com/400/300/bigben?lock=3"),
            spot(R.string.hot_colosseum_title, R.string.hot_colosseum_sub, R.string.hot_colosseum_desc,
                41.8902, 12.4922, "https://loremflickr.com/400/300/colosseum?lock=4"),
            spot(R.string.hot_sydney_title, R.string.hot_sydney_sub, R.string.hot_sydney_desc,
                -33.8568, 151.2153, "https://loremflickr.com/400/300/sydneyoperahouse?lock=5"),
            spot(R.string.hot_shibuya_title, R.string.hot_shibuya_sub, R.string.hot_shibuya_desc,
                35.6595, 139.7004, "https://loremflickr.com/400/300/shibuya?lock=6"),
            spot(R.string.hot_goldengate_title, R.string.hot_goldengate_sub, R.string.hot_goldengate_desc,
                37.8199, -122.4783, "https://loremflickr.com/400/300/goldengatebridge?lock=7"),
            spot(R.string.hot_sagrada_title, R.string.hot_sagrada_sub, R.string.hot_sagrada_desc,
                41.4036, 2.1744, "https://loremflickr.com/400/300/sagradafamilia?lock=8")
        )
    }

    sealed interface ListState {
        data object Idle : ListState
        data object Loading : ListState
        data class Success(val spots: List<StreetViewSpot>) : ListState
        data class Error(val message: String?) : ListState
    }
}
