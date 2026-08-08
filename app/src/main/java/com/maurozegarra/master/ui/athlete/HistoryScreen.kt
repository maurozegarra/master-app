package com.maurozegarra.master.ui.athlete

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.maurozegarra.master.AthleteViewModel
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.model.ExerciseRecord
import com.maurozegarra.master.model.SessionLog
import com.maurozegarra.master.model.SessionStatus
import com.maurozegarra.master.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Historial de sesiones (completas y parciales). Lee `vm.sessions` (recargado al
 * abrir desde el store). Agrupa por día con encabezados relativos (Today/Yesterday).
 * Fila expandible con detalle de ejercicios y series. Badge Partial en sesiones
 * incompletas. Tap en un ejercicio abre ExerciseHistoryScreen.
 */
@Composable
fun HistoryScreen(vm: AthleteViewModel, accent: Color, t: Strings) {
    val sessions = vm.sessions

    if (sessions.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(t.historyEmpty, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text(t.historyEmptyHint, color = AppTheme.colors.textDim, fontSize = 14.sp)
        }
        return
    }

    val zone = remember { ZoneId.systemDefault() }
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    // Se reagrupa cuando cambia la lista (snapshot inmutable como key).
    val groups = remember(sessions.toList()) {
        sessions
            .sortedByDescending { it.completedAt }
            .groupBy { Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate() }
            .toList()
            .sortedByDescending { it.first }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "count") {
                Text(
                    "${sessions.size} ${t.sessionsCount}",
                    color = AppTheme.colors.textDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            groups.forEach { (date, items) ->
                item(key = "hdr-$date") {
                    Text(
                        dayLabel(date, zone, t),
                        color = AppTheme.colors.textDim,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                items(items, key = { it.id }) { s ->
                    SessionRow(
                        session = s,
                        time = Instant.ofEpochMilli(s.completedAt).atZone(zone).format(timeFmt),
                        accent = accent,
                        t = t,
                        onDelete = { vm.deleteSession(s.id) },
                        onExerciseClick = { exerciseId -> vm.openExerciseHistory(exerciseId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionLog,
    time: String,
    accent: Color,
    t: Strings,
    onDelete: () -> Unit,
    onExerciseClick: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.surface)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        session.trainingName.ifBlank { t.noName },
                        color = AppTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    val isPartial = session.status == SessionStatus.PARTIAL
                    val trainingBadgeColor = if (isPartial) accent else Color(0xFF4CAF50)
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(trainingBadgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            if (isPartial) t.partial else t.complete,
                            color = trainingBadgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(time, color = AppTheme.colors.textDim, fontSize = 13.sp)
            }
            if (session.exercises.isNotEmpty()) {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = AppTheme.colors.textDim,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null, tint = AppTheme.colors.textDim)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(t.delete) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }

        AnimatedVisibility(
            visible = expanded && session.exercises.isNotEmpty(),
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val workoutGroups = session.exercises.groupBy { it.workoutIndex }
                    .toSortedMap()
                workoutGroups.forEach { (workoutIndex, exs) ->
                    WorkoutGroupSection(
                        exercises = exs,
                        accent = accent,
                        t = t,
                        onExerciseClick = onExerciseClick,
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = AppTheme.colors.surface,
            titleContentColor = AppTheme.colors.textPrimary,
            textContentColor = AppTheme.colors.textDim,
            title = { Text(t.delete) },
            text = { Text(t.deleteSessionConfirm) },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmDelete = false }) {
                    Text(t.delete, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(t.cancel, color = AppTheme.colors.textDim)
                }
            },
        )
    }
}

@Composable
private fun WorkoutGroupSection(
    exercises: List<ExerciseRecord>,
    accent: Color,
    t: Strings,
    onExerciseClick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val workoutName = exercises.firstOrNull()?.workoutName?.ifBlank { t.workout } ?: t.workout
    val allComplete = exercises.size == (exercises.firstOrNull()?.totalExercisesInWorkout ?: exercises.size) &&
        exercises.all { it.setsCompleted == it.totalSets }
    val badgeColor = if (allComplete) Color(0xFF4CAF50) else accent

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.colors.bg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                workoutName,
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    if (allComplete) t.complete else t.partial,
                    color = badgeColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = AppTheme.colors.textDim,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                exercises.forEach { er ->
                    ExerciseDetailRow(er, accent, t) { onExerciseClick(er.exerciseId) }
                }
            }
        }
    }
}

@Composable
private fun ExerciseDetailRow(er: ExerciseRecord, accent: Color, t: Strings, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                er.name,
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${er.setsCompleted}/${er.totalSets} ${t.setsShort}",
                color = AppTheme.colors.textDim,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        er.sets.forEachIndexed { i, sr ->
            val setLabel = "Set ${i + 1}"
            val detail = if (er.timeBased) {
                if (sr.weightKg > 0) "$setLabel  ·  ${sr.reps} reps  ·  ${fmtKgHistory(sr.weightKg)} ${t.kg}  ·  ${sr.durationSec}s"
                else "$setLabel  ·  ${sr.reps} reps  ·  ${sr.durationSec}s"
            } else {
                if (sr.weightKg > 0) "$setLabel  ·  ${sr.reps} ${t.repLabel}  ·  ${fmtKgHistory(sr.weightKg)} ${t.kg}"
                else "$setLabel  ·  ${sr.reps} ${t.repLabel}"
            }
            Text(detail, color = AppTheme.colors.textDim, fontSize = 12.sp)
        }
        if (er.feedbackDeltaKg != null && er.feedbackDeltaKg != 0.0) {
            Spacer(Modifier.height(2.dp))
            val arrow = if (er.feedbackDeltaKg > 0) "\u2191" else "\u2193"
            Text(
                "Feedback: $arrow ${fmtKgHistory(kotlin.math.abs(er.feedbackDeltaKg))} ${t.kg}",
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun fmtKgHistory(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

/** Etiqueta relativa del día: Today/Yesterday o fecha larga localizada. */
private fun dayLabel(date: LocalDate, zone: ZoneId, t: Strings): String {
    val today = LocalDate.now(zone)
    return when (date) {
        today -> t.today
        today.minusDays(1) -> t.yesterday
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(t.locale))
    }
}
