package com.example.lcb.app.streetview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared state between the map activity and the bottom-sheet tab fragments.
 */
class StreetViewViewModel : ViewModel() {

    private val repository = StreetViewRepository()
    private val mapillaryRepository = MapillaryRepository()

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

    private val _hot = MutableStateFlow(HOT_SPOTS)
    val hot: StateFlow<List<StreetViewSpot>> = _hot.asStateFlow()

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
                        val spot = p.toSpot(center)
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

    sealed interface ListState {
        data object Idle : ListState
        data object Loading : ListState
        data class Success(val spots: List<StreetViewSpot>) : ListState
        data class Error(val message: String?) : ListState
    }

    companion object {
        // "热门街景"没有官方 API，这里内置一份精选的著名地标。
        // 封面图使用 Wikimedia Commons 的公开图片（缩略图尺寸）。
        val HOT_SPOTS = listOf(
            StreetViewSpot(
                title = "埃菲尔铁塔",
                subtitle = "法国 · 巴黎",
                position = LatLng(48.8584, 2.2945),
                date = null,
                distanceMeters = null,
                coverUrl = "https://loremflickr.com/400/300/eiffeltower?lock=1",
                description = "巴黎的标志性铁塔，塞纳河畔的浪漫地标，俯瞰整座城市。"
            ),
            StreetViewSpot(
                title = "时代广场",
                subtitle = "美国 · 纽约",
                position = LatLng(40.7580, -73.9855),
                date = null,
                distanceMeters = null,
                coverUrl = "https://loremflickr.com/400/300/timessquare?lock=2",
                description = "纽约最繁华的十字路口，霓虹广告与人潮昼夜不息。"
            ),
            StreetViewSpot(
                title = "大本钟",
                subtitle = "英国 · 伦敦",
                position = LatLng(51.5007, -0.1246),
                date = null,
                distanceMeters = null,
                coverUrl = "https://loremflickr.com/400/300/bigben?lock=3",
                description = "威斯敏斯特宫的钟楼，伦敦最具代表性的建筑之一。"
            ),
            StreetViewSpot(
                title = "罗马斗兽场",
                subtitle = "意大利 · 罗马",
                position = LatLng(41.8902, 12.4922),
                date = null,
                distanceMeters = null,
                coverUrl = "https://loremflickr.com/400/300/colosseum?lock=4",
                description = "古罗马的圆形竞技场，两千年历史的恢弘遗迹。"
            ),
            StreetViewSpot(
                title = "悉尼歌剧院",
                subtitle = "澳大利亚 · 悉尼",
                position = LatLng(-33.8568, 151.2153),
                date = null,
                distanceMeters = null,
                coverUrl = "https://loremflickr.com/400/300/sydneyoperahouse?lock=5",
                description = "帆船造型的世界级表演艺术中心，矗立于悉尼港湾。"
            ),
            StreetViewSpot(
                title = "涩谷十字路口",
                subtitle = "日本 · 东京",
                position = LatLng(35.6595, 139.7004),
                date = null,
                distanceMeters = null,
                coverUrl = "https://loremflickr.com/400/300/shibuya?lock=6",
                description = "全球最繁忙的人行横道，东京潮流文化的中心。"
            ),
            StreetViewSpot(
                title = "金门大桥",
                subtitle = "美国 · 旧金山",
                position = LatLng(37.8199, -122.4783),
                date = null,
                distanceMeters = null,
                coverUrl = "https://loremflickr.com/400/300/goldengatebridge?lock=7",
                description = "横跨金门海峡的红色悬索大桥，旧金山的象征。"
            ),
            StreetViewSpot(
                title = "圣家堂",
                subtitle = "西班牙 · 巴塞罗那",
                position = LatLng(41.4036, 2.1744),
                date = null,
                distanceMeters = null,
                coverUrl = "https://loremflickr.com/400/300/sagradafamilia?lock=8",
                description = "高迪未竟的旷世杰作，巴塞罗那的精神地标。"
            )
        )
    }
}
