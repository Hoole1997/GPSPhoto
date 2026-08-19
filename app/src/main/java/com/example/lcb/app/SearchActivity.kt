package com.example.lcb.app

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lcb.app.streetview.PlaceResult
import com.example.lcb.app.streetview.PlaceResultAdapter
import com.example.lcb.app.streetview.RecommendAdapter
import com.example.lcb.app.streetview.SearchViewModel
import com.example.lcb.app.utils.AdSlotPositions
import com.example.lcb.app.utils.applySystemBarInsets
import com.example.lcb.app.utils.loadNative
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.launch

/**
 * Full-screen place search backed by Nominatim (OpenStreetMap, free).
 * Returns the chosen coordinate to the caller, which then flies the map there
 * and triggers an area scan.
 */
class SearchActivity : AppCompatActivity() {

    private val viewModel: SearchViewModel by viewModels()

    private lateinit var searchInput: EditText
    private lateinit var clearButton: View
    private lateinit var idleContainer: NestedScrollView
    private lateinit var resultList: RecyclerView
    private lateinit var historyList: RecyclerView
    private lateinit var recommendList: RecyclerView
    private lateinit var historyHeader: View
    private lateinit var stateText: TextView
    private lateinit var loading: ProgressBar

    private val resultAdapter = PlaceResultAdapter(isHistory = false) { onPlaceChosen(it) }
    private val historyAdapter = PlaceResultAdapter(isHistory = true) { onPlaceChosen(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_search)

        // 普通内容页整体避让状态栏，以及手势/三键导航栏。
        findViewById<View>(R.id.searchRoot).applySystemBarInsets()

        // 接收来自地图的可见区域，用于偏向搜索结果
        val biasBounds = readBiasFromIntent()
        viewModel.setBias(biasBounds)

        searchInput = findViewById(R.id.searchInput)
        clearButton = findViewById(R.id.clearButton)
        idleContainer = findViewById(R.id.idleContainer)
        resultList = findViewById(R.id.resultList)
        historyList = findViewById(R.id.historyList)
        recommendList = findViewById(R.id.recommendList)
        historyHeader = findViewById(R.id.historyHeader)
        stateText = findViewById(R.id.stateText)
        loading = findViewById(R.id.loading)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        resultList.layoutManager = LinearLayoutManager(this)
        resultList.adapter = resultAdapter
        historyList.layoutManager = LinearLayoutManager(this)
        historyList.adapter = historyAdapter
        recommendList.layoutManager = LinearLayoutManager(this)
        recommendList.adapter = RecommendAdapter(viewModel.recommendations) { spot ->
            returnLocation(spot.position.latitude, spot.position.longitude)
        }

        findViewById<View>(R.id.useMyLocation).setOnClickListener {
            // 返回空坐标，MainActivity 用当前定位处理
            setResult(RESULT_OK, intent.putExtra(EXTRA_USE_MY_LOCATION, true))
            finish()
        }

        findViewById<TextView>(R.id.clearHistory).setOnClickListener {
            viewModel.clearHistory()
        }

        clearButton.setOnClickListener {
            searchInput.setText("")
        }

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val text = s?.toString().orEmpty()
                clearButton.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
                viewModel.onQueryChanged(text)
            }
        })

        searchInput.setOnEditorActionListener { _, _, _ ->
            viewModel.onQueryChanged(searchInput.text.toString())
            true
        }

        observeState()
        searchInput.requestFocus()

        // 搜索页空闲态展示一条原生广告（频率自然、不打扰搜索）
        loadNative(
            positionName = AdSlotPositions.NA_SEARCH_IDLE_BOTTOM,
            container = findViewById(R.id.adContainer)
        )
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: SearchViewModel.SearchState) {
        // 所有搜索状态共享同一个 FrameLayout；切换前先关闭全部状态层，保证任意
        // 状态迁移（尤其是“结果 → 清空输入”）都只会留下一个可见内容层。
        hideAllContentLayers()

        when (state) {
            is SearchViewModel.SearchState.History -> {
                idleContainer.visibility = View.VISIBLE
                historyAdapter.submitList(state.items)
                historyHeader.visibility = if (state.items.isEmpty()) View.GONE else View.VISIBLE
                historyList.visibility = if (state.items.isEmpty()) View.GONE else View.VISIBLE
            }
            SearchViewModel.SearchState.Loading -> {
                loading.visibility = View.VISIBLE
            }
            is SearchViewModel.SearchState.Results -> {
                resultList.visibility = View.VISIBLE
                resultAdapter.submitList(state.items)
            }
            SearchViewModel.SearchState.Empty -> {
                stateText.visibility = View.VISIBLE
                stateText.text = getString(R.string.search_no_result)
            }
            is SearchViewModel.SearchState.Error -> {
                stateText.visibility = View.VISIBLE
                stateText.text = state.message ?: getString(R.string.search_error)
            }
        }
    }

    /** 重置互斥内容层，避免上一个搜索状态残留在当前状态上方。 */
    private fun hideAllContentLayers() {
        idleContainer.visibility = View.GONE
        resultList.visibility = View.GONE
        stateText.visibility = View.GONE
        loading.visibility = View.GONE
    }

    private fun onPlaceChosen(place: PlaceResult) {
        viewModel.addHistory(place)
        returnLocation(place.position.latitude, place.position.longitude)
    }

    private fun returnLocation(lat: Double, lng: Double) {
        setResult(
            RESULT_OK,
            intent.putExtra(EXTRA_LAT, lat).putExtra(EXTRA_LNG, lng)
        )
        finish()
    }

    private fun readBiasFromIntent(): LatLngBounds? {
        val swLat = intent.getDoubleExtra(EXTRA_BIAS_SW_LAT, Double.NaN)
        val swLng = intent.getDoubleExtra(EXTRA_BIAS_SW_LNG, Double.NaN)
        val neLat = intent.getDoubleExtra(EXTRA_BIAS_NE_LAT, Double.NaN)
        val neLng = intent.getDoubleExtra(EXTRA_BIAS_NE_LNG, Double.NaN)
        if (swLat.isNaN() || swLng.isNaN() || neLat.isNaN() || neLng.isNaN()) return null
        return LatLngBounds(LatLng(swLat, swLng), LatLng(neLat, neLng))
    }

    companion object {
        const val EXTRA_LAT = "result_lat"
        const val EXTRA_LNG = "result_lng"
        const val EXTRA_USE_MY_LOCATION = "use_my_location"
        const val EXTRA_BIAS_SW_LAT = "bias_sw_lat"
        const val EXTRA_BIAS_SW_LNG = "bias_sw_lng"
        const val EXTRA_BIAS_NE_LAT = "bias_ne_lat"
        const val EXTRA_BIAS_NE_LNG = "bias_ne_lng"

        fun newIntent(context: android.content.Context, bias: LatLngBounds?): android.content.Intent {
            return android.content.Intent(context, SearchActivity::class.java).apply {
                if (bias != null) {
                    putExtra(EXTRA_BIAS_SW_LAT, bias.southwest.latitude)
                    putExtra(EXTRA_BIAS_SW_LNG, bias.southwest.longitude)
                    putExtra(EXTRA_BIAS_NE_LAT, bias.northeast.latitude)
                    putExtra(EXTRA_BIAS_NE_LNG, bias.northeast.longitude)
                }
            }
        }
    }
}
