package com.raad.browser.browser

import android.webkit.WebView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.raad.browser.MainViewModel
import com.raad.browser.Navigator
import com.raad.browser.R
import com.raad.browser.Screen
import com.raad.browser.downloads.DownloadEngine
import com.raad.browser.downloads.DownloadRequestDialog
import com.raad.browser.store.BookmarkStore
import java.net.URLEncoder

/**
 * شاشة المتصفح الرئيسية: شريط عنوان/بحث شبيه بكروم + شريط تقدم + WebView.
 * تدعم RTL تلقائياً لأن اتجاه الواجهة يفرض من MainActivity.
 */
@Composable
fun BrowserScreen(vm: MainViewModel) {
    val tab = TabManager.currentTab()
    if (tab == null) {
        LaunchedEffect(Unit) { TabManager.newTab() }
        return
    }

    val focusManager = LocalFocusManager.current
    var urlText by remember(tab.id) { mutableStateOf(tab.url) }
    // مزامنة حقل العنوان مع عنوان الصفحة الفعلي عند التنقل
    LaunchedEffect(tab.url) { urlText = tab.url }

    var menuExpanded by remember { mutableStateOf(false) }

    // نافذة التحميل المنبثقة (اختيار الاسم والمجلد) قبل بدء أي تحميل
    vm.downloadRequest?.let { request ->
        DownloadRequestDialog(
            request = request,
            onDismiss = { vm.downloadRequest = null },
            onConfirm = { name, treeUri ->
                DownloadEngine.enqueue(request.url, name, treeUri)
                vm.downloadRequest = null
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        // ---------------- الشريط العلوي ----------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // زر التبويبات مع عداد
            IconButton(onClick = { Navigator.go(Screen.Tabs) }) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = com.raad.browser.ui.AppIcons.Tabs,
                        contentDescription = stringResource(R.string.tabs),
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = TabManager.tabs.size.toString(),
                        style = TextStyle(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // حقل العنوان/البحث
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                textStyle = TextStyle(fontSize = 14.sp),
                placeholder = {
                    Text(stringResource(R.string.search_hint), fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (urlText.isNotEmpty()) {
                        IconButton(onClick = { urlText = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        loadUrl(tab, urlText)
                        focusManager.clearFocus()
                    }
                )
            )

            // تحديث الصفحة
            IconButton(onClick = { TabManager.webView(tab.id)?.reload() }) {
                Icon(Icons.Default.Refresh, stringResource(R.string.reload))
            }

            // القائمة المنسدلة
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.menu))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.new_tab)) },
                        onClick = { menuExpanded = false; TabManager.newTab() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home)) },
                        onClick = {
                            menuExpanded = false
                            TabManager.webView(tab.id)?.loadUrl(TabManager.HOME_URL)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (BookmarkStore.contains(tab.url)) R.string.remove_bookmark
                                    else R.string.add_bookmark
                                )
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            BookmarkStore.toggle(tab.url, tab.title.ifBlank { tab.url })
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.bookmarks)) },
                        onClick = { menuExpanded = false; Navigator.go(Screen.Bookmarks) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history)) },
                        onClick = { menuExpanded = false; Navigator.go(Screen.History) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.downloads)) },
                        onClick = { menuExpanded = false; Navigator.go(Screen.Downloads) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings)) },
                        onClick = { menuExpanded = false; Navigator.go(Screen.Settings) }
                    )
                }
            }
        }

        // ---------------- شريط التقدم ----------------
        if (tab.isLoading) {
            LinearProgressIndicator(
                progress = { (tab.progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
        } else {
            Spacer(Modifier.height(3.dp))
        }

        // ---------------- محتوى الويب ----------------
        androidx.compose.runtime.key(tab.id) {
            AndroidView(
                factory = { TabManager.getOrCreateWebView(tab.id, vm) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * تحويل إدخال المستخدم إلى URL:
 * - روابط كاملة تمر كما هي.
 * - نص فيه نقطة بدون مسافات يعتبر نطاقاً.
 * - غير ذلك يعتبر بحثاً في Google.
 */
private fun loadUrl(tab: Tab, input: String) {
    val query = input.trim()
    if (query.isEmpty()) return
    val url = when {
        query.startsWith("http://") || query.startsWith("https://") -> query
        query.contains(".") && !query.contains(" ") -> "https://$query"
        else -> "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")
    }
    TabManager.webView(tab.id)?.loadUrl(url)
}
