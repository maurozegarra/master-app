package com.maurozegarra.master.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** El permiso de lectura de vídeo cambió de nombre en Android 13. */
private val VIDEO_PERMISSION =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

/**
 * Estado del permiso para leer los vídeos de `Movies/MASTER/`.
 *
 * Hace falta aunque los archivos los haya escrito el propio app: bajo scoped storage
 * MediaStore solo devuelve lo que el app posee, y al desinstalar Android borra ese
 * `owner_package_name`. Sin el permiso, tras reinstalar los vídeos serían invisibles
 * pese a seguir en disco.
 */
@Composable
fun rememberVideoPermission(requestOnLoad: Boolean = false): VideoPermissionState {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, VIDEO_PERMISSION) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }

    LaunchedEffect(requestOnLoad, granted) {
        if (requestOnLoad && !granted) launcher.launch(VIDEO_PERMISSION)
    }

    return remember(granted) {
        VideoPermissionState(granted = granted, request = { launcher.launch(VIDEO_PERMISSION) })
    }
}

data class VideoPermissionState(val granted: Boolean, val request: () -> Unit)
