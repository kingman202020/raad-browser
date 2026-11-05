package com.raad.browser.downloads

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raad.browser.Prefs
import com.raad.browser.R
import com.raad.browser.ui.AppIcons

/**
 * النافذة المنبثقة التي تظهر قبل بدء أي تحميل:
 * أ) تعديل اسم الملف المقترح يدوياً.
 * ب) اختيار مجلد التخزين المخصص عبر SAF.
 */
@Composable
fun DownloadRequestDialog(
    request: DownloadRequest,
    onDismiss: () -> Unit,
    onConfirm: (fileName: String, treeUri: String?) -> Unit
) {
    val context = LocalContext.current
    var fileName by remember { mutableStateOf(request.suggestedName) }
    var treeUri by remember { mutableStateOf(Prefs.defaultTreeUri) }

    // منتقي مجلد النظام (SAF) مع أخذ إذن دائم على المجلد المختار
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {
            }
            treeUri = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_file)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.file_name),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.save_to),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        AppIcons.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = dirLabel(treeUri),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { folderPicker.launch(null) }) {
                        Text(stringResource(R.string.choose_folder))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (fileName.isNotBlank()) onConfirm(fileName.trim(), treeUri) }
            ) {
                Text(stringResource(R.string.download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** تسمية مقروءة لمجلد الحفظ المختار */
@Composable
fun dirLabel(treeUri: String?): String {
    if (treeUri == null) return stringResource(R.string.default_dir)
    val segment = Uri.parse(treeUri).lastPathSegment ?: return treeUri
    // الشكل المعتاد: "primary:Download" أو "XXXX-XXXX:Documents"
    return Uri.decode(segment.substringAfter(':'))
}
