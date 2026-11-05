package com.raad.browser.settings

import android.content.Intent
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raad.browser.Prefs
import com.raad.browser.R
import com.raad.browser.downloads.dirLabel
import com.raad.browser.store.HistoryStore
import com.raad.browser.ui.AppIcons
import com.raad.browser.ui.SimpleTopBar

/**
 * شاشة الإعدادات: المظهر (تلقائي/فاتح/داكن)، مجلد التحميل الافتراضي،
 * مسح السجل وملفات تعريف الارتباط.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current

    // منتقي مجلد التحميل الافتراضي عبر SAF
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {
            }
            Prefs.setDefaultTreeUri(uri.toString())
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SimpleTopBar(title = stringResource(R.string.settings))

        // ---------- المظهر ----------
        SectionTitle(stringResource(R.string.theme))
        ThemeOption(R.string.theme_system, Prefs.THEME_SYSTEM)
        ThemeOption(R.string.theme_light, Prefs.THEME_LIGHT)
        ThemeOption(R.string.theme_dark, Prefs.THEME_DARK)

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---------- مجلد التحميل ----------
        SectionTitle(stringResource(R.string.default_download_dir))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(AppIcons.Folder, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                dirLabel(Prefs.defaultTreeUri),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = { folderPicker.launch(null) }) {
                Text(stringResource(R.string.choose_folder))
            }
        }
        TextButton(
            onClick = { Prefs.setDefaultTreeUri(null) },
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(stringResource(R.string.use_app_folder))
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---------- الخصوصية ----------
        TextButton(
            onClick = { HistoryStore.clear() },
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(stringResource(R.string.clear_history))
        }
        TextButton(
            onClick = {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                Toast.makeText(
                    context,
                    context.getString(R.string.cookies_cleared),
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(stringResource(R.string.clear_cookies))
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---------- حول ----------
        SectionTitle(stringResource(R.string.about))
        Text(
            stringResource(R.string.app_name) + " • " +
                stringResource(R.string.version) + " 1.0",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ThemeOption(labelRes: Int, mode: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { Prefs.setThemeMode(mode) }
            .padding(horizontal = 8.dp)
    ) {
        RadioButton(
            selected = Prefs.themeMode == mode,
            onClick = { Prefs.setThemeMode(mode) }
        )
        Text(stringResource(labelRes))
    }
}
