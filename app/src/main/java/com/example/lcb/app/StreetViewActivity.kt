package com.example.lcb.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.lcb.app.utils.applySystemBarInsets
import com.example.lcb.app.utils.updateTopMarginForInset

/**
 * Full-screen immersive Street View viewer backed by the Maps Embed API
 * (free, unlimited usage) loaded inside a WebView.
 */
class StreetViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loadingOverlay: View
    private lateinit var loadingProgress: ProgressBar
    private lateinit var errorOverlay: View
    private lateinit var titleBar: TextView

    private var pageFailed = false
    private var embedUrl: String = ""
    private var isMapillary: Boolean = false
    private var mapillaryId: String? = null
    private val showContentRunnable = Runnable {
        // iframe 可能晚于外层 HTML 失败，执行延迟任务前必须再次确认加载状态。
        if (!pageFailed && !isFinishing && !isDestroyed) showContent()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_street_view)

        webView = findViewById(R.id.webView)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        loadingProgress = findViewById(R.id.loadingProgress)
        errorOverlay = findViewById(R.id.errorOverlay)
        titleBar = findViewById(R.id.titleBar)

        val backButton = findViewById<ImageView>(R.id.backButton)
        configureSystemBarInsets(backButton)

        backButton.setOnClickListener { finish() }
        findViewById<TextView>(R.id.retryButton).setOnClickListener { reload() }

        val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
        val lng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)
        val pano = intent.getStringExtra(EXTRA_PANO)
        val title = intent.getStringExtra(EXTRA_TITLE)
        isMapillary = intent.getBooleanExtra(EXTRA_IS_MAPILLARY, false)
        mapillaryId = intent.getStringExtra(EXTRA_MAPILLARY_ID)

        if (!title.isNullOrBlank()) {
            titleBar.text = title
            titleBar.visibility = View.VISIBLE
        }

        embedUrl = buildEmbedUrl(lat, lng, pano)
        setupWebView()
        reload()
    }

    /**
     * 统一处理街景页的系统栏安全区。
     *
     * 页面保持 edge-to-edge：顶部浮层通过 margin 避让状态栏，街景内容则通过根容器
     * padding 避让底部/侧边导航栏。导航栏 inset 由系统按手势导航或三键导航动态提供，
     * 避免在不同设备形态下使用固定高度造成内容遮挡。
     */
    private fun configureSystemBarInsets(backButton: View) {
        val root = findViewById<View>(R.id.streetViewRoot)
        root.applySystemBarInsets(padStatusBarTop = false) { systemBars ->
            backButton.updateTopMarginForInset(systemBars.statusBarTop, dp(8))
            titleBar.updateTopMarginForInset(systemBars.statusBarTop, dp(8))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
        }
        // 街景使用 WebGL，必须开启硬件加速渲染
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.setBackgroundColor(Color.parseColor("#0E1116"))

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                // 允许加载街景 Embed 本身及其内部资源，其余（如"在 Google 地图中打开"）
                // 交给系统用外部应用打开。
                if (isEmbedUrl(url)) return false
                if (request.hasGesture() && (url.startsWith("http://") || url.startsWith("https://"))) {
                    openExternally(url)
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (!pageFailed) showLoading()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                // 街景在 iframe/脚本中加载，子资源失败也统一显示应用错误页。
                handleWebViewLoadFailure()
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                handleWebViewLoadFailure()
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                // SSL 加载失败不继续绕过证书校验，直接进入统一错误页。
                handler?.cancel()
                handleWebViewLoadFailure()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!pageFailed) {
                    // 外层 HTML 加载完成后，iframe 内的街景仍在渲染，
                    // 留出时间让全景出现，避免过早移除 loading 露出黑屏
                    webView.removeCallbacks(showContentRunnable)
                    webView.postDelayed(showContentRunnable, CONTENT_REVEAL_DELAY_MILLIS)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                loadingProgress.progress = newProgress
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                // "在 Google 地图中打开"等以新窗口(target=_blank)方式打开的链接，
                // 用一个临时 WebView 捕获其目标地址，再交给系统外部应用打开。
                val tempWebView = WebView(this@StreetViewActivity)
                tempWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        request?.url?.toString()?.let { openExternally(it) }
                        tempWebView.destroy()
                        return true
                    }
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = tempWebView
                resultMsg?.sendToTarget()
                return true
            }
        }
    }

    private fun reload() {
        beginWebViewLoad()
        if (isMapillary) {
            val id = mapillaryId
            if (id.isNullOrBlank()) {
                handleWebViewLoadFailure()
                return
            }
            webView.loadDataWithBaseURL(
                "https://www.mapillary.com",
                buildMapillaryHtml(id),
                "text/html",
                "utf-8",
                null
            )
            return
        }

        if (embedUrl.isBlank()) {
            handleWebViewLoadFailure()
            return
        }
        // 用一个全屏 HTML 包裹 iframe 来承载 Embed 街景，
        // 直接把 embed URL 作为顶层文档加载在 WebView 里可能渲染异常（黑屏）。
        webView.loadDataWithBaseURL(
            "https://www.google.com",
            buildHtml(embedUrl),
            "text/html",
            "utf-8",
            null
        )
    }

    private fun buildHtml(url: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
                <style>
                    html, body { margin: 0; padding: 0; height: 100%; background: #0E1116; overflow: hidden; }
                    iframe { border: 0; width: 100%; height: 100%; display: block; }
                </style>
            </head>
            <body>
                <iframe
                    src="$url"
                    allow="accelerometer; gyroscope; fullscreen"
                    referrerpolicy="no-referrer-when-downgrade"
                    allowfullscreen>
                </iframe>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildMapillaryHtml(imageId: String): String {
        val token = BuildConfig.MAPILLARY_TOKEN
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
                <link rel="stylesheet" href="https://unpkg.com/mapillary-js@4.1.2/dist/mapillary.css">
                <style>
                    html, body { margin: 0; padding: 0; height: 100%; background: #0E1116; overflow: hidden; }
                    #mly { width: 100%; height: 100%; }
                </style>
            </head>
            <body>
                <div id="mly"></div>
                <script src="https://unpkg.com/mapillary-js@4.1.2/dist/mapillary.js"></script>
                <script>
                    try {
                        var viewer = new mapillary.Viewer({
                            accessToken: "$token",
                            container: "mly",
                            imageId: "$imageId"
                        });
                        window.addEventListener("resize", function () { viewer.resize(); });
                    } catch (e) {
                        document.body.innerHTML = "";
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    /** 开始一次全新的加载，清除上次失败及延迟显示任务。 */
    private fun beginWebViewLoad() {
        webView.removeCallbacks(showContentRunnable)
        pageFailed = false
        showLoading()
    }

    /** 所有 WebView 加载错误统一收口到应用自己的默认错误页。 */
    private fun handleWebViewLoadFailure() {
        if (pageFailed) return
        pageFailed = true
        webView.removeCallbacks(showContentRunnable)
        webView.stopLoading()
        showError()
    }

    private fun showLoading() {
        loadingOverlay.animate().cancel()
        loadingOverlay.alpha = 1f
        webView.visibility = View.VISIBLE
        loadingOverlay.visibility = View.VISIBLE
        errorOverlay.visibility = View.GONE
        loadingProgress.progress = 0
    }

    private fun showContent() {
        if (pageFailed) return
        webView.visibility = View.VISIBLE
        loadingOverlay.animate().alpha(0f).setDuration(250).withEndAction {
            loadingOverlay.visibility = View.GONE
            loadingOverlay.alpha = 1f
        }.start()
        errorOverlay.visibility = View.GONE
    }

    private fun showError() {
        loadingOverlay.animate().cancel()
        loadingOverlay.alpha = 1f
        loadingOverlay.visibility = View.GONE
        // 隐藏 WebView，避免系统原生错误页在覆盖层切换期间闪现。
        webView.visibility = View.INVISIBLE
        errorOverlay.visibility = View.VISIBLE
    }

    private fun buildEmbedUrl(lat: Double, lng: Double, pano: String?): String {
        val key = BuildConfig.MAPS_API_KEY
        if (key.isBlank()) return ""
        val base = "https://www.google.com/maps/embed/v1/streetview?key=$key"
        return when {
            !pano.isNullOrBlank() -> "$base&pano=$pano"
            !lat.isNaN() && !lng.isNaN() -> "$base&location=$lat,$lng&heading=210&pitch=0&fov=90"
            else -> ""
        }
    }

    private fun isEmbedUrl(url: String): Boolean {
        // 街景查看器（Google Embed / Mapillary）及其依赖资源，保持在 WebView 内加载
        return url.contains("/maps/embed/v1/streetview") ||
            url.contains("google.com/maps/embed") ||
            url.contains("gstatic.com") ||
            url.contains("googleapis.com") ||
            url.contains("ggpht.com") ||
            url.contains("mapillary.com") ||
            url.contains("unpkg.com") ||
            url.startsWith("about:") ||
            url.startsWith("data:") ||
            url.startsWith("blob:")
    }

    private fun openExternally(url: String) {
        try {
            startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            toast(getString(R.string.sv_open_external_failed))
        }
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        webView.removeCallbacks(showContentRunnable)
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        const val EXTRA_PANO = "extra_pano"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_IS_MAPILLARY = "extra_is_mapillary"
        const val EXTRA_MAPILLARY_ID = "extra_mapillary_id"
        private const val CONTENT_REVEAL_DELAY_MILLIS = 900L

        fun newIntent(
            context: android.content.Context,
            lat: Double,
            lng: Double,
            pano: String? = null,
            title: String? = null,
            isMapillary: Boolean = false,
            mapillaryId: String? = null
        ): android.content.Intent {
            return android.content.Intent(context, StreetViewActivity::class.java).apply {
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LNG, lng)
                putExtra(EXTRA_PANO, pano)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_IS_MAPILLARY, isMapillary)
                putExtra(EXTRA_MAPILLARY_ID, mapillaryId)
            }
        }
    }
}
