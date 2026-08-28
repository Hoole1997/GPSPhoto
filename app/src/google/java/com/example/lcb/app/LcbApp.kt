package com.example.lcb.app

/**
 * Google 正式渠道的 Launcher Application 适配层。
 *
 * 本文件只维护正式 Launcher SDK 的 pre-R8 API 映射。正式 SDK 坐标更新后，只需在
 * google source set 内同步混淆符号，不影响 Local 渠道与公共业务初始化。
 */
class LcbApp : com.gpsphoto.locationmap.tool.Odo8klyq5p4gfd76igpq() {

    private val delegate = LcbAppDelegate(this)

    override fun onCreate() {
        super.onCreate()
        LauncherSdkGateway.install(
            // 正式 SDK: openMainActivity -> scansafeloc
            returnToLauncher = { checkkit() },
            // Local SDK: appShowAd -> maxquicklitememory(Activity, String, Int)
            beforeShowAd = { activity -> autoscancompass(activity, "", -1) },
        )
        delegate.onCreate { listener -> autoscancompass(listener) }
    }

    @Suppress("UNCHECKED_CAST")
    override fun unlockqr(): Class<in Any>? {
        // 正式 SDK: getLauncherActivityClass -> smartbackuptoolsignal
        return delegate.launcherActivityClass() as Class<in Any>?
    }

    @Suppress("UNCHECKED_CAST")
    override fun toolcache(): List<Class<in Any>?>? {
        // 正式 SDK: getAppActivityClassArray -> prodailysmartmemory
        return delegate.protectedActivityClasses() as List<Class<in Any>?>?
    }
}
