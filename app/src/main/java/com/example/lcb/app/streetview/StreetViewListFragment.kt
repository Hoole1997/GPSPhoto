package com.example.lcb.app.streetview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lcb.app.R
import kotlinx.coroutines.launch

/**
 * One tab in the bottom sheet. type = TYPE_HOT or TYPE_NEARBY.
 */
class StreetViewListFragment : Fragment(R.layout.fragment_street_view_list) {

    private val viewModel: StreetViewViewModel by activityViewModels()
    private val nearbyAdapter = StreetViewAdapter { spot -> viewModel.openStreetView(spot) }
    private val hotAdapter = HotSpotAdapter { spot -> viewModel.openStreetView(spot) }

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var loadingView: ProgressBar

    private val type: Int get() = arguments?.getInt(ARG_TYPE) ?: TYPE_NEARBY

    /** 暴露页面类型（即 ViewPager 中的位置）。 */
    fun pageType(): Int = type

    /** 供宿主在切页时设置：仅当前可见页参与 BottomSheet 的嵌套滚动联动。 */
    fun setNestedScrollActive(active: Boolean) {
        if (::recyclerView.isInitialized) {
            recyclerView.isNestedScrollingEnabled = active
        }
    }

    /** 自检：找到父 ViewPager2，仅当本页为当前页时启用嵌套滚动。 */
    private fun syncNestedScrollWithPager() {
        var p = view?.parent
        while (p != null && p !is androidx.viewpager2.widget.ViewPager2) {
            p = (p as? View)?.parent
        }
        val pager = p as? androidx.viewpager2.widget.ViewPager2
        if (pager != null) {
            setNestedScrollActive(pager.currentItem == type)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.recyclerView)
        emptyView = view.findViewById(R.id.emptyView)
        loadingView = view.findViewById(R.id.loadingView)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        if (type == TYPE_HOT) {
            recyclerView.adapter = hotAdapter
            observeHot()
        } else {
            recyclerView.adapter = nearbyAdapter
            observeNearby()
        }

        // 根据本页是否为当前可见页，初始化嵌套滚动联动状态
        recyclerView.post { syncNestedScrollWithPager() }

        // 列表底部留出导航栏高度，避免最后一项被遮挡
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.listBottomInset.collect { bottom ->
                    recyclerView.setPadding(
                        recyclerView.paddingLeft,
                        recyclerView.paddingTop,
                        recyclerView.paddingRight,
                        bottom
                    )
                }
            }
        }
    }

    private fun observeHot() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.hot.collect { spots ->
                    loadingView.visibility = View.GONE
                    hotAdapter.submitList(spots)
                    showEmpty(spots.isEmpty(), getString(R.string.street_view_none))
                }
            }
        }
    }

    private fun observeNearby() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.nearby.collect { state ->
                    when (state) {
                        StreetViewViewModel.ListState.Idle -> {
                            loadingView.visibility = View.GONE
                            nearbyAdapter.submitList(emptyList())
                            showEmpty(true, getString(R.string.street_view_nearby_idle))
                        }
                        StreetViewViewModel.ListState.Loading -> {
                            loadingView.visibility = View.VISIBLE
                            emptyView.visibility = View.GONE
                        }
                        is StreetViewViewModel.ListState.Success -> {
                            loadingView.visibility = View.GONE
                            nearbyAdapter.submitList(state.spots)
                            showEmpty(state.spots.isEmpty(), getString(R.string.street_view_none))
                        }
                        is StreetViewViewModel.ListState.Error -> {
                            loadingView.visibility = View.GONE
                            nearbyAdapter.submitList(emptyList())
                            showEmpty(true, state.message ?: getString(R.string.street_view_error))
                        }
                    }
                }
            }
        }
    }

    private fun showEmpty(show: Boolean, message: String) {
        emptyView.text = message
        emptyView.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    companion object {
        const val TYPE_HOT = 0
        const val TYPE_NEARBY = 1
        private const val ARG_TYPE = "type"

        fun newInstance(type: Int): StreetViewListFragment {
            return StreetViewListFragment().apply {
                arguments = Bundle().apply { putInt(ARG_TYPE, type) }
            }
        }
    }
}
