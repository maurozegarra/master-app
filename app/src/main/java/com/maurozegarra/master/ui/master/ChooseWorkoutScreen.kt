package com.maurozegarra.master.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.MasterViewModel
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.model.Training
import com.maurozegarra.master.model.Workout
import com.maurozegarra.master.ui.theme.AppTheme
import java.time.Instant
import java.time.ZoneId

/**
 * Selector de workouts ya creados para copiarlos al training en edición.
 *
 * Los grupos vienen ordenados por uso real (ver `MasterViewModel.workoutPickerSources`):
 * arriba el training entrenado más recientemente, que es de donde normalmente se quiere
 * partir. El encabezado muestra cuándo se entrenó para que ese orden se explique solo.
 */
@Composable
fun ChooseWorkoutScreen(vm: MasterViewModel, accent: Color, t: Strings) {
    var query by remember { mutableStateOf("") }
    val sources = vm.workoutPickerSources()
    val lastTrained = vm.lastTrainedByTraining()
    val zone = remember { ZoneId.systemDefault() }

    val q = query.trim().lowercase()
    val filtered = if (q.isEmpty()) sources else sources.mapNotNull { (training, workouts) ->
        // Si el nombre del training calza, se muestra el grupo entero; si no, sus workouts.
        if (training.name.lowercase().contains(q)) {
            training to workouts
        } else {
            workouts.filter { it.name.lowercase().contains(q) }
                .takeIf { it.isNotEmpty() }
                ?.let { training to it }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(t.searchHint, color = AppTheme.colors.textFaded) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AppTheme.colors.textDim) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = AppTheme.colors.track,
                focusedTextColor = AppTheme.colors.textPrimary,
                unfocusedTextColor = AppTheme.colors.textPrimary,
                cursorColor = accent,
            ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filtered.forEach { (training, workouts) ->
                item(key = "h${training.id}") {
                    TrainingGroupHeader(
                        training = training,
                        lastTrainedAt = lastTrained[training.id],
                        zone = zone,
                        t = t,
                    )
                }
                items(workouts.size, key = { workouts[it].id }) { i ->
                    val w = workouts[i]
                    WorkoutPickRow(workout = w, t = t) { vm.pickWorkout(w.id) }
                }
            }
        }
    }
}

@Composable
private fun TrainingGroupHeader(
    training: Training,
    lastTrainedAt: Long?,
    zone: ZoneId,
    t: Strings,
) {
    val when_ = lastTrainedAt
        ?.let { dayLabel(Instant.ofEpochMilli(it).atZone(zone).toLocalDate(), zone, t) }
        ?: t.neverTrained
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            training.name.ifBlank { t.training },
            color = AppTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(when_, color = AppTheme.colors.textFaded, fontSize = 12.sp)
    }
}

@Composable
private fun WorkoutPickRow(workout: Workout, t: Strings, onClick: () -> Unit) {
    val subtitle = if (workout.rotating) {
        workout.variants.joinToString(" / ") { it.name.ifBlank { t.variant } }
            .ifBlank { "${workout.variants.size} ${t.variant}" }
    } else {
        "${workout.exercises.size} ${t.exercise}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.colors.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                workout.name.ifBlank { t.workout },
                color = AppTheme.colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(subtitle, color = AppTheme.colors.textDim, fontSize = 13.sp)
        }
    }
}
