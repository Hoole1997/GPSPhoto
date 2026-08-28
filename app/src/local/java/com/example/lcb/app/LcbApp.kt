package com.example.lcb.app

/**
 * Local 测试渠道的 Launcher Application 适配层。
 *
 * 本文件只维护 Local Launcher SDK 的 pre-R8 API 映射，避免混淆符号进入公共业务层。
 */
class LcbApp : com.leafmotivation.quizguessoncolor.Iej9ieio6r89e7ya() {

    private val delegate = LcbAppDelegate(this)

    override fun onCreate() {
        super.onCreate()
        LauncherSdkGateway.install(
            // Local SDK: openMainActivity -> prodailysmartmemory
            returnToLauncher = { litesafesafelocker() },
            // Local SDK: appShowAd -> maxquicklitememory(Activity, String, Int)
            beforeShowAd = { activity -> maxquicklitememory(activity, "", -1) },
        )
        // Local SDK: setNetworkEventListener -> maxquicklitememory(Function6)
        delegate.onCreate { listener -> maxquicklitememory(listener) }
    }

    @Suppress("UNCHECKED_CAST")
    override fun scanautofile(): Class<in Any>? {
        // Local SDK: getLauncherActivityClass -> metaautovault
        return delegate.launcherActivityClass() as Class<in Any>?
    }

    @Suppress("UNCHECKED_CAST")
    override fun scansafeloc(): List<Class<in Any>?>? {
        // Local SDK: getAppActivityClassArray -> convertsafepower
        return delegate.protectedActivityClasses() as List<Class<in Any>?>?
    }
}
