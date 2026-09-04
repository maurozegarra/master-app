package com.maurozegarra.master.ui.master

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.MasterViewModel
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.model.Profile
import com.maurozegarra.master.model.Training
import com.maurozegarra.master.ui.theme.AppTheme

/**
 * "Asignar a…": quién recibe este training.
 *
 * Se abre con lo que hay HOY en el servidor ya marcado, no con una lista en blanco. Sin
 * eso, confirmar sin tocar nada retiraría el training a todo el mundo, y el diálogo que
 * parece inofensivo sería el que más borra.
 *
 * El perfil de este mismo teléfono no aparece: asignárselo a uno mismo añadiría una
 * segunda copia del training, marcada como asignada, junto a la que ya está. El original
 * es propio y la fusión no lo toca nunca —esa regla protege lo que uno crea—, así que las
 * dos convivirían y la lista mostraría el mismo training dos veces.
 */
@Composable
fun AssignDialog(vm: MasterViewModel, training: Training, t: Strings, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val accent = AppTheme.colors.accent

    var loading by remember { mutableStateOf(true) }
    // Null es "no se pudo leer". Con la lista o los marcados a medias no se puede escribir:
    // se confirmaría contra un estado inventado.
    var profiles by remember { mutableStateOf<List<Profile>?>(null) }
    var checked by remember { mutableStateOf<Set<String>?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(training.uid) {
        vm.loadProfiles { list ->
            profiles = list?.filter { it.id != vm.profileId }
            vm.loadAssignees(training.uid) { assignees ->
                checked = assignees?.toSet()
                loading = false
            }
        }
    }

    val list = profiles
    val marks = checked

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        containerColor = AppTheme.colors.surface,
        titleContentColor = AppTheme.colors.textPrimary,
        textContentColor = AppTheme.colors.textDim,
        title = { Text(t.assignTo) },
        text = {
            Column {
                when {
                    loading -> Text("…")
                    list == null || marks == null -> Text(t.profileLoadFailed)
                    list.isEmpty() -> Text(t.assignNobody)
                    else -> {
                        list.forEach { p ->
                            val isOn = p.id in marks
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !busy) {
                                        checked = if (isOn) marks - p.id else marks + p.id
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Checkbox(
                                    checked = isOn,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = accent),
                                )
                                Text(p.name, color = AppTheme.colors.textPrimary, fontSize = 16.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(t.assignWarning, color = AppTheme.colors.textFaded, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            // Sin lista o sin los marcados de hoy no se ofrece guardar: se confirmaria
            // contra un estado inventado, y confirmar es lo que retira asignaciones.
            if (!loading && list != null && marks != null && list.isNotEmpty()) {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        vm.setAssignees(training.id, marks) { error ->
                            Toast.makeText(ctx, error ?: t.assignDone, Toast.LENGTH_SHORT).show()
                            onClose()
                        }
                    },
                ) { Text(t.save, color = accent, fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onClose) {
                Text(t.cancel, color = AppTheme.colors.textDim)
            }
        },
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
