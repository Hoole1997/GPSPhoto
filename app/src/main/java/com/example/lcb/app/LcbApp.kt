package com.example.lcb.app

import com.blankj.utilcode.util.LogUtils
import com.example.lcb.app.ad.LcbAdInitializer
import com.google.android.gms.maps.MapsInitializer
import net.corekit.metrics.adjust.AdjustTracker

class LcbApp : com.gpsphoto.locationmap.tool.Odo8klyq5p4gfd76igpq() {

    companion object {

        var lcbApp: LcbApp? = null

        fun backLaunchActivity() {
            lcbApp?.checkdoc()
        }
    }

    override fun onCreate() {
        super.onCreate()
        lcbApp = this
        // 提前请求最新的地图渲染器（GPU 矢量渲染），平移缩放更顺滑
        MapsInitializer.initialize(
            applicationContext,
            MapsInitializer.Renderer.LATEST
        ) { /* renderer ready, no-op */ }
        LcbAdInitializer.initialize(this)
        this.autoscancompass {isOrganic, network, campaign, adgroup, creative, jsonResponse ->
            AdjustTracker.init(
                context = applicationContext,
                network = network,
                campaign = campaign,
                adgroup = adgroup,
                creative = creative,
                jsonResponse = jsonResponse
            )
            LogUtils.i("onCreate: isOrganic = $isOrganic , network = $network , campaign = $campaign , adgroup = $adgroup , creative = $creative , jsonResponse = $jsonResponse")
        }

    }

    override fun toolcache(): Class<in Any>? {
        return MainActivity::class.java as Class<in Any>?
    }

    override fun organizesafevault(): List<Class<in Any>?>? {
        return listOf(
            MainActivity::class.java,
            StreetViewActivity::class.java,
            SearchActivity::class.java,
            SettingsActivity::class.java,
            AboutActivity::class.java
        ) as List<Class<in Any>?>?
    }

}
