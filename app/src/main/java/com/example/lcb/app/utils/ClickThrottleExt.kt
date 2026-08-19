package com.example.lcb.app.utils

/** 防重复点击的默认间隔。 */
const val DEFAULT_CLICK_THROTTLE_MILLIS = 800L

/**
 * 将单参数点击回调转换为“时间窗口内只响应第一次”的回调。
 *
 * 扩展不依赖具体 View，因此 RecyclerView item、地图 marker 和导航请求可以复用
 * 同一种防重策略。多个入口需要共享时间窗口时，应复用同一个返回回调实例。
 */
fun <T> ((T) -> Unit).throttleFirst(
    intervalMillis: Long = DEFAULT_CLICK_THROTTLE_MILLIS,
    clockMillis: () -> Long = { System.nanoTime() / 1_000_000L }
): (T) -> Unit {
    require(intervalMillis >= 0L) { "intervalMillis must not be negative" }

    var lastAcceptedAt: Long? = null
    return { value ->
        val now = clockMillis()
        val previous = lastAcceptedAt
        if (previous == null || now - previous >= intervalMillis) {
            lastAcceptedAt = now
            this(value)
        }
    }
}
