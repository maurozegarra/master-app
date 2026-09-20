package com.maurozegarra.master.ui.master

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.maurozegarra.master.data.VideoState
import com.maurozegarra.master.model.Exercise
import com.maurozegarra.master.ui.SwitchRow
import com.maurozegarra.master.ui.ExerciseVideo
import com.maurozegarra.master.ui.openVideoExternally
import com.maurozegarra.master.ui.theme.ACTION_DELETE
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.Dims

/**
 * El vídeo del ejercicio y sus instrucciones, como un campo más (TD-145).
 *
 * Antes esta tarjeta traía un cartel de dos líneas avisando de que lo de dentro era "del
 * movimiento" y no de este ejercicio, y el interruptor de verlo vivía lejos, entre las
 * series. Las dos cosas explicaban una decisión de implementación —el archivo pesa megas y
 * se guarda una vez por `exerciseId`— y no algo que el usuario tenga que cargar: *"es un
 * tremendo banner para justificar el mal diseño"*.
 *
 * Ahora el alcance se dice **solo cuando es verdad**: si el movimiento se usa en otro
 * training, se nombra; si no, no se dice nada.
 *
 * El vídeo se copia **al elegirlo** y no espera al Save, a diferencia de las instrucciones.
 * Es la única asimetría y es a propósito: elegir un archivo es un acto con respuesta
 * inmediata —aparece la miniatura— y se deshace con Remove; escribir un texto es un
 * borrador.
 */
@Composable
fun ExerciseMediaCard(
    vm: MasterViewModel,
    ex: Exercise,
    accent: Color,
    t: Strings,
    steps: List<String>,
    onStepsChange: (List<String>) -> Unit,
    onChange: (Exercise) -> Unit,
) {
    val ctx = LocalContext.current
    val exerciseId = ex.exerciseId
    val state = vm.videoStateFor(exerciseId)
    val videoFile = (state as? VideoState.Ready)?.file
    // Solo se puede quitar lo que puso el usuario: un vídeo publicado se volvería a
    // descargar, así que ofrecer "quitar" sería mentirle.
    val own = remember(exerciseId, state) { vm.hasOwnVideo(exerciseId) }
    val alsoIn = remember(exerciseId, ex.id) { vm.otherTrainingsUsing(exerciseId, vm.draft?.id) }
    var error by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        error = false
        vm.assignVideo(
            exerciseId = exerciseId,
            source = { ctx.contentResolver.openInputStream(uri) },
        ) { ok -> error = !ok }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.card))
            .background(AppTheme.colors.surface)
            .padding(16.dp),
    ) {
        Text(
            t.video,
            color = AppTheme.colors.textDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        if (videoFile != null) {
            // Alto máximo, no fijo: la miniatura toma la forma real del archivo, así que
            // un vídeo vertical se ve entero en vez de recortado a un hueco horizontal.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .clickable { openVideoExternally(ctx, videoFile) },
                contentAlignment = Alignment.Center,
            ) {
                ExerciseVideo(
                    file = videoFile,
                    playing = false,
                    paused = true,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                )
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = t.play, tint = Color.White)
                }
            }
            VideoActions(
                accent = accent,
                t = t,
                canRemove = own,
                // Solo hay algo que publicar si este telefono tiene un video propio, y solo
                // puede hacerlo quien reparte rutinas (TD-140).
                canPublish = own && vm.isCoach,
                onReplace = { picker.launch(arrayOf("video/*")) },
                onRemove = { vm.removeVideo(exerciseId) },
                onPublish = {
                    publishing = true
                    vm.publishVideo(exerciseId) { err ->
                        publishing = false
                        Toast.makeText(ctx, err ?: t.videoPublished, Toast.LENGTH_LONG).show()
                    }
                },
                publishing = publishing,
            )
            // Debajo del vídeo del que habla, y no entre las series: así no hace falta
            // explicar a qué se refiere.
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                label = t.showVideoHere,
                desc = null,
                checked = ex.showVideo,
                accent = accent,
                onCheckedChange = { onChange(ex.copy(showVideo = it)) },
            )
        } else {
            // Hay vídeo publicado pero todavía no está en el teléfono. Se dice, en vez de
            // ofrecer "añadir" como si no existiera ninguno.
            val pending = when (state) {
                is VideoState.Downloading -> t.videoDownloading
                is VideoState.Pending -> t.videoNotDownloaded
                else -> null
            }
            if (pending != null) {
                Text(pending, color = AppTheme.colors.textDim, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
            }
            Text(
                t.addVideo,
                color = accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { picker.launch(arrayOf("video/*")) },
            )
        }

        if (error) {
            Text(t.videoSaveFailed, color = ACTION_DELETE, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }

        InstructionsEditor(
            steps = steps,
            accent = accent,
            t = t,
            onChange = onStepsChange,
        )

        // El alcance, solo si lo hay: el vídeo y las instrucciones son del movimiento, y
        // eso importa cuando de verdad hay otro training que los enseña.
        if (alsoIn.isNotEmpty()) {
            Text(
                "${t.usedAlsoIn} ${alsoIn.joinToString(", ")}",
                color = AppTheme.colors.textFaded,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun VideoActions(
    accent: Color,
    t: Strings,
    canRemove: Boolean,
    canPublish: Boolean,
    publishing: Boolean,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    onPublish: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            t.replaceVideo,
            color = accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onReplace),
        )
        if (canPublish) {
            Text(
                if (publishing) "${t.publishVideo}…" else t.publishVideo,
                color = if (publishing) AppTheme.colors.textFaded else accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(enabled = !publishing, onClick = onPublish),
            )
        }
        // Solo se puede quitar lo que puso el usuario: un vídeo publicado se volvería a
        // descargar, así que ofrecer "quitar" sería mentirle. Para no verlo aquí está el
        // interruptor del ejercicio, que es de esta instancia y no del movimiento.
        if (canRemove) {
            Text(
                t.removeVideo,
                color = ACTION_DELETE,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onRemove),
            )
        }
    }
}

@Composable
private fun InstructionsEditor(
    steps: List<String>,
    accent: Color,
    t: Strings,
    onChange: (List<String>) -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    Text(
        t.instructions,
        color = AppTheme.colors.textPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )

    steps.forEachIndexed { i, step ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("${i + 1}", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                step,
                color = AppTheme.colors.textPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onChange(steps.toMutableList().apply { removeAt(i) }) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = t.delete, tint = AppTheme.colors.textDim, modifier = Modifier.size(16.dp))
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text(t.addStepHint, color = AppTheme.colors.textFaded) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = AppTheme.colors.track,
                focusedTextColor = AppTheme.colors.textPrimary,
                unfocusedTextColor = AppTheme.colors.textPrimary,
                cursorColor = accent,
            ),
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(enabled = draft.isNotBlank()) {
                    onChange(steps + draft.trim())
                    draft = ""
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = t.add,
                tint = if (draft.isNotBlank()) accent else AppTheme.colors.textFaded,
            )
        }
    }
}
