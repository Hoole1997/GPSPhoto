package com.example.lcb.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.example.lcb.app.streetview.StreetViewPagerAdapter
import com.example.lcb.app.streetview.StreetViewViewModel
import com.example.lcb.app.utils.loadInterstitial
import com.example.lcb.app.utils.loadNative
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }
    private val cancellationTokenSource = CancellationTokenSource()
    private val viewModel: StreetViewViewModel by viewModels()

    private var googleMap: GoogleMap? = null
    private var clusterManager:
        com.google.maps.android.clustering.ClusterManager<com.example.lcb.app.streetview.StreetViewClusterItem>? = null
    private var userMarker: Marker? = null
    private var hasCenteredOnUser = false
    private var hasAutoScanned = false
    private var isAdjustingZoomFromSlider = false
    private lateinit var scanOverlay: com.example.lcb.app.streetview.ScanOverlayView
    // 当前扫描范围的地理锚点（中心 + 边缘点），用于相机移动时重新投影
    private var scanCenterLatLng: LatLng? = null
    private var scanEdgeLatLng: LatLng? = null
    private var bottomSheetBehavior:
        com.google.android.material.bottomsheet.BottomSheetBehavior<android.view.View>? = null

    private val prefs by lazy {
        getSharedPreferences("street_view_prefs", Context.MODE_PRIVATE)
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            primeWithLastLocation()
            moveToCurrentLocation()
        } else {
            toast(getString(R.string.location_permission_denied))
        }
    }

    private val searchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        if (data.getBooleanExtra(SearchActivity.EXTRA_USE_MY_LOCATION, false)) {
            requestLocationOrPermission()
            return@registerForActivityResult
        }
        val lat = data.getDoubleExtra(SearchActivity.EXTRA_LAT, Double.NaN)
        val lng = data.getDoubleExtra(SearchActivity.EXTRA_LNG, Double.NaN)
        if (!lat.isNaN() && !lng.isNaN()) {
            flyToAndScan(LatLng(lat, lng))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main_home)
        // 全屏沉浸：地图铺满整屏（延伸到状态栏/导航栏下）。
        // 给搜索框让出状态栏高度；并把底部面板高度限制为"搜索框下方到屏幕底部"，
        // 这样面板展开时正好停在搜索框下，且只有 收起/展开 两档，无中间停顿。
        val searchBar = findViewById<android.view.View>(R.id.searchBar)
        val bottomSheet = findViewById<android.view.View>(R.id.bottomSheet)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (searchBar.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.topMargin = bars.top + dp(12)
                searchBar.layoutParams = lp
            }
            // 面板顶部上限 = 状态栏 + 搜索框上间距 + 搜索框高度 + 搜索框下间距
            val sheetTopLimit = bars.top + dp(12) + dp(52) + dp(12)
            val sheetHeight = root.height - sheetTopLimit
            if (sheetHeight > 0 && bottomSheet.layoutParams.height != sheetHeight) {
                bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                    height = sheetHeight
                }
            }
            // 列表底部留出导航栏高度，避免最后一项被导航栏遮挡
            viewModel.setListBottomInset(bars.bottom + dp(8))
            insets
        }

        setupBottomSheet()

        scanOverlay = findViewById(R.id.scanOverlay)

        // 点击顶部搜索框 → 打开搜索页（带当前可见区域用于结果偏向）
        findViewById<android.view.View>(R.id.searchBar).setOnClickListener {
            val bias = googleMap?.projection?.visibleRegion?.latLngBounds
            searchLauncher.launch(SearchActivity.newIntent(this, bias))
        }

        // 设置按钮（在搜索框内，单独处理点击，避免触发搜索）
        findViewById<android.widget.ImageView>(R.id.settingsButton).setOnClickListener {
            startActivity(SettingsActivity.newIntent(this))
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapContainer) as SupportMapFragment
        mapFragment.getMapAsync(this)

        findViewById<android.widget.ImageView>(
            R.id.myLocationFab
        ).setOnClickListener {
            requestLocationOrPermission()
        }

        findViewById<android.widget.ImageView>(
            R.id.mapTypeFab
        ).setOnClickListener {
            toggleMapType()
        }

        setupZoomSlider()

        observeViewModel()

        // 底部面板内原生广告
        loadNative(container = findViewById(R.id.sheetAdContainer))
    }

    private fun setupZoomSlider() {
        val seekBar = findViewById<android.widget.SeekBar>(R.id.zoomSeekBar)
        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val zoom = progressToZoom(progress)
                    isAdjustingZoomFromSlider = true
                    googleMap?.animateCamera(CameraUpdateFactory.zoomTo(zoom), 150, null)
                }
            }

            override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar) {
                isAdjustingZoomFromSlider = false
            }
        })
    }

    private fun progressToZoom(progress: Int): Float {
        return MIN_ZOOM + (progress / 100f) * (MAX_ZOOM - MIN_ZOOM)
    }

    private fun zoomToProgress(zoom: Float): Int {
        return (((zoom - MIN_ZOOM) / (MAX_ZOOM - MIN_ZOOM)) * 100f)
            .toInt().coerceIn(0, 100)
    }

    private fun syncZoomSlider(zoom: Float) {
        if (isAdjustingZoomFromSlider) return
        findViewById<android.widget.SeekBar>(R.id.zoomSeekBar).progress = zoomToProgress(zoom)
    }

    private fun setupBottomSheet() {
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        bottomSheetBehavior = com.google.android.material.bottomsheet.BottomSheetBehavior
            .from(findViewById(R.id.bottomSheet))

        // 面板上滑时渐隐右下角地图控件，避免与顶部搜索框重叠；回落时渐显
        val mapControls = findViewById<android.view.View>(R.id.mapControls)
        bottomSheetBehavior?.addBottomSheetCallback(
            object : com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: android.view.View, newState: Int) {}

                override fun onSlide(bottomSheet: android.view.View, slideOffset: Float) {
                    // slideOffset: 0=收起(底部) → 1=完全展开(顶部)
                    val visibility = (1f - slideOffset).coerceIn(0f, 1f)
                    mapControls.alpha = visibility
                    mapControls.isClickable = visibility > 0.1f
                    // 完全透明时不接收触摸，避免被遮挡区域误触
                    mapControls.visibility =
                        if (visibility <= 0.02f) android.view.View.INVISIBLE else android.view.View.VISIBLE
                }
            }
        )

        val pagerAdapter = StreetViewPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = getString(
                if (position == 0) R.string.tab_hot else R.string.tab_nearby
            )
        }.attach()

        // ViewPager2 内部横向 RecyclerView 不参与嵌套滚动，避免它被 BottomSheet 当成联动对象
        (viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView)
            ?.isNestedScrollingEnabled = false

        // 只让当前可见页的列表参与 BottomSheet 的嵌套滚动联动，切页时刷新。
        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    syncActivePageNestedScroll(position)
                }

                override fun onPageScrollStateChanged(state: Int) {
                    if (state == ViewPager2.SCROLL_STATE_IDLE) {
                        syncActivePageNestedScroll(viewPager.currentItem)
                    }
                }
            }
        )
        viewPager.post { syncActivePageNestedScroll(viewPager.currentItem) }
    }

    private fun syncActivePageNestedScroll(activePosition: Int) {
        supportFragmentManager.fragments.forEach { fragment ->
            if (fragment is com.example.lcb.app.streetview.StreetViewListFragment) {
                fragment.setNestedScrollActive(fragment.pageType() == activePosition)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.markers.collect { spots ->
                        renderPanoramaMarkers(spots)
                    }
                }
                launch {
                    viewModel.scanning.collect { scanning ->
                        if (!scanning) {
                            scanOverlay.stop()
                            scanCenterLatLng = null
                            scanEdgeLatLng = null
                        }
                    }
                }
                launch {
                    viewModel.focusRequest.collect { target ->
                        if (target != null) {
                            focusOn(target)
                            viewModel.consumeFocusRequest()
                        }
                    }
                }
                launch {
                    viewModel.openStreetView.collect { spot ->
                        if (spot != null) {
                            val intent = StreetViewActivity.newIntent(
                                this@MainActivity,
                                lat = spot.position.latitude,
                                lng = spot.position.longitude,
                                pano = spot.panoId,
                                title = spot.title,
                                isMapillary = spot.source ==
                                    com.example.lcb.app.streetview.StreetViewSource.MAPILLARY,
                                mapillaryId = spot.mapillaryId
                            )
                            viewModel.consumeOpenStreetView()
                            openStreetViewWithAd(intent)
                        }
                    }
                }
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        // 默认用矢量地图，瓦片轻、平移缩放最顺滑；卫星图可通过按钮切换
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isRotateGesturesEnabled = true
        map.uiSettings.isTiltGesturesEnabled = true

        setupClusterManager(map)

        // 地图缩放变化（手势/动画）时同步滑块位置 + 驱动聚合重算
        map.setOnCameraIdleListener {
            syncZoomSlider(map.cameraPosition.zoom)
            clusterManager?.onCameraIdle()
        }

        // 相机移动时，让扫描罩子跟随地图（按地理锚点重新投影），避免错位
        map.setOnCameraMoveListener {
            if (scanOverlay.isActive()) {
                updateScanOverlayGeometry(start = false)
            }
        }

        // 点击地图：在点击处显示扫描动效，并对该范围聚合搜索街景
        map.setOnMapClickListener { latLng ->
            startAreaScan(latLng)
        }

        // marker 点击交给聚合管理器（点聚合点会放大，点单点打开街景）
        map.setOnMarkerClickListener { marker ->
            clusterManager?.onMarkerClick(marker) ?: false
        }

        // 地图（重新）就绪后，把已发现的街景点重新喂给聚合管理器
        restorePanoramaMarkers()

        val cached = readCachedLocation()
        if (cached != null) {
            // 有缓存：直接落在用户附近，避免拉取全球卫星瓦片
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(cached, 16f))
        } else {
            // 首次安装：用较温和的区域级缩放，而非 zoom 1 的全球视图，减少首屏瓦片压力
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(20.0, 0.0), 3f))
        }

        if (hasLocationPermission()) {
            primeWithLastLocation()
            moveToCurrentLocation()
        } else {
            requestLocationOrPermission()
        }
    }

    private fun requestLocationOrPermission() {
        if (hasLocationPermission()) {
            primeWithLastLocation()
            moveToCurrentLocation()
        } else {
            showLocationPermissionDialog()
        }
    }

    /** 申请定位权限前，先弹出自定义提示框（不可点击外部取消，仅“去授权”按钮）。 */
    private fun showLocationPermissionDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_location_permission, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )
        view.findViewById<android.widget.TextView>(R.id.grantButton).setOnClickListener {
            dialog.dismiss()
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        dialog.show()
        // 控制弹框宽度，留出两侧边距，整体更小巧
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.72f).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    @SuppressLint("MissingPermission")
    private fun primeWithLastLocation() {
        if (!hasLocationPermission()) return
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null && !hasCenteredOnUser) {
                val latLng = LatLng(location.latitude, location.longitude)
                cacheLocation(latLng)
                updateUserMarker(latLng)
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun moveToCurrentLocation() {
        if (!hasLocationPermission()) return
        val map = googleMap ?: return
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location ->
            if (location == null) {
                toast(getString(R.string.location_unavailable))
                return@addOnSuccessListener
            }
            val latLng = LatLng(location.latitude, location.longitude)
            cacheLocation(latLng)
            updateUserMarker(latLng)
            val cameraPosition = CameraPosition.Builder()
                .target(latLng)
                .zoom(16f)
                .tilt(45f)
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1200, null)
            val isFirstFix = !hasCenteredOnUser
            hasCenteredOnUser = true
            // 首次定位成功后，在定位处自动扫描一次当前区域
            if (isFirstFix && !hasAutoScanned) {
                hasAutoScanned = true
                scanOverlay.postDelayed({ startAreaScan(latLng) }, 1300)
            }
        }.addOnFailureListener {
            toast(getString(R.string.location_unavailable))
        }
    }

    /** 维护唯一的"我的位置"marker，更新前先移除旧的，避免地图上出现多个定位点。 */
    private fun updateUserMarker(latLng: LatLng) {
        val map = googleMap ?: return
        val existing = userMarker
        if (existing != null) {
            existing.position = latLng
        } else {
            userMarker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(getString(R.string.my_location_title))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        }
    }

    private fun setupClusterManager(map: GoogleMap) {
        val manager = com.google.maps.android.clustering.ClusterManager<
            com.example.lcb.app.streetview.StreetViewClusterItem>(this, map)
        manager.renderer = com.example.lcb.app.streetview.StreetViewClusterRenderer(this, map, manager)
        // 点击单个街景点 → 打开全屏街景
        manager.setOnClusterItemClickListener { item ->
            viewModel.openStreetView(item.spot)
            true
        }
        // 点击聚合点 → 放大地图展开
        manager.setOnClusterClickListener { cluster ->
            val current = map.cameraPosition.zoom
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(cluster.position, current + 2f)
            )
            true
        }
        clusterManager = manager
    }

    private fun renderPanoramaMarkers(spots: List<com.example.lcb.app.streetview.StreetViewSpot>) {
        val manager = clusterManager ?: return
        manager.clearItems()
        manager.addItems(spots.map { com.example.lcb.app.streetview.StreetViewClusterItem(it) })
        manager.cluster()
    }

    /** 地图重建后重新把已发现的街景点喂给聚合管理器。 */
    private fun restorePanoramaMarkers() {
        renderPanoramaMarkers(viewModel.markers.value)
    }

    /**
     * 搜索选中地点后：飞到该坐标，再在该处触发扫描。
     */
    private fun flyToAndScan(target: LatLng) {
        val map = googleMap ?: return
        val zoom = SCAN_TARGET_ZOOM
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(target, zoom),
            700,
            object : GoogleMap.CancelableCallback {
                override fun onFinish() {
                    performScan(target)
                }

                override fun onCancel() {
                    performScan(target)
                }
            }
        )
    }

    /**
     * 点击地图后：以点击点为中心确定一个大范围（bbox），在屏幕对应位置显示
     * 扫描动效，并触发聚合搜索；数据返回后（scanning=false）停止动效并显示点。
     */
    private fun startAreaScan(center: LatLng) {
        val map = googleMap ?: return
        if (viewModel.scanning.value) return

        // 缩放太小时点位看不清：先放大到合适级别并居中，动画结束后再扫描
        if (map.cameraPosition.zoom < SCAN_MIN_ZOOM) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(center, SCAN_TARGET_ZOOM),
                600,
                object : GoogleMap.CancelableCallback {
                    override fun onFinish() {
                        performScan(center)
                    }

                    override fun onCancel() {
                        performScan(center)
                    }
                }
            )
        } else {
            performScan(center)
        }
    }

    private fun performScan(center: LatLng) {
        val map = googleMap ?: return
        if (viewModel.scanning.value) return

        // 以点击点为中心，构造一个大范围方框（约 ±SCAN_RADIUS_METERS）
        val latDelta = SCAN_RADIUS_METERS / 111_320.0
        val lngDelta = SCAN_RADIUS_METERS /
            (111_320.0 * kotlin.math.cos(Math.toRadians(center.latitude)))
        val sw = LatLng(center.latitude - latDelta, center.longitude - lngDelta)
        val ne = LatLng(center.latitude + latDelta, center.longitude + lngDelta)
        val bounds = com.google.android.gms.maps.model.LatLngBounds(sw, ne)

        // 记录地理锚点，相机移动时据此重新投影，保证罩子贴合地图区域
        scanCenterLatLng = center
        scanEdgeLatLng = LatLng(center.latitude, center.longitude + lngDelta)
        updateScanOverlayGeometry(start = true)

        viewModel.searchArea(center, bounds)
    }

    /** 根据地理锚点把扫描罩子重新投影到屏幕坐标。start=true 时启动动效。 */
    private fun updateScanOverlayGeometry(start: Boolean) {
        val map = googleMap ?: return
        val center = scanCenterLatLng ?: return
        val edge = scanEdgeLatLng ?: return
        val centerPx = map.projection.toScreenLocation(center)
        val edgePx = map.projection.toScreenLocation(edge)
        val screenRadius = kotlin.math.hypot(
            (edgePx.x - centerPx.x).toDouble(),
            (edgePx.y - centerPx.y).toDouble()
        ).toFloat()
        if (start) {
            scanOverlay.start(centerPx.x.toFloat(), centerPx.y.toFloat(), screenRadius)
        } else {
            scanOverlay.updateGeometry(centerPx.x.toFloat(), centerPx.y.toFloat(), screenRadius)
        }
    }

    private fun focusOn(target: LatLng) {
        val map = googleMap ?: return
        val cameraPosition = CameraPosition.Builder()
            .target(target)
            .zoom(17f)
            .tilt(45f)
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 800, null)
    }

    private fun toggleMapType() {
        val map = googleMap ?: return
        val typeIcon = findViewById<android.widget.ImageView>(R.id.mapTypeFab)
        if (map.mapType == GoogleMap.MAP_TYPE_NORMAL) {
            map.mapType = GoogleMap.MAP_TYPE_HYBRID
            // 当前是卫星图，按钮显示"地图"图标，提示可切回普通地图
            typeIcon.setImageResource(R.drawable.ic_map_normal)
        } else {
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            // 当前是普通地图，按钮显示"卫星"图标，提示可切到卫星图
            typeIcon.setImageResource(R.drawable.ic_map_satellite)
        }
    }

    private fun cacheLocation(latLng: LatLng) {
        prefs.edit()
            .putFloat(KEY_LAST_LAT, latLng.latitude.toFloat())
            .putFloat(KEY_LAST_LNG, latLng.longitude.toFloat())
            .apply()
    }

    private fun readCachedLocation(): LatLng? {
        if (!prefs.contains(KEY_LAST_LAT) || !prefs.contains(KEY_LAST_LNG)) return null
        val lat = prefs.getFloat(KEY_LAST_LAT, 0f).toDouble()
        val lng = prefs.getFloat(KEY_LAST_LNG, 0f).toDouble()
        return LatLng(lat, lng)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 打开街景前按频率门控展示一次插屏；展示/关闭或失败后都继续打开街景。
     */
    private fun openStreetViewWithAd(intent: android.content.Intent) {
        if (com.example.lcb.app.utils.AdGate.shouldShowOnStreetViewOpen()) {
            loadInterstitial(condition = { true }) { shown: Boolean ->
                if (shown) com.example.lcb.app.utils.AdGate.markInterstitialShown()
                startActivity(intent)
            }
        } else {
            startActivity(intent)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        cancellationTokenSource.cancel()
        super.onDestroy()
    }

    override fun onBackPressed() {
        LcbApp.backLaunchActivity()
    }

    companion object {
        private const val KEY_LAST_LAT = "last_lat"
        private const val KEY_LAST_LNG = "last_lng"
        private const val MIN_ZOOM = 2f
        private const val MAX_ZOOM = 21f
        // 点击地图后扫描的范围半径（米）
        private const val SCAN_RADIUS_METERS = 500.0
        // 缩放低于该级别时，点击扫描前先放大，避免点位看不清
        private const val SCAN_MIN_ZOOM = 14f
        // 点击扫描时若需放大，目标缩放级别
        private const val SCAN_TARGET_ZOOM = 16f
    }
}
