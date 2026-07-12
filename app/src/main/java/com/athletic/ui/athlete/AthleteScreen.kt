package com.athletic.ui.athlete

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.athletic.AthleteViewModel
import com.athletic.i18n.Strings
import com.athletic.model.Training
import com.athletic.model.hasContent
import com.athletic.ui.DraggableItem
import com.athletic.ui.ReorderableContentType
import com.athletic.ui.dragContainer
import com.athletic.ui.rememberDragDropState
import com.athletic.ui.theme.AppTheme
import com.athletic.util.formatRemaining

/** Router de la sección Athlete según el estado de navegación del ViewModel. */
@Composable
fun AthleteScreen(vm: AthleteViewModel, accent: Color, t: Strings, onStart: () -> Unit = { vm.startPlayerRun() }) {
    when {
        vm.playerTrainingId != null -> PlayerScreen(vm, accent, t, onStart)
        vm.showingHistory -> HistoryScreen(vm, accent, t)
        vm.choosingExercise -> ChooseExerciseScreen(vm, accent, t)
        vm.editingExerciseId != null -> ExerciseEditorScreen(vm, accent, t)
        vm.editingVariantId != null -> WorkoutEditorScreen(vm, accent, t)
        vm.editingWorkoutId != null && vm.editingWorkout()?.rotating == true -> VariantListScreen(vm, accent, t)
        vm.editingWorkoutId != null -> WorkoutEditorScreen(vm, accent, t)
        vm.draft != null -> TrainingEditorScreen(vm, accent, t)
        else -> TrainingsList(vm, accent, t)
    }
}

@Composable
private fun TrainingsList(vm: AthleteViewModel, accent: Color, t: Strings) {
    Box(Modifier.fillMaxSize()) {
        if (vm.trainings.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(t.emptyTrainings, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(t.savedHint, color = AppTheme.colors.textDim, fontSize = 14.sp)
            }
        } else {
            val listState = rememberLazyListState()
            val dragDropState = rememberDragDropState(listState) { from, to ->
                vm.moveTraining(from, to)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().dragContainer(dragDropState),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(
                    vm.trainings,
                    key = { _, it -> it.id },
                    contentType = { _, _ -> ReorderableContentType },
                ) { index, tr ->
                    DraggableItem(dragDropState, index) { _ ->
                        TrainingCard(
                            training = tr,
                            accent = accent,
                            t = t,
                            isActive = vm.activePlayerTrainingId == tr.id,
                            onPlay = { vm.openPlayer(tr.id) },
                            onEdit = { vm.startEditTraining(tr.id) },
                            onDuplicate = { vm.duplicateTraining(tr.id) },
                            onDelete = { vm.deleteTraining(tr.id) },
                        )
                    }
                }
            }
        }

        val activeId = vm.activePlayerTrainingId
        if (activeId != null && vm.playerStep != null) {
            MiniPlayer(
                vm = vm,
                accent = accent,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp, 0.dp, 16.dp, 80.dp),
                onOpen = { vm.openPlayer(activeId) },
            )
        }

        PrimaryButton(
            label = "+  ${t.createTraining}",
            accent = accent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            onClick = { vm.startNewTraining() },
        )
    }
}

@Composable
private fun TrainingCard(
    training: Training,
    accent: Color,
    t: Strings,
    isActive: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val exercises = training.workouts.sumOf { w ->
        if (w.variants.isNotEmpty()) w.variants.sumOf { it.exercises.size } else w.exercises.size
    }
    val canPlay = training.workouts.any { it.hasContent() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppTheme.colors.surface)
            .clickable(enabled = canPlay, onClick = onPlay)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    training.name.ifBlank { t.noName },
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                if (isActive) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("IN PROGRESS", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                "${training.workouts.size} ${t.workout} · $exercises ${t.exercise}",
                color = AppTheme.colors.textDim,
                fontSize = 13.sp,
            )
        }

        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = AppTheme.colors.textDim)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(t.edit) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text(t.duplicate) }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text(t.delete) }, onClick = { menu = false; onDelete() })
            }
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (canPlay) accent else AppTheme.colors.surface)
                .clickable(enabled = canPlay, onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = t.start,
                tint = if (canPlay) AppTheme.colors.onAccent else AppTheme.colors.textDim,
            )
        }
    }
}

@Composable
private fun MiniPlayer(vm: AthleteViewModel, accent: Color, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val step = vm.playerStep ?: return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.colors.surface)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                step.title.ifBlank { step.workoutName },
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
            )
            Text(
                if (step.manual) "${step.reps} reps" else formatRemaining(vm.playerRemainingMs),
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(onClick = { if (vm.playerRunning) vm.pausePlayer() else vm.resumePlayer() }) {
            Icon(
                if (vm.playerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = AppTheme.colors.textPrimary,
            )
        }
        IconButton(onClick = { vm.closePlayer() }) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = AppTheme.colors.textDim)
        }
    }
}
