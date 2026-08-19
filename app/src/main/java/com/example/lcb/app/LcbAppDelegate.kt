package com.example.lcb.app

import android.app.Application
import com.blankj.utilcode.util.LogUtils
import com.example.lcb.app.ad.LcbAdInitializer
import com.google.android.gms.maps.MapsInitializer
import net.corekit.metrics.adjust.AdjustTracker

/** 归因 SDK 的稳定回调类型，避免公共初始化逻辑感知渠道 SDK 的混淆类。 */
internal typealias AttributionListener = (
    isOrganic: Boolean,
    network: String,
    campaign: String,
    adgroup: String,
    creative: String,
    jsonResponse: String,
) -> Unit

/**
 * 承载所有渠道共用的 Application 初始化逻辑。
 *
 * 各渠道的 LcbApp 只维护对应 Launcher SDK 的 pre-R8 符号映射；地图、广告和归因
 * 初始化集中在这里，避免渠道文件复制后发生逻辑漂移。
 */
internal class LcbAppDelegate(
    private val application: Application,
) {

    fun onCreate(registerAttributionListener: (AttributionListener) -> Unit) {
        // 提前请求最新地图渲染器；回调不承载业务状态。
        MapsInitializer.initialize(
            application.applicationContext,
            MapsInitializer.Renderer.LATEST,
        ) { /* renderer ready, no-op */ }
        LcbAdInitializer.initialize(application)
        registerAttributionListener { isOrganic, network, campaign, adgroup, creative, jsonResponse ->
            AdjustTracker.init(
                context = application.applicationContext,
                network = network,
                campaign = campaign,
                adgroup = adgroup,
                creative = creative,
                jsonResponse = jsonResponse,
            )
            LogUtils.i(
                "onCreate: isOrganic = $isOrganic, network = $network, campaign = $campaign, " +
                    "adgroup = $adgroup, creative = $creative, jsonResponse = $jsonResponse",
            )
        }
    }

    fun launcherActivityClass(): Class<*> = MainActivity::class.java

    /** Launcher SDK 需要保护并识别的全部应用 Activity。 */
    fun protectedActivityClasses(): List<Class<*>> = listOf(
        MainActivity::class.java,
        StreetViewActivity::class.java,
        SearchActivity::class.java,
        SettingsActivity::class.java,
        AboutActivity::class.java,
    )
}
