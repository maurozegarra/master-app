package com.maurozegarra.master.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
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
import com.maurozegarra.master.data.ExerciseCatalog
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.model.Exercise
import com.maurozegarra.master.model.WorkMode
import com.maurozegarra.master.ui.DraggableItem
import com.maurozegarra.master.ui.ExerciseThumb
import com.maurozegarra.master.ui.ReorderableContentType
import com.maurozegarra.master.ui.SwipeAction
import com.maurozegarra.master.ui.SwipeActionsRow
import com.maurozegarra.master.ui.SwipeRowsController
import com.maurozegarra.master.ui.dragContainer
import com.maurozegarra.master.ui.rememberDragDropState
import com.maurozegarra.master.ui.rememberSwipeRowsController
import com.maurozegarra.master.ui.theme.Dims
import com.maurozegarra.master.ui.theme.AppTheme

@Composable
fun WorkoutEditorScreen(vm: MasterViewModel, accent: Color, t: Strings) {
    vm.editingWorkout() ?: return
    val inVariant = vm.editingVariantId != null
    val exercises = vm.editorExercises()
    val listState = rememberLazyListState()
    // El campo de nombre es el item 0 (fijo); los ejercicios arrastrables empiezan en 1.
    val dragDropState = rememberDragDropState(listState) { from, to ->
        vm.moveExercise(from - 1, to - 1)
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
            // Un tap en cualquier zona vacia cierra el panel abierto.
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
                    value = vm.editorName(),
                    onValueChange = { vm.setEditorName(it) },
                    placeholder = { Text(if (inVariant) t.variantNameHint else t.workoutNameHint, color = AppTheme.colors.textFaded) },
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
                exercises,
                key = { _, it -> it.id },
                contentType = { _, _ -> ReorderableContentType },
            ) { index, ex ->
                DraggableItem(dragDropState, index + 1) { _ ->
                    ExerciseRow(
                        exercise = ex,
                        t = t,
                        videoFor = vm::videoFileFor,
                        swipeController = swipeController,
                        onOpen = { vm.openExercise(ex.id) },
                        onDuplicate = { vm.duplicateExercise(ex.id) },
                        onDelete = { vm.deleteExercise(ex.id) },
                    )
                }
            }

            item {
                AddButton(label = t.addExercise, accent = accent, onClick = { vm.openExercisePicker() })
            }
        }

        PrimaryButton(
            label = t.save,
            accent = accent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            onClick = { if (inVariant) vm.closeVariantEditor() else vm.closeWorkoutEditor() },
        )
    }
}

private fun workSummary(ex: Exercise, t: Strings): String {
    val work = if (ex.workMode == WorkMode.TIME) fmtSec(ex.workValue) else "${ex.workValue} ${t.repsUnit}"
    return "${ex.sets} × $work"
}

@Composable
private fun ExerciseRow(
    exercise: Exercise,
    t: Strings,
    /** El vídeo ya descargado de ese ejercicio, o null. Lambda y no el ViewModel: la fila solo pinta. */
    videoFor: (String) -> java.io.File?,
    swipeController: SwipeRowsController,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    // Mismo orden que en las otras listas: el destructivo en el extremo más lejano al
    // pulgar cuando la fila apenas se abre. "Abrir" no es una acción del panel porque ya
    // es el tap de la fila entera.
    val actions = listOf(
        SwipeAction(Icons.Outlined.Delete, t.delete, onDelete),
        SwipeAction(Icons.Outlined.ContentCopy, t.duplicate, onDuplicate),
    )

    SwipeActionsRow(actions = actions, controller = swipeController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.row))
            .background(AppTheme.colors.surface)
            // Con el panel abierto, el primer tap lo cierra en vez de abrir el ejercicio:
            // si no, tocar la fila para cerrar te metía en el editor sin querer.
            .clickable { if (!swipeController.consumeTapIfOpen()) onOpen() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val exLabel = ExerciseCatalog.display(exercise.exerciseId, exercise.name, t.locale.language)
        // Respeta `showVideo` de ESTA instancia: si le apagaste el vídeo en este training,
        // la fila enseña el emoji, igual que el player y que el preview.
        val thumb = if (exercise.showVideo) videoFor(exercise.exerciseId) else null
        if (thumb != null) {
            ExerciseThumb(file = thumb, sizeDp = 44)
        } else {
            ExerciseGlyph(name = exLabel, color = exercise.workCfg.color, exerciseId = exercise.exerciseId)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                exLabel.ifBlank { t.exercise },
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Text(workSummary(exercise, t), color = AppTheme.colors.textDim, fontSize = 13.sp)
        }
    }
    }
}
