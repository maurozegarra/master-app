package com.maurozegarra.master.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.MasterViewModel
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.model.Workout
import com.maurozegarra.master.ui.DraggableItem
import com.maurozegarra.master.ui.SwipeAction
import com.maurozegarra.master.ui.SwipeActionsRow
import com.maurozegarra.master.ui.SwipeRowsController
import com.maurozegarra.master.ui.rememberSwipeRowsController
import com.maurozegarra.master.ui.ReorderableContentType
import com.maurozegarra.master.ui.dragContainer
import com.maurozegarra.master.ui.rememberDragDropState
import com.maurozegarra.master.ui.theme.ACTION_DELETE
import com.maurozegarra.master.ui.theme.ACTION_DUPLICATE
import com.maurozegarra.master.ui.theme.ACTION_EDIT
import com.maurozegarra.master.ui.theme.AppTheme

@Composable
fun TrainingEditorScreen(vm: MasterViewModel, accent: Color, t: Strings) {
    val draft = vm.draft ?: return
    val listState = rememberLazyListState()
    // El campo de nombre es el item 0 (fijo); los workouts arrastrables empiezan en 1.
    val dragDropState = rememberDragDropState(listState) { from, to ->
        vm.moveWorkout(from - 1, to - 1)
    }
    val swipeController = rememberSwipeRowsController()

    // Al hacer scroll se cierra la fila abierta: dejarla abierta fuera de vista significa
    // volver a encontrarla asi mas tarde, sin recordar por que.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) swipeController.closeAll()
    }

    Box(
        Modifier
            .fillMaxSize()
            // Un tap en cualquier zona vacia cierra el panel abierto. Solo mientras hay
            // algo abierto: detectTapGestures ignora los taps que un hijo ya consumio.
            .then(
                if (swipeController.isAnyOpen) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { swipeController.closeAll() }
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().dragContainer(dragDropState),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { vm.setTrainingName(it) },
                    placeholder = { Text(t.trainingNameHint, color = AppTheme.colors.textFaded) },
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
            }

            itemsIndexed(
                draft.workouts,
                key = { _, it -> it.id },
                contentType = { _, _ -> ReorderableContentType },
            ) { index, w ->
                DraggableItem(dragDropState, index + 1) { _ ->
                    WorkoutRow(
                        workout = w,
                        t = t,
                        swipeController = swipeController,
                        onOpen = { vm.openWorkout(w.id) },
                        onDuplicate = { vm.duplicateWorkout(w.id) },
                        onDelete = { vm.deleteWorkout(w.id) },
                        onToggleRotating = {
                            if (w.rotating) vm.makeWorkoutSimple(w.id) else vm.makeWorkoutRotating(w.id)
                        },
                    )
                }
            }

            item {
                AddButton(label = t.addWorkout, accent = accent, onClick = { vm.addWorkout() })
            }

            // Sin otros trainings con contenido no hay nada que copiar: se oculta en vez de
            // abrir un selector vacío (caso del primer arranque).
            if (vm.workoutPickerSources().isNotEmpty()) {
                item {
                    AddButton(
                        label = t.addFromExisting,
                        accent = accent,
                        onClick = { vm.openWorkoutPicker() },
                    )
                }
            }
        }

        PrimaryButton(
            label = t.saveTraining,
            accent = accent,
            enabled = vm.canSaveTraining,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            onClick = { vm.saveTraining() },
        )
    }
}

@Composable
private fun WorkoutRow(
    workout: Workout,
    t: Strings,
    swipeController: SwipeRowsController,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onToggleRotating: () -> Unit,
) {
    // Mismo orden que en TrainingCard: el destructivo en el extremo más lejano al pulgar
    // cuando la fila apenas se abre. La tercera acción es alternar rotativo porque
    // "abrir" ya es el tap de la fila entera.
    val actions = listOf(
        SwipeAction(Icons.Filled.Delete, ACTION_DELETE, t.delete, onDelete),
        SwipeAction(Icons.Filled.ContentCopy, ACTION_DUPLICATE, t.duplicate, onDuplicate),
        SwipeAction(
            icon = if (workout.rotating) Icons.Filled.SyncDisabled else Icons.Filled.Sync,
            tint = ACTION_EDIT,
            label = if (workout.rotating) t.makeSimple else t.makeRotating,
            onClick = onToggleRotating,
        ),
    )
    val subtitle = if (workout.rotating) {
        workout.variants.joinToString(" / ") { it.name.ifBlank { t.variant } }
            .ifBlank { "${workout.variants.size} ${t.variant}" }
    } else {
        "${workout.exercises.size} ${t.exercise}"
    }
    SwipeActionsRow(actions = actions, controller = swipeController) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AppTheme.colors.surface)
                // Con otra fila abierta, el primer tap solo la cierra: si no, tocar esta
                // fila abriría su workout cuando la intención era descartar el panel.
                .clickable { if (!swipeController.consumeTapIfOpen()) onOpen() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    workout.name.ifBlank { t.workout },
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Text(subtitle, color = AppTheme.colors.textDim, fontSize = 13.sp)
            }
        }
    }
}
