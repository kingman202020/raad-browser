package com.raad.browser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.raad.browser.MainViewModel
import com.raad.browser.downloads.DownloadRequest
import com.raad.browser.store.HistoryStore
import java.util.UUID

/** تبويب واحد في المتصفح مع حالته القابلة للملاحظة في Compose */
class Tab(val id: String, initialUrl: String) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf("")
    var progress by mutableStateOf(0)
    var isLoading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var loadedOnce = false
}

/**
 * مدير التبويبات: ينشئ ويخزن نسخ WebView لكل تبويب
 * بحيث يبقى محتوى كل تبويب محفوظاً عند التنقل بينها.
 */
object TabManager {

    // الصفحة الرئيسية الافتراضية للمتصفح
    const val HOME_URL = "http://30.10.10.10/"

    private lateinit var appContext: Context

    val tabs = mutableStateListOf<Tab>()

    var currentTabId by mutableStateOf<String?>(null)
        private set

    private val webViews = HashMap<String, WebView>()

    fun init(context: Context) {
        appContext = context.applicationContext
        if (tabs.isEmpty()) newTab()
    }

    fun newTab(url: String = HOME_URL): String {
        val tab = Tab(UUID.randomUUID().toString(), url)
        tabs.add(tab)
        currentTabId = tab.id
        return tab.id
    }

    fun selectTab(id: String) {
        if (tabs.any { it.id == id }) currentTabId = id
    }

    fun closeTab(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        webViews.remove(id)?.destroy()
        tabs.removeAll { it.id == id }
        if (tabs.isEmpty()) {
            newTab()
            return
        }
        if (currentTabId == id) {
            currentTabId = tabs[maxOf(0, index - 1)].id
        }
    }

    fun currentTab(): Tab? = tabs.firstOrNull { it.id == currentTabId }

    fun webView(id: String): WebView? = webViews[id]

    fun currentWebView(): WebView? = currentTabId?.let { webViews[it] }

    /**
     * يعيد WebView موجوداً للتبويب أو ينشئ واحداً جديداً بكامل الإعدادات.
     * يجب استدعاؤها من الخيط الرئيسي (داخل AndroidView factory).
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(tabId: String, vm: MainViewModel): WebView {
        webViews[tabId]?.let { return it }

        val tab = tabs.first { it.id == tabId }
        val webView = WebView(appContext)

        // إصلاح حاسم: فرض أبعاد MATCH_PARENT صراحةً.
        // بدونها يُقاس ارتفاع WebView بـ 0px داخل AndroidView (خصوصاً مع useWideViewPort)،
        // فتنهار كل قواعد CSS النسبية مثل height:100% في صفحات React إلى صفر
        // ولا يظهر إلا العناصر ذات الارتفاع الثابت (مثل الشريط العلوي 52px).
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        // دعم الكوكيز بما فيها كوكيز الطرف الثالث (مثل تسجيل الدخول)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                // الروابط العادية تبقى داخل المتصفح، والروابط الخارجية (intent:, tel:, ...) تفتح بتطبيقاتها
                return if (url.startsWith("http://") || url.startsWith("https://")) {
                    false
                } else {
                    try {
                        appContext.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                    }
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                tab.isLoading = true
                tab.progress = 0
                url?.let { tab.url = it }
                tab.canGoBack = view.canGoBack()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                tab.isLoading = false
                tab.progress = 100
                url?.let {
                    tab.url = it
                    if (it != "about:blank") {
                        HistoryStore.add(it, view.title ?: it)
                    }
                }
                view.title?.let { if (it.isNotBlank()) tab.title = it }
                tab.canGoBack = view.canGoBack()
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                tab.canGoBack = view.canGoBack()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                tab.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                if (!title.isNullOrBlank()) tab.title = title
            }
        }

        // اعتراض طلبات التحميل وإظهار نافذة اختيار الاسم والمجلد
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            val suggested = URLUtil.guessFileName(url, contentDisposition, mimetype)
            vm.downloadRequest = DownloadRequest(
                url = url,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimeType = mimetype,
                suggestedName = suggested
            )
        }

        webViews[tabId] = webView

        // تحميل الصفحة الأولى للتبويب الجديد
        if (!tab.loadedOnce && tab.url.isNotBlank()) {
            tab.loadedOnce = true
            webView.loadUrl(tab.url)
        }

        return webView
    }
}
