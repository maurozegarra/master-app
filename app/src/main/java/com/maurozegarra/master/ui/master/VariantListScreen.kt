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
import com.maurozegarra.master.model.WorkoutVariant
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
import com.maurozegarra.master.ui.theme.AppTheme

/** Editor de un workout rotativo: lista de variantes que se alternan al completar. */
@Composable
fun VariantListScreen(vm: MasterViewModel, accent: Color, t: Strings) {
    val workout = vm.editingWorkout() ?: return
    val listState = rememberLazyListState()
    // El campo de nombre es el item 0 (fijo); las variantes arrastrables empiezan en 1.
    val dragDropState = rememberDragDropState(listState) { from, to ->
        vm.moveVariant(from - 1, to - 1)
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
                    placeholder = { Text(t.workoutNameHint, color = AppTheme.colors.textFaded) },
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
                workout.variants,
                key = { _, v -> v.id },
                contentType = { _, _ -> ReorderableContentType },
            ) { index, v ->
                DraggableItem(dragDropState, index + 1) { _ ->
                    VariantRow(
                        variant = v,
                        index = index,
                        t = t,
                        swipeController = swipeController,
                        onOpen = { vm.openVariant(v.id) },
                        onDuplicate = { vm.duplicateVariant(v.id) },
                        onDelete = { vm.deleteVariant(v.id) },
                    )
                }
            }

            item {
                AddButton(label = t.addVariant, accent = accent, onClick = { vm.addVariant() })
            }
        }

        PrimaryButton(
            label = t.save,
            accent = accent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            onClick = { vm.closeWorkoutEditor() },
        )
    }
}

@Composable
private fun VariantRow(
    variant: WorkoutVariant,
    index: Int,
    t: Strings,
    swipeController: SwipeRowsController,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    // Dos acciones, no tres: una variante no se puede volver rotativa. El componente
    // acepta un número variable justo por esto.
    val actions = listOf(
        SwipeAction(Icons.Filled.Delete, ACTION_DELETE, t.delete, onDelete),
        SwipeAction(Icons.Filled.ContentCopy, ACTION_DUPLICATE, t.duplicate, onDuplicate),
    )

    SwipeActionsRow(actions = actions, controller = swipeController) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AppTheme.colors.surface)
                // Con otra fila abierta, el primer tap solo la cierra.
                .clickable { if (!swipeController.consumeTapIfOpen()) onOpen() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    variant.name.ifBlank { "${t.variant} ${index + 1}" },
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Text("${variant.exercises.size} ${t.exercise}", color = AppTheme.colors.textDim, fontSize = 13.sp)
            }
        }
    }
}
