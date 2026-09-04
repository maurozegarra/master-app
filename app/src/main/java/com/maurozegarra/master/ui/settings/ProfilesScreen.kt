package com.maurozegarra.master.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.maurozegarra.master.MasterViewModel
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.model.Profile
import com.maurozegarra.master.ui.theme.ACTION_DELETE
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.Dims

/**
 * Quién puede recibir trainings: crear, renombrar y quitar.
 *
 * Solo se llega aquí con sesión de entrenador. Aun así, la autorización real vive en el
 * servidor: si la sesión caducó, la escritura se rechaza y se ve el motivo. Esconder los
 * botones es comodidad, no seguridad.
 */
@Composable
fun ProfilesScreen(vm: MasterViewModel, t: Strings) {
    val ctx = LocalContext.current
    val accent = AppTheme.colors.accent

    var loading by remember { mutableStateOf(true) }
    // Null es "no se pudo leer", que no es "no hay nadie": enseñar una lista vacía tras un
    // fallo de red haría creer que se borraron los perfiles.
    var profiles by remember { mutableStateOf<List<Profile>?>(null) }
    var reloads by remember { mutableStateOf(0) }

    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Profile?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    var deletingCount by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(reloads) {
        loading = true
        vm.loadProfiles {
            profiles = it
            loading = false
        }
    }

    /** Cierra el diálogo, avisa del resultado y vuelve a pedir la lista al servidor. */
    fun finish(error: String?) {
        busy = false
        Toast.makeText(ctx, error ?: t.profilesUpdated, Toast.LENGTH_SHORT).show()
        if (error == null) reloads++
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val list = profiles
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dims.card))
                .background(AppTheme.colors.surface)
                .padding(16.dp),
        ) {
            when {
                loading -> Text("…", color = AppTheme.colors.textDim, fontSize = 14.sp)
                list == null -> Text(t.profileLoadFailed, color = AppTheme.colors.textDim, fontSize = 14.sp)
                list.isEmpty() -> Text(t.noProfilesYet, color = AppTheme.colors.textDim, fontSize = 14.sp)
                else -> list.forEachIndexed { i, p ->
                    if (i > 0) Spacer(Modifier.height(4.dp))
                    ProfileRow(
                        profile = p,
                        accent = accent,
                        t = t,
                        onRename = { renaming = p },
                        onDelete = {
                            deleting = p
                            deletingCount = null
                            vm.countAssignments(p.id) { deletingCount = it }
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                t.newProfile,
                color = accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { adding = true }
                    .padding(vertical = 4.dp),
            )
        }
    }

    if (adding) {
        NameDialog(
            title = t.newProfile,
            initial = "",
            confirm = t.add,
            busy = busy,
            t = t,
            onDismiss = { adding = false },
            onConfirm = { name ->
                busy = true
                vm.createProfile(name) { error ->
                    adding = false
                    finish(error)
                }
            },
        )
    }

    renaming?.let { p ->
        NameDialog(
            title = t.renameProfile,
            initial = p.name,
            confirm = t.save,
            busy = busy,
            t = t,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                busy = true
                vm.renameProfile(p.id, name) { error ->
                    renaming = null
                    finish(error)
                }
            },
        )
    }

    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = AppTheme.colors.surface,
            titleContentColor = AppTheme.colors.textPrimary,
            title = { Text(t.delete) },
            text = {
                // Mientras no se sepa cuántas asignaciones se lleva, no se ofrece borrar:
                // el aviso sin el número diría menos de lo que hace.
                val count = deletingCount
                Text(
                    if (count == null) "…" else t.deleteProfileConfirm(p.name, count),
                    color = AppTheme.colors.textDim,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = deletingCount != null && !busy,
                    onClick = {
                        busy = true
                        vm.deleteProfile(p.id) { error ->
                            deleting = null
                            finish(error)
                        }
                    },
                ) { Text(t.delete, color = ACTION_DELETE, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(t.cancel, color = AppTheme.colors.textDim)
                }
            },
        )
    }
}

@Composable
private fun ProfileRow(
    profile: Profile,
    accent: Color,
    t: Strings,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            profile.name,
            color = AppTheme.colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            t.renameProfile,
            color = accent,
            fontSize = 14.sp,
            modifier = Modifier.clickable(onClick = onRename),
        )
        Text(
            t.delete,
            color = ACTION_DELETE,
            fontSize = 14.sp,
            modifier = Modifier.clickable(onClick = onDelete),
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    busy: Boolean,
    t: Strings,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = AppTheme.colors.surface,
        titleContentColor = AppTheme.colors.textPrimary,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(t.profileName) },
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && !busy, onClick = { onConfirm(name) }) {
                Text(confirm, color = AppTheme.colors.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(t.cancel, color = AppTheme.colors.textDim)
            }
        },
    )
}
