package com.raad.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** شاشات التطبيق الرئيسية */
enum class Screen { Browser, Tabs, Downloads, History, Bookmarks, Settings }

/** تنقل بسيط مبني على حالة Compose بدون مكتبة Navigation لتقليل التعقيد */
object Navigator {
    var current by mutableStateOf(Screen.Browser)
        private set

    fun go(screen: Screen) {
        current = screen
    }
}
