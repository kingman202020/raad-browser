package com.raad.browser.store

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raad.browser.Navigator
import com.raad.browser.R
import com.raad.browser.Screen
import com.raad.browser.browser.TabManager
import com.raad.browser.ui.AppIcons
import com.raad.browser.ui.SimpleTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** شاشة سجل التصفح */
@Composable
fun HistoryScreen() {
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(title = stringResource(R.string.history)) {
            if (HistoryStore.entries.isNotEmpty()) {
                TextButton(onClick = { HistoryStore.clear() }) {
                    Text(stringResource(R.string.clear_history))
                }
            }
        }
        EntryList(
            entries = HistoryStore.entries,
            emptyText = stringResource(R.string.empty_history),
            showTime = true,
            onRemove = { HistoryStore.remove(it) }
        )
    }
}

/** شاشة العلامات المرجعية */
@Composable
fun BookmarksScreen() {
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(title = stringResource(R.string.bookmarks))
        EntryList(
            entries = BookmarkStore.entries,
            emptyText = stringResource(R.string.empty_bookmarks),
            showTime = false,
            onRemove = { BookmarkStore.remove(it) }
        )
    }
}

@Composable
private fun EntryList(
    entries: List<StoreEntry>,
    emptyText: String,
    showTime: Boolean,
    onRemove: (StoreEntry) -> Unit
) {
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        return
    }

    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    LazyColumn(Modifier.fillMaxSize()) {
        items(entries) { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // فتح الرابط في التبويب الحالي ثم العودة لشاشة المتصفح
                        val tab = TabManager.currentTab()
                        if (tab != null) {
                            TabManager.webView(tab.id)?.loadUrl(entry.url)
                                ?: run {
                                    tab.url = entry.url
                                    tab.loadedOnce = false
                                }
                        }
                        Navigator.go(Screen.Browser)
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = if (showTime) AppIcons.History else Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        entry.title.ifBlank { entry.url },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (showTime)
                            entry.url + "  •  " + formatter.format(Date(entry.time))
                        else entry.url,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = { onRemove(entry) }) {
                    Icon(Icons.Default.Close, stringResource(R.string.close))
                }
            }
            HorizontalDivider()
        }
    }
}
