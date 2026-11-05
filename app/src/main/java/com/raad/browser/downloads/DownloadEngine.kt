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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
 * - **تحميل متعدد الأجزاء للتسريع**: الملفات الكبيرة تُقسم إلى عدة أجزاء
 *   تُحمل بالتوازي عبر ترويسة HTTP Range ثم تُدمج في ملف واحد.
 * - إيقاف مؤقت واستئناف (لكل جزء على حدة، أو للملف كاملاً في الوضع العادي).
 * - رجوع تلقائي للتحميل أحادي الاتصال إذا لم يدعم الخادم التقسيم.
 * - الحفظ في مجلد SAF يختاره المستخدم أو في مجلد التطبيق الافتراضي.
 */
object DownloadEngine {

    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_PARTS = 4                       // أقصى عدد للاتصالات المتوازية
    private const val BYTES_PER_PART = 2L * 1024 * 1024   // جزء جديد لكل 2MB تقريباً
    private const val MIN_SIZE_FOR_PARTS = 2L * 1024 * 1024

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val downloads = mutableStateListOf<DownloadItem>()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val pauseFlags = ConcurrentHashMap<String, AtomicBoolean>()

    fun init(context: Context) {
        app = context.applicationContext
        // تنظيف بقايا أجزاء من جلسات سابقة (إن أُغلق التطبيق أثناء تحميل)
        File(app.filesDir, "parts").deleteRecursively()
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
        // تشغيل الخدمة الأمامية ليستمر التحميل حتى بعد الخروج من التطبيق
        DownloadService.start(app)
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
        DownloadService.start(app)
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
        downloads.firstOrNull { it.id == id }?.let { partsDir(it).deleteRecursively() }
        downloads.removeAll { it.id == id }
    }

    fun clearCompleted() {
        downloads.filter { it.status == DownloadItem.Status.COMPLETED }
            .forEach { remove(it.id) }
    }

