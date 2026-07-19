package com.athletic.update

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athletic.ui.theme.AppTheme
import java.io.File

@Composable
fun UpdateBar(
    updateInfo: UpdateInfo,
    isForced: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val accent = AppTheme.colors.accent
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var downloaded by remember { mutableStateOf(false) }
    var apkFile by remember { mutableStateOf<File?>(null) }

    fun startDownload() {
        downloading = true
        val file = File(context.cacheDir, "athletic-update.apk")
        apkFile = file
        Thread {
            try {
                downloadApk(updateInfo.apkUrl, file) { p -> progress = p }
                downloaded = true
            } catch (e: Exception) {
                downloading = false
            }
        }.start()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent)
            .clickable(enabled = !downloading) {
                if (downloaded && apkFile != null) {
                    installApk(context, apkFile!!)
                } else if (!downloading) {
                    startDownload()
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (downloading && !downloaded) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = accent,
                )
            } else if (downloaded) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    Icons.Filled.ArrowDownward,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (downloaded) "Tap to install" else "Update Athletic",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            if (!downloading && !downloaded) {
                Text(
                    text = "v${updateInfo.versionName}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
            } else if (downloading && !downloaded) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }
        }

        if (!isForced && !downloading && !downloaded) {
            Text(
                text = "X",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(8.dp),
            )
        }
    }
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
