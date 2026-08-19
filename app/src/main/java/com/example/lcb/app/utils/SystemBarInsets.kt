package com.example.lcb.app.utils

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** 当前设备实际的状态栏和导航栏安全区，单位为像素。 */
data class SystemBarInsets(
    val statusBarTop: Int,
    val navigationBarLeft: Int,
    val navigationBarRight: Int,
    val navigationBarBottom: Int
)

/**
 * 为页面根视图统一安装系统栏适配。
 *
 * 普通内容页使用默认参数即可；沉浸式页面可以关闭对应 padding，并通过
 * [onInsetsChanged] 只调整需要避让系统栏的浮层或底部控件。
 */
fun View.applySystemBarInsets(
    padStatusBarTop: Boolean = true,
    padNavigationBars: Boolean = true,
    onInsetsChanged: (SystemBarInsets) -> Unit = {}
) {
    // 始终基于初始 padding 计算，避免系统栏变化或重复分发时累加。
    val initialPaddingLeft = paddingLeft
    val initialPaddingTop = paddingTop
    val initialPaddingRight = paddingRight
    val initialPaddingBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
        val navigationBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
        val resolvedInsets = SystemBarInsets(
            statusBarTop = statusBars.top,
            navigationBarLeft = navigationBars.left,
            navigationBarRight = navigationBars.right,
            navigationBarBottom = navigationBars.bottom
        )

        view.setPadding(
            initialPaddingLeft + if (padNavigationBars) resolvedInsets.navigationBarLeft else 0,
            initialPaddingTop + if (padStatusBarTop) resolvedInsets.statusBarTop else 0,
            initialPaddingRight + if (padNavigationBars) resolvedInsets.navigationBarRight else 0,
            initialPaddingBottom + if (padNavigationBars) resolvedInsets.navigationBarBottom else 0
        )
        onInsetsChanged(resolvedInsets)
        windowInsets
    }
}

/** 使用系统栏高度更新顶部外边距，同时保留页面定义的视觉间距。 */
fun View.updateTopMarginForInset(topInset: Int, spacing: Int) {
    (layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
        params.topMargin = topInset + spacing
        layoutParams = params
    }
}
