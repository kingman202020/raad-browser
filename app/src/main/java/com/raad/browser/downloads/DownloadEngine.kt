package com.raad.browser.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.WebSettings
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.raad.browser.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * محرك التحميلات المخصص:
 * - يعمل على Coroutines في الخلفية.
 * - يدعم الإيقاف المؤقت والاستئناف عبر ترويسة HTTP Range.
 * - يدعم الحفظ في مجلد يختاره المستخدم عبر SAF (بدون أذونات تخزين في أندرويد الحديث)
 *   أو في مجلد التطبيق الافتراضي.
 */
object DownloadEngine {

    private const val BUFFER_SIZE = 64 * 1024

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val downloads = mutableStateListOf<DownloadItem>()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val pauseFlags = ConcurrentHashMap<String, AtomicBoolean>()

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** بدء تحميل جديد بعد موافقة المستخدم من النافذة المنبثقة */
    fun enqueue(url: String, fileName: String, treeUri: String?) {
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = url,
            fileName = fileName,
            treeUri = treeUri
        )
        downloads.add(0, item)
        start(item, tryResume = false)
    }

    fun pause(id: String) {
        pauseFlags[id]?.set(true)
    }

    fun resume(id: String) {
        val item = downloads.firstOrNull { it.id == id } ?: return
        if (item.status != DownloadItem.Status.PAUSED && item.status != DownloadItem.Status.FAILED) return
        item.status = DownloadItem.Status.RUNNING
        item.error = null
        start(item, tryResume = true)
    }

    fun cancel(id: String) {
        pauseFlags[id]?.set(true)
        jobs[id]?.cancel()
        downloads.firstOrNull { it.id == id }?.let {
            if (it.status != DownloadItem.Status.COMPLETED) it.status = DownloadItem.Status.CANCELED
        }
    }

    fun remove(id: String) {
        pauseFlags[id]?.set(true)
        jobs[id]?.cancel()
        downloads.removeAll { it.id == id }
    }

    fun clearCompleted() {
        downloads.filter { it.status == DownloadItem.Status.COMPLETED }
            .forEach { remove(it.id) }
    }

    /** فتح الملف المحمل بتطبيق مناسب */
    fun open(context: Context, item: DownloadItem) {
        val uriString = item.savedFileUri ?: return
        val mime = mimeFor(item.fileName)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uriString), mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.no_app_to_open), Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------

    private fun start(item: DownloadItem, tryResume: Boolean) {
        val flag = AtomicBoolean(false)
        pauseFlags[item.id] = flag
        jobs[item.id] = scope.launch {
            try {
                perform(item, flag, tryResume)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (item.status != DownloadItem.Status.PAUSED &&
                    item.status != DownloadItem.Status.CANCELED
                ) {
                    item.status = DownloadItem.Status.FAILED
                    item.error = e.message
                }
            }
        }
    }

    private fun perform(item: DownloadItem, pauseFlag: AtomicBoolean, tryResume: Boolean) {
        val conn = (URL(item.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(app))
            // تمرير كوكيز المتصفح لدعم التحميل من الصفحات التي تتطلب تسجيل دخول
            CookieManager.getInstance().getCookie(item.url)?.let {
                setRequestProperty("Cookie", it)
            }
        }

        // طلب استئناف من آخر بايت محمل
        if (tryResume && item.downloadedBytes > 0) {
            conn.setRequestProperty("Range", "bytes=${item.downloadedBytes}-")
        }

        conn.connect()
        val code = conn.responseCode
        if (code !in 200..299) throw IOException("HTTP $code")

        val append = tryResume && code == HttpURLConnection.HTTP_PARTIAL && item.downloadedBytes > 0
        if (append) {
            // الخادم يدعم الاستئناف: الإجمالي = المحمل + المتبقي
            item.totalBytes = item.downloadedBytes + conn.contentLengthLong
        } else {
            // الخادم لا يدعم الاستئناف أو أول تحميل: نبدأ من الصفر
            item.downloadedBytes = 0
            item.totalBytes = conn.contentLengthLong
        }

        openOutput(item, append).use { output ->
            conn.inputStream.use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    if (pauseFlag.get()) {
                        item.status = DownloadItem.Status.PAUSED
                        return
                    }
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    item.downloadedBytes += read
                }
                output.flush()
            }
        }
        item.status = DownloadItem.Status.COMPLETED
    }

    /**
     * فتح مجرى الكتابة للملف الهدف:
     * - SAF: عبر DocumentFile مع وضع الإلحاق "wa" عند الاستئناف.
     * - مجلد التطبيق: FileOutputStream مع توليد اسم فريد عند التعارض.
     */
    private fun openOutput(item: DownloadItem, append: Boolean): OutputStream {
        val tree = item.treeUri
        return if (tree != null) {
            val root = DocumentFile.fromTreeUri(app, Uri.parse(tree))
                ?: throw IOException("لا يمكن الوصول إلى المجلد المختار")
            val doc = if (append) {
                root.findFile(item.fileName)
                    ?: throw IOException("الملف الجزئي غير موجود لاستئناف التحميل")
            } else {
                // SAF يعيد تسمية الملف تلقائياً إذا كان الاسم موجوداً
                root.createFile("application/octet-stream", item.fileName)
                    ?: throw IOException("تعذر إنشاء الملف في المجلد المختار")
            }
            doc.name?.let { item.fileName = it }
            item.savedFileUri = doc.uri.toString()
            app.contentResolver.openOutputStream(doc.uri, if (append) "wa" else "w")
                ?: throw IOException("تعذر فتح الملف للكتابة")
        } else {
            val dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: app.filesDir
            if (!append) {
                val file = uniqueFile(dir, item.fileName)
                item.fileName = file.name
                item.savedFileUri = FileProvider
                    .getUriForFile(app, app.packageName + ".fileprovider", file)
                    .toString()
                FileOutputStream(file, false)
            } else {
                FileOutputStream(File(dir, item.fileName), true)
            }
        }
    }

    /** توليد اسم ملف فريد: name.ext ثم name (1).ext وهكذا */
    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (file.exists()) {
            val candidate = if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext"
            file = File(dir, candidate)
            i++
        }
        return file
    }

    private fun mimeFor(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }
}