    /** فتح الملف المحمل بتطبيق مناسب */
    fun open(context: Context, item: DownloadItem) {
        val uriString = item.savedFileUri ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uriString), mimeFor(item.fileName))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.no_app_to_open), Toast.LENGTH_SHORT).show()
        }
    }

    fun mimeFor(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    // ------------------------------------------------------------------
    // التنفيذ
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

    /**
     * نقطة الدخول: يستكشف الخادم أولاً (طلب Range تجريبي) لمعرفة
     * حجم الملف ودعم التقسيم، ثم يختار وضع التحميل المناسب.
     */
    private suspend fun perform(item: DownloadItem, pauseFlag: AtomicBoolean, tryResume: Boolean) {
        if (!tryResume) partsDir(item).deleteRecursively()

        val probe = openConnection(item.url).apply {
            setRequestProperty("Range", "bytes=0-0")
        }
        probe.connect()
        val supportsRanges = probe.responseCode == HttpURLConnection.HTTP_PARTIAL
        val total = if (supportsRanges) {
            // الشكل المعتاد: "bytes 0-0/12345"
            probe.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
        } else {
            probe.contentLengthLong
        }
        probe.disconnect()

        if (total > 0) item.totalBytes = total

        if (supportsRanges && total >= MIN_SIZE_FOR_PARTS) {
            multiPartDownload(item, pauseFlag, total, tryResume)
        } else {
            singleStreamDownload(item, pauseFlag, tryResume)
        }
    }

    /** بناء اتصال HTTP موحد الإعدادات (User-Agent + كوكيز المتصفح) */
    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(app))
            // تمرير كوكيز المتصفح لدعم التحميل من الصفحات التي تتطلب تسجيل دخول
            CookieManager.getInstance().getCookie(url)?.let { setRequestProperty("Cookie", it) }
        }

    // ------------------------------------------------------------------
    // الوضع أحادي الاتصال (للملفات الصغيرة أو الخوادم بدون دعم Range)
    // ------------------------------------------------------------------

    private fun singleStreamDownload(item: DownloadItem, pauseFlag: AtomicBoolean, tryResume: Boolean) {
        val conn = openConnection(item.url)
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

    // ------------------------------------------------------------------
    // الوضع متعدد الأجزاء (تسريع التحميل باتصالات متوازية)
    // ------------------------------------------------------------------

    private fun partsDir(item: DownloadItem): File = File(app.filesDir, "parts/${item.id}")

    private fun computePartCount(total: Long): Int =
        (total / BYTES_PER_PART).toInt().coerceIn(2, MAX_PARTS)

    private suspend fun multiPartDownload(
        item: DownloadItem,
        pauseFlag: AtomicBoolean,
        total: Long,
        tryResume: Boolean
    ) = coroutineScope {
        val partCount = computePartCount(total)
        val partSize = total / partCount

        // حدود كل جزء: [start, end]
        val ranges = (0 until partCount).map { i ->
            val start = i * partSize
            val end = if (i == partCount - 1) total - 1 else (i + 1) * partSize - 1
            start to end
        }

        val dir = partsDir(item)
        dir.mkdirs()

        // المحمّل سابقاً من كل جزء (للاستئناف بعد إيقاف مؤقت)
        val downloaded = LongArray(partCount) { i ->
            val f = File(dir, "part$i")
            val maxLen = ranges[i].second - ranges[i].first + 1
            when {
                !tryResume || !f.exists() -> 0L
                f.length() > maxLen -> {
                    f.delete(); 0L
                }
                else -> f.length()
            }
        }
        item.downloadedBytes = downloaded.sum()

        val allPartsDone = (0 until partCount).all {
            downloaded[it] >= ranges[it].second - ranges[it].first + 1
        }

        if (!allPartsDone) {
            // تحميل الأجزاء بالتوازي — إن رمى جزء استثناءً تُلغى بقية الأجزاء
            (0 until partCount).map { i ->
                async(Dispatchers.IO) {
                    downloadPart(item, i, ranges[i], downloaded, pauseFlag)
                }
            }.awaitAll()
        }

        // خروج نظيف عند الإيقاف المؤقت: الأجزاء تبقى على القرص للاستئناف
        if (pauseFlag.get()) {
            item.status = DownloadItem.Status.PAUSED
            return@coroutineScope
        }

        // دمج الأجزاء في الملف النهائي ثم حذف الملفات المؤقتة
        mergeParts(item, partCount)
        partsDir(item).deleteRecursively()
        item.status = DownloadItem.Status.COMPLETED
    }

    /** تحميل جزء واحد: bytes=(start+downloaded)-end مع الإلحاق بملف الجزء */
    private fun downloadPart(
        item: DownloadItem,
        index: Int,
        range: Pair<Long, Long>,
        downloaded: LongArray,
        pauseFlag: AtomicBoolean
    ) {
        val (start, end) = range
        val targetLen = end - start + 1
        if (downloaded[index] >= targetLen) return

        val partFile = File(partsDir(item), "part$index")
        val conn = openConnection(item.url).apply {
            setRequestProperty("Range", "bytes=${start + downloaded[index]}-$end")
        }
        conn.connect()
        val code = conn.responseCode
        if (code !in 200..299) throw IOException("HTTP $code (part $index)")

        // خادم تجاهل Range رغم نجاح الاستكشاف: نعيد الجزء من بدايته
        val output = if (code == HttpURLConnection.HTTP_PARTIAL) {
            FileOutputStream(partFile, true)
        } else {
            downloaded[index] = 0
            FileOutputStream(partFile, false)
        }

        output.use { out ->
            conn.inputStream.use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (downloaded[index] < targetLen) {
                    if (pauseFlag.get()) return   // إيقاف مؤقت نظيف
                    val read = input.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    downloaded[index] += read
                    // تحديث التقدم الإجمالي (مجموع كل الأجزاء)
                    item.downloadedBytes = downloaded.sum()
                }
                out.flush()
            }
        }
        if (downloaded[index] < targetLen) throw IOException("انقطع الجزء $index قبل اكتماله")
    }

    /** دمج الأجزاء المحملة بالترتيب في الملف النهائي (SAF أو مجلد التطبيق) */
    private fun mergeParts(item: DownloadItem, partCount: Int) {
        openOutput(item, append = false).use { out ->
            val buffer = ByteArray(BUFFER_SIZE)
            for (i in 0 until partCount) {
                File(partsDir(item), "part$i").inputStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                    }
                }
            }
            out.flush()
        }
    }

    // ------------------------------------------------------------------
    // الكتابة إلى الوجهة
    // ------------------------------------------------------------------

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
}
