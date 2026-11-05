package com.raad.browser.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raad.browser.R
import com.raad.browser.ui.AppIcons
import com.raad.browser.ui.SimpleTopBar
import java.util.Locale

/**
 * شاشة مدير التحميلات: متابعة التقدم، إيقاف مؤقت، استئناف، إلغاء، فتح الملفات.
 */
@Composable
fun DownloadsScreen() {
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(title = stringResource(R.string.downloads)) {
            if (DownloadEngine.downloads.any { it.status == DownloadItem.Status.COMPLETED }) {
                androidx.compose.material3.TextButton(onClick = { DownloadEngine.clearCompleted() }) {
                    Text(stringResource(R.string.clear_completed))
                }
            }
        }

        if (DownloadEngine.downloads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.empty_downloads),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(DownloadEngine.downloads, key = { it.id }) { item ->
                    DownloadRow(item)
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadItem) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                item.fileName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${formatBytes(item.downloadedBytes)} / " +
                    if (item.totalBytes > 0) formatBytes(item.totalBytes) else "؟",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(6.dp))

            // شريط التقدم: محدد عند معرفة الحجم، غير محدد عندما يكون الحجم مجهولاً
            when (item.status) {
                DownloadItem.Status.RUNNING -> {
                    if (item.totalBytes > 0) {
                        LinearProgressIndicator(
                            progress = { (item.downloadedBytes.toFloat() / item.totalBytes).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                DownloadItem.Status.PAUSED -> LinearProgressIndicator(
                    progress = {
                        if (item.totalBytes > 0)
                            (item.downloadedBytes.toFloat() / item.totalBytes).coerceIn(0f, 1f)
                        else 0f
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                else -> {}
            }

            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = statusText(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (item.status) {
                        DownloadItem.Status.FAILED -> MaterialTheme.colorScheme.error
                        DownloadItem.Status.COMPLETED -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    },
                    modifier = Modifier.weight(1f)
                )

                when (item.status) {
                    DownloadItem.Status.RUNNING -> IconButton(onClick = { DownloadEngine.pause(item.id) }) {
                        Icon(AppIcons.Pause, stringResource(R.string.pause))
                    }
                    DownloadItem.Status.PAUSED, DownloadItem.Status.FAILED ->
                        IconButton(onClick = { DownloadEngine.resume(item.id) }) {
                            Icon(Icons.Default.PlayArrow, stringResource(R.string.resume))
                        }
                    DownloadItem.Status.COMPLETED ->
                        IconButton(onClick = { DownloadEngine.open(context, item) }) {
                            Icon(AppIcons.Folder, stringResource(R.string.open_file))
                        }
                    else -> {}
                }

                IconButton(onClick = { DownloadEngine.remove(item.id) }) {
                    Icon(Icons.Default.Close, stringResource(R.string.remove_from_list))
                }
            }
        }
    }
}

@Composable
private fun statusText(item: DownloadItem): String = when (item.status) {
    DownloadItem.Status.RUNNING -> stringResource(R.string.status_running)
    DownloadItem.Status.PAUSED -> stringResource(R.string.status_paused)
    DownloadItem.Status.COMPLETED -> stringResource(R.string.status_completed)
    DownloadItem.Status.FAILED ->
        stringResource(R.string.status_failed) + (item.error?.let { ": $it" } ?: "")
    DownloadItem.Status.CANCELED -> stringResource(R.string.status_canceled)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    do {
        value /= 1024.0
        unit++
    } while (value >= 1024 && unit < units.size - 1)
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}
