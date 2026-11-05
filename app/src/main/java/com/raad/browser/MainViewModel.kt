package com.raad.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.raad.browser.downloads.DownloadRequest

/**
 * ViewModel رئيسي: يحمل طلب التحميل القادم من WebView
 * لعرض نافذة اختيار الاسم والمجلد.
 */
class MainViewModel : ViewModel() {
    var downloadRequest by mutableStateOf<DownloadRequest?>(null)
}
