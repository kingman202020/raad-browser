package com.raad.browser.downloads

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raad.browser.Prefs
import com.raad.browser.R
import com.raad.browser.ui.AppIcons

/**
 * النافذة المنبثقة التي تظهر قبل بدء أي تحميل:
 * - تسمية الملف يدوياً **بدون تغيير صيغته** (الامتداد ثابت ويُلحق تلقائياً).
 * - الحفظ يتم في آخر مجلد اختاره المستخدم من قسم الإعدادات
 *   (أو مجلد التطبيق الافتراضي إذا لم يختر شيئاً).
 */
@Composable
fun DownloadRequestDialog(
    request: DownloadRequest,
    onDismiss: () -> Unit,
    onConfirm: (fileName: String, treeUri: String?) -> Unit
) {
    // فصل الاسم عن الامتداد: المستخدم يعدّل الاسم فقط والامتداد يبقى ثابتاً
    val suggested = request.suggestedName
    val hasExtension = suggested.contains('.') && suggested.lastIndexOf('.') > 0
    val extension = if (hasExtension) suggested.substring(suggested.lastIndexOf('.')) else ""
    var baseName by remember {
        mutableStateOf(if (hasExtension) suggested.substringBeforeLast('.') else suggested)
    }

    // مجلد الحفظ = آخر مجلد مختار من الإعدادات
    val treeUri = Prefs.defaultTreeUri

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = baseName,
                        onValueChange = { baseName = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (extension.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        // الامتداد يظهر كنص ثابت غير قابل للتعديل
                        Text(
                            extension,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val name = baseName.trim()
                    if (name.isNotEmpty()) {
                        // إلحاق الامتداد الأصلي دائماً مهما كتب المستخدم في حقل الاسم
                        onConfirm(name + extension, treeUri)
                    }
                }
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
