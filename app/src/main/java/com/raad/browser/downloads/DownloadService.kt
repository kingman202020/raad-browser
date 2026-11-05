package com.raad.browser.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.raad.browser.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * خدمة أمامية (Foreground Service) تبقي عملية التطبيق حية
 * حتى تستمر التحميلات حتى لو خرج المستخدم من المتصفح.
 *
 * - تعرض إشعاراً دائماً في شريط المهام مع نسبة التقدم.
 * - عند اكتمال أي تحميل: اهتزاز خفيف + نافذة Toast + إشعار قابل للنقر لفتح الملف.
 * - تتوقف تلقائياً عندما لا تبقى تحميلات نشطة.
 */
class DownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val FOREGROUND_ID = 1001

        /** تشغيل الخدمة (تُستدعى عند بدء أو استئناف أي تحميل) */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadService::class.java)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    /** آخر حالة معروفة لكل تحميل لرصد لحظة الاكتمال */
    private val lastStatus = ConcurrentHashMap<String, DownloadItem.Status>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // تثبيت الحالات الحالية حتى لا تُحسب التحميلات القديمة المكتملة كأحداث جديدة
        DownloadEngine.downloads.forEach { lastStatus.putIfAbsent(it.id, it.status) }

        // يجب استدعاء startForeground فوراً (خلال 5 ثوانٍ من startForegroundService)
        startForeground(FOREGROUND_ID, buildProgressNotification())
        startWatcher()
        return START_STICKY
    }

    override fun onDestroy() {
        watcher?.cancel()
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------

    /** مراقبة دورية لقائمة التحميلات: تحديث الإشعار ورصد الاكتمال */
    private fun startWatcher() {
        if (watcher?.isActive == true) return
        watcher = scope.launch {
            while (isActive) {
                val items = DownloadEngine.downloads.toList()

                // رصد انتقال أي تحميل إلى حالة "مكتمل"
                for (item in items) {
                    val previous = lastStatus[item.id]
                    if (previous != item.status) {
                        if (item.status == DownloadItem.Status.COMPLETED) {
                            onDownloadCompleted(item)
                        }
                        lastStatus[item.id] = item.status
                    }
                }

                val running = items.filter { it.status == DownloadItem.Status.RUNNING }
                if (running.isEmpty()) {
                    // لا تحميلات نشطة: إخفاء الإشعار الدائم وإيقاف الخدمة
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }

                updateProgressNotification(running)
                delay(700)
            }
        }
    }

    /** إشعار التقدم الدائم: يجمع كل التحميلات النشطة في إشعار واحد */
    private fun updateProgressNotification(running: List<DownloadItem>) {
        val downloaded = running.sumOf { it.downloadedBytes }
        val total = running.filter { it.totalBytes > 0 }.sumOf { it.totalBytes }
        val indeterminate = total <= 0
        val progress = if (!indeterminate) {
            ((downloaded * 100) / total).toInt().coerceIn(0, 100)
        } else 0

        val text = if (running.size == 1) running.first().fileName
        else "${running.size} ملفات — " + running.first().fileName

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(FOREGROUND_ID, buildProgressNotification(progress, indeterminate, text))
    }

    private fun buildProgressNotification(
        progress: Int = 0,
        indeterminate: Boolean = true,
        text: String = ""
    ): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.status_running))
            .setContentText(text)
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    /** عند اكتمال تحميل: اهتزاز خفيف + Toast + إشعار يفتح الملف */
    private fun onDownloadCompleted(item: DownloadItem) {
        // 1) اهتزاز خفيف
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(150)
            }
        } catch (_: Exception) {
        }

        // 2) نافذة منبثقة صغيرة
        Toast.makeText(
            this,
            getString(R.string.download_done, item.fileName),
            Toast.LENGTH_SHORT
        ).show()

        // 3) إشعار اكتمال (النقر عليه يفتح الملف)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(item.id.hashCode(), buildCompletedNotification(item))
    }

    private fun buildCompletedNotification(item: DownloadItem): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.status_completed))
            .setContentText(item.fileName)
            .setAutoCancel(true)

        item.savedFileUri?.let { uriString ->
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uriString), DownloadEngine.mimeFor(item.fileName))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pending = PendingIntent.getActivity(
                this,
                item.id.hashCode(),
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.setContentIntent(pending)
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        // أهمية LOW: الإشعار الدائم بدون صوت، والاهتزاز يتم يدوياً عند الاكتمال
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.downloads),
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
