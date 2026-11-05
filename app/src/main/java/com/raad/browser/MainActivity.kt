package com.raad.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raad.browser.browser.BrowserScreen
import com.raad.browser.browser.TabManager
import com.raad.browser.browser.TabsScreen
import com.raad.browser.downloads.DownloadEngine
import com.raad.browser.downloads.DownloadsScreen
import com.raad.browser.settings.SettingsScreen
import com.raad.browser.store.BookmarkStore
import com.raad.browser.store.BookmarksScreen
import com.raad.browser.store.HistoryScreen
import com.raad.browser.store.HistoryStore
import com.raad.browser.ui.theme.RaadBrowserTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // تهيئة الوحدات الأساسية
        Prefs.init(applicationContext)
        HistoryStore.init(applicationContext)
        BookmarkStore.init(applicationContext)
        DownloadEngine.init(applicationContext)
        TabManager.init(applicationContext)

        setContent {
            RaadBrowserTheme {
                // فرض اتجاه RTL عندما تكون لغة الواجهة العربية
                val isArabic = LocalConfiguration.current.locales[0]?.language == "ar"
                CompositionLocalProvider(
                    LocalLayoutDirection provides
                        if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .imePadding()
                    ) {
                        RaadApp()
                    }
                }
            }
        }
    }
}

/**
 * جذر واجهة التطبيق: يبدل بين الشاشات حسب Navigator
 * ويعالج زر الرجوع (رجوع داخل صفحات الويب أولاً).
 */
@Composable
fun RaadApp(vm: MainViewModel = viewModel()) {
    val tab = TabManager.currentTab()
    val canWebGoBack = Navigator.current == Screen.Browser && tab?.canGoBack == true

    BackHandler(enabled = Navigator.current != Screen.Browser || canWebGoBack) {
        if (Navigator.current == Screen.Browser) {
            TabManager.currentWebView()?.goBack()
        } else {
            Navigator.go(Screen.Browser)
        }
    }

    when (Navigator.current) {
        Screen.Browser -> BrowserScreen(vm)
        Screen.Tabs -> TabsScreen()
        Screen.Downloads -> DownloadsScreen()
        Screen.History -> HistoryScreen()
        Screen.Bookmarks -> BookmarksScreen()
        Screen.Settings -> SettingsScreen()
    }
}
