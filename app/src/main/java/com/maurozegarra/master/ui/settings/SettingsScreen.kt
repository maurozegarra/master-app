package com.maurozegarra.master.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.MasterViewModel
import com.maurozegarra.master.SettingsViewModel
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.model.ACCENT_COLORS
import com.maurozegarra.master.model.Profile
import com.maurozegarra.master.model.THEME_AUTO
import com.maurozegarra.master.model.THEME_DARK
import com.maurozegarra.master.model.THEME_LIGHT
import com.maurozegarra.master.ui.SwitchRow
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.Dims
import com.maurozegarra.master.ui.theme.PINK_ACCENT
import com.maurozegarra.master.ui.theme.STITCH_ACCENT
import java.time.LocalDate

/** Pantalla de Ajustes: general, player y respaldo de datos. */
@Composable
fun SettingsScreen(vm: SettingsViewModel, masterVm: MasterViewModel, t: Strings) {
    val cfg = vm.config
    val accent = AppTheme.colors.accent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsCard(t.groupProfile) {
            ProfileSection(masterVm = masterVm, accent = accent, t = t)
        }

        SettingsCard(t.groupGeneral) {
            Text(t.color, color = AppTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            AccentPicker(cfg.general.accent) { vm.setAccent(it) }
            Spacer(Modifier.height(16.dp))
            val themeLocked = cfg.general.accent == PINK_ACCENT || cfg.general.accent == STITCH_ACCENT
            Text(t.theme, color = AppTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            SegmentedRow(
                options = listOf(THEME_AUTO to t.themeAuto, THEME_LIGHT to t.themeLight, THEME_DARK to t.themeDark),
                selected = cfg.general.themeMode,
                accent = accent,
                enabled = !themeLocked,
                onSelect = { vm.setThemeMode(it) },
            )
            if (themeLocked) {
                Spacer(Modifier.height(8.dp))
                Text(
                    t.themeLockedByAccent,
                    color = AppTheme.colors.textFaded,
                    fontSize = 12.sp,
                )
            }
        }

        SettingsCard(t.groupPlayer) {
            SwitchRow(
                label = t.padPlayerClock,
                desc = t.padPlayerClockDesc,
                checked = cfg.masterConfig.padPlayerClock,
                accent = accent,
                onCheckedChange = { vm.setPadPlayerClock(it) },
            )
        }

        SettingsCard(t.groupData) {
            SwitchRow(
                label = t.downloadOverMobile,
                desc = t.downloadOverMobileDesc,
                checked = cfg.downloads.overMobileData,
                accent = accent,
                onCheckedChange = { vm.setDownloadOverMobileData(it) },
            )
            Spacer(Modifier.height(16.dp))
            BackupSection(masterVm = masterVm, accent = accent, t = t)
        }
    }
}

/**
 * Export/import del respaldo vía SAF: el archivo queda donde el usuario elija, fuera
 * del sandbox, que es lo único que sobrevive a una desinstalación.
 */
@Composable
private fun BackupSection(masterVm: MasterViewModel, accent: Color, t: Strings) {
    val ctx = LocalContext.current
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(masterVm.exportData().toByteArray())
            } ?: error("no output stream")
        }.isSuccess
        Toast.makeText(ctx, if (ok) t.exportDone else t.exportFailed, Toast.LENGTH_SHORT).show()
    }

    // El picker se abre con */* a propósito: según la app que haya generado el archivo,
    // un .json puede llegar con mime application/octet-stream y quedar invisible al
    // filtrar por application/json.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingImport = uri }

    ActionRow(
        label = t.exportData,
        desc = t.exportDataDesc,
        accent = accent,
        onClick = { exportLauncher.launch(backupFileName()) },
    )
    Spacer(Modifier.height(16.dp))
    ActionRow(
        label = t.importData,
        desc = t.importDataDesc,
        accent = accent,
        onClick = { importLauncher.launch(arrayOf("*/*")) },
    )

    val uri = pendingImport
    if (uri != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            containerColor = AppTheme.colors.surface,
            titleContentColor = AppTheme.colors.textPrimary,
            title = { Text(t.importTitle) },
            text = { Text(t.importWarning, color = AppTheme.colors.textDim) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    val json = runCatching {
                        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    }.getOrNull()
                    val result = json?.let { masterVm.importData(it) }
                    val msg = if (result == null) {
                        t.importFailed
                    } else {
                        val done = "${t.importDone}: ${result.summary.trainings} / ${result.summary.sessions}"
                        // Si la copia previa no se pudo escribir, el usuario acaba de
                        // reemplazar sus datos sin red: mejor que se entere ahora.
                        if (result.backedUp) done else "$done\n${t.importNoBackup}"
                    }
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                }) { Text(t.replace, color = accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(t.cancel, color = AppTheme.colors.textDim)
                }
            },
        )
    }
}

/**
 * Quién usa este dispositivo.
 *
 * No hay cuentas ni contraseñas: la identidad solo decide qué trainings se descargan, y
 * son personas conocidas. Elegir perfil dispara la sincronización en el acto, para que el
 * efecto se vea sin tener que reiniciar nada.
 */
@Composable
private fun ProfileSection(masterVm: MasterViewModel, accent: Color, t: Strings) {
    var choosing by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf<List<Profile>?>(null) }

    Text(t.profileWho, color = AppTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(2.dp))
    Text(t.profileDesc, color = AppTheme.colors.textDim, fontSize = 13.sp)
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            masterVm.profileName.ifBlank { t.profileNotSet },
            color = AppTheme.colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            t.profileChange,
            color = accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable {
                // La lista se pide al abrir, no al pintar los ajustes: es una llamada de
                // red y no tiene por que ocurrir cada vez que alguien mira esta pantalla.
                profiles = null
                choosing = true
                masterVm.loadProfiles { profiles = it }
            },
        )
    }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            containerColor = AppTheme.colors.surface,
            titleContentColor = AppTheme.colors.textPrimary,
            textContentColor = AppTheme.colors.textDim,
            title = { Text(t.profileWho) },
            text = {
                val list = profiles
                when {
                    list == null -> Text("…")
                    list.isEmpty() -> Text(t.profileNoneAvailable)
                    else -> Column {
                        list.forEach { p ->
                            Text(
                                p.name,
                                color = AppTheme.colors.textPrimary,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        masterVm.chooseProfile(p)
                                        choosing = false
                                    }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosing = false }) {
                    Text(t.cancel, color = AppTheme.colors.textDim)
                }
            },
        )
    }
}

/** master-backup-2026-08-29.json */
private fun backupFileName(): String =
    "master-backup-${LocalDate.now()}.json"

@Composable
private fun ActionRow(label: String, desc: String, accent: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.card))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Text(label, color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(desc, color = AppTheme.colors.textFaded, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.card))
            .background(AppTheme.colors.surface)
            .padding(16.dp),
    ) {
        Text(title, color = AppTheme.colors.textDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(selected: Long, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ACCENT_COLORS.forEach { ac ->
            val c = Color(ac.argb)
            val isSel = ac.argb == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(if (isSel) 3.dp else 0.dp, AppTheme.colors.textPrimary, CircleShape)
                        .clickable { onSelect(ac.argb) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSel) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    ac.label,
                    color = if (isSel) AppTheme.colors.textPrimary else AppTheme.colors.textFaded,
                    fontSize = 10.sp,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun SegmentedRow(
    options: List<Pair<Int, String>>,
    selected: Int,
    accent: Color,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.button))
            .background(AppTheme.colors.track)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(Dims.button))
                    .background(if (isSel) accent else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (isSel) AppTheme.colors.onAccent else AppTheme.colors.textDim,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
