package com.raad.browser.downloads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** طلب تحميل قادم من WebView (يحمل المعلومات الأولية قبل موافقة المستخدم) */
data class DownloadRequest(
    val url: String,
    val userAgent: String,
    val contentDisposition: String?,
    val mimeType: String?,
    val suggestedName: String
)

/**
 * عنصر تحميل واحد. الحقول قابلة للملاحظة (Compose State)
 * حتى تتحدث الواجهة مباشرة مع تقدم التحميل.
 */
class DownloadItem(
    val id: String,
    val url: String,
    fileName: String,
    /** URI لمجلد SAF المختار، أو null لمجلد التطبيق الافتراضي */
    val treeUri: String?
) {
    var fileName by mutableStateOf(fileName)
    var savedFileUri by mutableStateOf<String?>(null)
    var totalBytes by mutableStateOf(-1L)
    var downloadedBytes by mutableStateOf(0L)
    var status by mutableStateOf(Status.RUNNING)
    var error by mutableStateOf<String?>(null)

    enum class Status { RUNNING, PAUSED, COMPLETED, FAILED, CANCELED }
}
