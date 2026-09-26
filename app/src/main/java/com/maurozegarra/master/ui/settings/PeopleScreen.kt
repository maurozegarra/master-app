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
import com.maurozegarra.master.ui.theme.STATUS_SKIPPED
import com.maurozegarra.master.model.Training
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
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
 * **People** (Settings → Coach → People): quién puede recibir trainings -crear, renombrar,
 * quitar- y, tocando un nombre, qué tiene asignado cada uno (TD-134).
 *
 * En pantalla se llaman *People*; en el servidor, `profiles`, y el modelo es [Profile]. El
 * nombre del archivo y de la función siguen a la pantalla (TD-135): el 19-sep el coach le
 * dio al usuario la ruta "Manage profiles", sacada del código, y en el app no existe.
 *
 * Solo se llega aquí con sesión de entrenador. Aun así, la autorización real vive en el
 * servidor: si la sesión caducó, la escritura se rechaza y se ve el motivo. Esconder los
 * botones es comodidad, no seguridad.
 */
@Composable
fun PeopleScreen(vm: MasterViewModel, t: Strings) {
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

    // Lo que tiene asignado la persona abierta, leido del SERVIDOR y no del telefono
    // (TD-134): lo que importa ver es justo lo que este telefono ya no tiene.
    var abierto by remember { mutableStateOf<String?>(null) }
    var asignados by remember { mutableStateOf<List<Training>?>(null) }
    var cargandoAsignados by remember { mutableStateOf(false) }
    var quitando by remember { mutableStateOf<Pair<Profile, Training>?>(null) }

    fun cargarAsignados(profileId: String) {
        cargandoAsignados = true
        asignados = null
        vm.loadAssignedTo(profileId) {
            asignados = it
            cargandoAsignados = false
        }
    }

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
                        expanded = abierto == p.id,
                        onToggle = {
                            if (abierto == p.id) {
                                abierto = null
                            } else {
                                abierto = p.id
                                cargarAsignados(p.id)
                            }
                        },
                        onRename = { renaming = p },
                        onDelete = {
                            deleting = p
                            deletingCount = null
                            vm.countAssignments(p.id) { deletingCount = it }
                        },
                    )
                    if (abierto == p.id) {
                        AssignedList(
                            asignados = asignados,
                            cargando = cargandoAsignados,
                            locales = vm.trainings.map { it.uid }.toSet(),
                            t = t,
                            onRemove = { tr -> quitando = p to tr },
                        )
                    }
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

    quitando?.let { (p, tr) ->
        AlertDialog(
            onDismissRequest = { if (!busy) quitando = null },
            containerColor = AppTheme.colors.surface,
            titleContentColor = AppTheme.colors.textPrimary,
            title = { Text(t.unassign) },
            text = { Text(t.unassignConfirm(tr.name, p.name), color = AppTheme.colors.textDim) },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        vm.unassign(p.id, tr.uid) { error ->
                            quitando = null
                            busy = false
                            Toast.makeText(ctx, error ?: t.profilesUpdated, Toast.LENGTH_SHORT).show()
                            if (error == null) cargarAsignados(p.id)
                        }
                    },
                ) { Text(t.unassign, color = ACTION_DELETE, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { quitando = null }) {
                    Text(t.cancel, color = AppTheme.colors.textDim)
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
    expanded: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // El nombre abre lo que tiene asignado (TD-134).
        Row(
            Modifier.weight(1f).clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppTheme.colors.textDim,
                modifier = Modifier.rotate(if (expanded) 90f else 0f),
            )
            Text(
                profile.name,
                color = AppTheme.colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
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

/**
 * Lo que tiene asignado una persona, con la opcion de quitarlo (TD-134).
 *
 * Marca lo que ya NO esta en este telefono: es justo la asignacion huerfana, la que no
 * tiene ningun otro sitio donde quitarse.
 */
@Composable
private fun AssignedList(
    asignados: List<Training>?,
    cargando: Boolean,
    locales: Set<String>,
    t: Strings,
    onRemove: (Training) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 28.dp, bottom = 8.dp)) {
        when {
            cargando -> Text("\u2026", color = AppTheme.colors.textDim, fontSize = 14.sp)
            asignados == null -> Text(t.profileLoadFailed, color = AppTheme.colors.textDim, fontSize = 14.sp)
            asignados.isEmpty() -> Text(t.noneAssigned, color = AppTheme.colors.textDim, fontSize = 14.sp)
            else -> asignados.forEach { tr ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr.name, color = AppTheme.colors.textPrimary, fontSize = 15.sp)
                        if (tr.uid !in locales) {
                            Text(t.notOnThisPhone, color = STATUS_SKIPPED, fontSize = 12.sp)
                        }
                    }
                    Text(
                        t.unassign,
                        color = ACTION_DELETE,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { onRemove(tr) },
                    )
                }
            }
        }
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
