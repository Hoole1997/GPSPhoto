package com.example.lcb.app.utils

/**
 * Simple frequency gate for interstitial ads so they show at a reasonable
 * cadence — not on every action (too aggressive) and not rarely (too few).
 */
object AdGate {

    private const val MIN_INTERVAL_MS = 60_000L // 距上次插屏至少 60 秒
    private const val SHOW_EVERY_N = 2          // 每第 2 次触发才考虑展示

    private var lastInterstitialAt = 0L
    private var streetViewOpenCount = 0

    /** 打开街景时调用，返回是否应展示插屏。 */
    fun shouldShowOnStreetViewOpen(): Boolean {
        streetViewOpenCount++
        if (streetViewOpenCount % SHOW_EVERY_N != 0) return false
        val now = System.currentTimeMillis()
        return now - lastInterstitialAt >= MIN_INTERVAL_MS
    }

    /** 插屏实际展示后调用，刷新时间戳。 */
    fun markInterstitialShown() {
        lastInterstitialAt = System.currentTimeMillis()
    }
}
