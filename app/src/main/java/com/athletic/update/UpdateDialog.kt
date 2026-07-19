package com.athletic.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.OptIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.unit.dp
import com.athletic.ui.theme.AppTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    isForced: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var downloaded by remember { mutableStateOf(false) }
    var apkFile by remember { mutableStateOf<File?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!isForced && !downloading) onDismiss()
        },
        title = { Text("Update available${if (isForced) " (required)" else ""}") },
        text = {
            Column {
                Text("Version ${updateInfo.versionName}")
                if (updateInfo.changelog.isNotBlank()) {
                    Text(
                        updateInfo.changelog,
                        modifier = Modifier.padding(top = 8.dp),
                        color = AppTheme.colors.textPrimary,
                    )
                }
                if (downloading && !downloaded) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (downloaded && apkFile != null) {
                TextButton(onClick = {
                    installApk(context, apkFile!!)
                }) {
                    Text("Install")
                }
            } else if (!downloading) {
                TextButton(onClick = {
                    downloading = true
                    val file = File(context.cacheDir, "athletic-update.apk")
                    apkFile = file
                    Thread {
                        try {
                            downloadApk(updateInfo.apkUrl, file) { p ->
                                progress = p
                            }
                            downloaded = true
                        } catch (e: Exception) {
                            downloading = false
                        }
                    }.start()
                }) {
                    Text("Download")
                }
            }
        },
        dismissButton = {
            if (!isForced && !downloading) {
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        },
    )
}

private fun downloadApk(url: String, file: File, onProgress: (Float) -> Unit) {
    val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
        connectTimeout = 30000
        readTimeout = 30000
    }
    val total = conn.contentLength.toFloat()
    conn.inputStream.use { input ->
        file.outputStream().use { output ->
            val buffer = ByteArray(8192)
            var bytesread = 0
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                bytesread += read
                if (total > 0) onProgress(bytesread / total)
            }
        }
    }
}

private fun installApk(context: Context, file: File) {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
