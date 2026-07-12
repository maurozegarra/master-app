package com.athletic.ui.athlete

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athletic.AthleteViewModel
import com.athletic.i18n.Strings
import com.athletic.model.AlarmSound
import com.athletic.model.ConfirmMode
import com.athletic.model.DisplayMode
import com.athletic.model.Exercise
import com.athletic.model.StageConfig
import com.athletic.model.StepKind
import com.athletic.model.WeightType
import com.athletic.model.WorkMode
import com.athletic.model.WorkSet
import com.athletic.model.setAt
import com.athletic.ui.AppStepButton
import com.athletic.ui.SwitchRow
import com.athletic.ui.theme.AppTheme

@Composable
fun ExerciseEditorScreen(vm: AthleteViewModel, accent: Color, t: Strings) {
    val initial = vm.editingExercise() ?: return
    var ex by remember(initial.id) { mutableStateOf(initial) }
    // Acordeon: una sola etapa abierta a la vez; null = todas colapsadas (por defecto).
    var openStage by remember(initial.id) { mutableStateOf<StepKind?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { GeneralCard(ex, accent, t) { ex = it } }
            listOf(StepKind.PREP, StepKind.WORK, StepKind.REST, StepKind.COOLDOWN).forEach { kind ->
                item(key = kind) {
                    StageSection(
                        kind = kind,
                        ex = ex,
                        expanded = openStage == kind,
                        onToggle = { openStage = if (openStage == kind) null else kind },
                        accent = accent,
                        t = t,
                        vm = vm,
                    ) { ex = it }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppTheme.colors.surface)
                    .clickable { vm.deleteExercise(ex.id); vm.closeExerciseEditor() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = t.delete, tint = AppTheme.colors.textDim)
            }
            PrimaryButton(
                label = t.save,
                accent = accent,
                modifier = Modifier.weight(1f),
                onClick = { vm.saveExercise(ex) },
            )
        }
    }
}

@Composable
private fun GeneralCard(ex: Exercise, accent: Color, t: Strings, onChange: (Exercise) -> Unit) {
    SectionCard {
        ExerciseNoteField(ex, accent, t, onChange)
        VSpace(14)
        Stepper(t.setsLabel, ex.sets, accent, min = 1, max = 30) { onChange(ex.copy(sets = it)) }
    }
}

/** Tarjeta colapsable de una etapa: campos basicos + "Opciones avanzadas". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StageSection(
    kind: StepKind,
    ex: Exercise,
    expanded: Boolean,
    onToggle: () -> Unit,
    accent: Color,
    t: Strings,
    vm: AthleteViewModel,
    onChange: (Exercise) -> Unit,
) {
    val cfg = when (kind) {
        StepKind.PREP -> ex.prepareCfg
        StepKind.WORK -> ex.workCfg
        StepKind.REST -> ex.restCfg
        StepKind.COOLDOWN -> ex.cooldownCfg
    }
    val title = when (kind) {
        StepKind.PREP -> t.prepare
        StepKind.WORK -> t.work
        StepKind.REST -> t.rest
        StepKind.COOLDOWN -> t.cooldown
    }
    var showColors by remember { mutableStateOf(false) }
    var pendingColor by remember { mutableStateOf<Long?>(null) }
    val onCfg: (StageConfig) -> Unit = { c ->
        onChange(
            when (kind) {
                StepKind.PREP -> ex.copy(prepareCfg = c)
                StepKind.WORK -> ex.copy(workCfg = c)
                StepKind.REST -> ex.copy(restCfg = c)
                StepKind.COOLDOWN -> ex.copy(cooldownCfg = c)
            },
        )
    }
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(cfg.color))
                    .clickable { showColors = true },
            )
            Spacer(Modifier.width(10.dp))
            Text(title, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = AppTheme.colors.textDim,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                VSpace(14)
                StageBasics(kind, ex, accent, t, onChange)
                VSpace(14)
                StageAdvanced(kind, ex, cfg, accent, t, vm, onChange, onColorClick = { showColors = true })
            }
        }
    }

    if (showColors) {
        ModalBottomSheet(
            onDismissRequest = { showColors = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AppTheme.colors.bg,
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(STAGE_COLORS) { c ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .clickable {
                                pendingColor = c
                                showColors = false
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (c == cfg.color) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
            VSpace(16)
        }
    }

    pendingColor?.let { color ->
        AlertDialog(
            onDismissRequest = { pendingColor = null },
            title = { Text(t.applyColorTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onCfg(cfg.copy(color = color))
                                pendingColor = null
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(Color(color)))
                        Spacer(Modifier.width(12.dp))
                        Text(t.applyColorThisExercise, color = AppTheme.colors.textPrimary, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onCfg(cfg.copy(color = color))
                                vm.applyColorToTraining(kind, color)
                                pendingColor = null
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(Color(color)))
                        Spacer(Modifier.width(12.dp))
                        Text(t.applyColorAllTraining, color = AppTheme.colors.textPrimary, fontSize = 15.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingColor = null }) { Text("Cancel") }
            },
            containerColor = AppTheme.colors.surface,
        )
    }
}

/** Campos basicos de cada etapa (duracion / modo / reps / peso). */
@Composable
private fun StageBasics(kind: StepKind, ex: Exercise, accent: Color, t: Strings, onChange: (Exercise) -> Unit) {
    when (kind) {
        StepKind.PREP ->
            DurationWheelField(t.secUnit, ex.prepareSec, accent, t, max = 1800) { onChange(ex.copy(prepareSec = it)) }
        StepKind.WORK -> {
            SegmentToggle(
                options = listOf("TIME" to t.secUnit, "REPS" to t.repsUnit),
                selected = ex.workMode.name,
                accent = accent,
            ) { onChange(ex.copy(workMode = WorkMode.valueOf(it))) }
            VSpace(10)
            if (ex.workMode == WorkMode.TIME) {
                DurationWheelField(t.secUnit, ex.workValue, accent, t, min = 1, max = 36000) { onChange(ex.copy(workValue = it)) }
            } else {
                Stepper(t.repsUnit, ex.workValue, accent, min = 1, max = 200) { onChange(ex.copy(workValue = it)) }
                VSpace(14)
                WeightSection(ex, accent, t, onChange)
            }
        }
        StepKind.REST -> {
            DurationWheelField(t.secUnit, ex.restSec, accent, t, max = 1800) { onChange(ex.copy(restSec = it)) }
            // Divulgacion progresiva: "skip rest on last set" solo aplica si hay descanso.
            if (ex.restSec > 0) {
                VSpace(10)
                SwitchRow(t.restSkipLast, null, ex.restSkipOnLastSet, accent) { onChange(ex.copy(restSkipOnLastSet = it)) }
            }
        }
        StepKind.COOLDOWN ->
            DurationWheelField(t.secUnit, ex.cooldownSec, accent, t, max = 1800) { onChange(ex.copy(cooldownSec = it)) }
    }
}

@Composable
private fun WeightSection(ex: Exercise, accent: Color, t: Strings, onChange: (Exercise) -> Unit) {
    Column {
        Text(t.weight, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        VSpace(10)
        SegmentToggle(
            options = listOf(
                "NONE" to t.weightNone,
                "TOTAL" to t.weightTotalLabel,
                "BARBELL" to t.weightBarbell,
                "DUMBBELL" to t.weightDumbbell,
            ),
            selected = ex.weightType.name,
            accent = accent,
        ) { sel ->
            val type = WeightType.valueOf(sel)
            val list = if (type == WeightType.NONE) emptyList()
            else (0 until ex.sets).map { ex.setAt(it) }
            onChange(ex.copy(weightType = type, setList = list))
        }

        if (ex.weightType == WeightType.BARBELL) {
            VSpace(12)
            WeightStepper(t.barWeight, ex.barWeight, accent) { onChange(ex.copy(barWeight = it)) }
        }

        if (ex.weightType != WeightType.NONE) {
            VSpace(12)
            val sets = (0 until ex.sets).map { ex.setAt(it) }
            sets.forEachIndexed { i, ws ->
                SetRow(
                    index = i,
                    set = ws,
                    type = ex.weightType,
                    accent = accent,
                    t = t,
                    onChange = { newSet ->
                        val list = sets.toMutableList().also { it[i] = newSet }
                        onChange(ex.copy(setList = list))
                    },
                )
                if (i < sets.size - 1) VSpace(8)
            }
        }
    }
}

@Composable
private fun SetRow(
    index: Int,
    set: WorkSet,
    type: WeightType,
    accent: Color,
    t: Strings,
    onChange: (WorkSet) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.colors.track)
            .padding(12.dp),
    ) {
        Text("SET ${index + 1}", color = AppTheme.colors.textDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        VSpace(6)
        Stepper(t.repsUnit, set.reps, accent, min = 1, max = 200) { onChange(set.copy(reps = it)) }
        VSpace(8)
        val label = when (type) {
            WeightType.DUMBBELL -> t.perHand
            WeightType.BARBELL -> t.plates
            else -> t.weight
        }
        WeightStepper(label, set.weight, accent) { onChange(set.copy(weight = it)) }
    }
}

@Composable
private fun WeightStepper(label: String, value: Double, accent: Color, onChange: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AppTheme.colors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        AppStepButton("−", accent) { onChange((value - 2.5).coerceAtLeast(0.0)) }
        Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) {
            Text("${fmtKg(value)} kg", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        AppStepButton("+", accent) { onChange(value + 2.5) }
    }
}

/** Subseccion colapsable "Opciones avanzadas" dentro de cada etapa. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StageAdvanced(
    kind: StepKind,
    ex: Exercise,
    cfg: StageConfig,
    accent: Color,
    t: Strings,
    vm: AthleteViewModel,
    onChange: (Exercise) -> Unit,
    onColorClick: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val onCfg: (StageConfig) -> Unit = { c ->
        onChange(
            when (kind) {
                StepKind.PREP -> ex.copy(prepareCfg = c)
                StepKind.WORK -> ex.copy(workCfg = c)
                StepKind.REST -> ex.copy(restCfg = c)
                StepKind.COOLDOWN -> ex.copy(cooldownCfg = c)
            },
        )
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t.advancedOptions, color = AppTheme.colors.textDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = AppTheme.colors.textDim,
            )
        }
        AnimatedVisibility(visible = open) {
            Column {
                VSpace(12)
                // Solo Work por repeticiones: segundos estimados por rep (pondera la barra de progreso).
                if (kind == StepKind.WORK && ex.workMode == WorkMode.REPS) {
                    Stepper(t.secPerRepLabel, ex.secPerRep, accent, min = 1, max = 30) { onChange(ex.copy(secPerRep = it)) }
                    VSpace(12)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.color, color = AppTheme.colors.textDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(cfg.color))
                            .clickable { onColorClick() },
                    )
                }
                VSpace(12)
                Text(t.displayLabel, color = AppTheme.colors.textDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                VSpace(6)
                SegmentToggle(
                    options = listOf(
                        "COUNTDOWN" to t.displayCountdown,
                        "STATIC" to t.displayStatic,
                        "COUNTUP" to t.displayCountup,
                    ),
                    selected = cfg.display.name,
                    accent = accent,
                ) { onCfg(cfg.copy(display = DisplayMode.valueOf(it))) }
                VSpace(12)
                SwitchRow(t.alarmLabel, null, cfg.alarm, accent) { onCfg(cfg.copy(alarm = it)) }
                VSpace(10)
                Stepper(t.finalCountLabel, cfg.finalCount, accent, min = 0, max = 10) { onCfg(cfg.copy(finalCount = it)) }
                // Divulgacion progresiva: el control de volumen y sonido del beep
                // solo aparece cuando finalCount > 0.
                if (cfg.finalCount > 0) {
                    VSpace(10)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.beepVolumeLabel, color = AppTheme.colors.textDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${(cfg.beepVolume * 100).toInt()}%", color = AppTheme.colors.textDim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = cfg.beepVolume,
                        onValueChange = { onCfg(cfg.copy(beepVolume = it)) },
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = AppTheme.colors.track,
                        ),
                    )
                    VSpace(8)
                    var showBeepPicker by remember { mutableStateOf(false) }
                    BeepSoundRow(
                        label = t.beepSoundLabel,
                        value = cfg.beepSoundName ?: t.defaultSound,
                        accent = accent,
                        onClick = { showBeepPicker = true },
                    )
                    if (showBeepPicker) {
                        BeepSoundPickerDialog(
                            vm = vm,
                            currentUri = cfg.beepSoundUri,
                            volume = cfg.beepVolume,
                            t = t,
                            accent = accent,
                            onDismiss = { showBeepPicker = false },
                            onSelect = { uri, name -> onCfg(cfg.copy(beepSoundUri = uri, beepSoundName = name)); showBeepPicker = false },
                        )
                    }
                }
                VSpace(12)
                Text(t.advanceLabel, color = AppTheme.colors.textDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                VSpace(6)
                SegmentToggle(
                    options = listOf("AUTO" to t.advanceAuto, "MANUAL" to t.advanceManual),
                    selected = cfg.confirm.name,
                    accent = accent,
                ) { onCfg(cfg.copy(confirm = ConfirmMode.valueOf(it))) }
            }
        }
    }
}

@Composable
private fun ExerciseNoteField(ex: Exercise, accent: Color, t: Strings, onChange: (Exercise) -> Unit) {
    OutlinedTextField(
        value = ex.note,
        onValueChange = { onChange(ex.copy(note = it)) },
        label = { Text(t.noteLabel, color = AppTheme.colors.textDim) },
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

@Composable
private fun BeepSoundRow(label: String, value: String, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.colors.track)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = AppTheme.colors.textPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppTheme.colors.textDim,
        )
    }
}

@Composable
private fun BeepSoundPickerDialog(
    vm: AthleteViewModel,
    currentUri: String?,
    volume: Float,
    t: Strings,
    accent: Color,
    onDismiss: () -> Unit,
    onSelect: (uri: String?, name: String?) -> Unit,
) {
    val sounds = remember { vm.loadAlarmSounds() }
    var selectedUri by remember { mutableStateOf(currentUri) }

    fun stopAndDismiss() {
        vm.stopBeepPreview()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = { stopAndDismiss() },
        containerColor = AppTheme.colors.surface,
        title = {
            Text(t.beepSoundLabel, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold)
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                // Option: Default (built-in beep_second.ogg)
                item {
                    val selected = selectedUri == null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedUri = null }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { selectedUri = null },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = accent,
                                unselectedColor = AppTheme.colors.textFaded,
                            ),
                        )
                        Text(
                            t.defaultSound,
                            color = AppTheme.colors.textPrimary,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                items(sounds) { sound ->
                    val selected = sound.uri == selectedUri
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedUri = sound.uri
                                vm.previewBeepTone(sound.uri, volume)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = {
                                selectedUri = sound.uri
                                vm.previewBeepTone(sound.uri, volume)
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = accent,
                                unselectedColor = AppTheme.colors.textFaded,
                            ),
                        )
                        Text(
                            sound.name,
                            color = AppTheme.colors.textPrimary,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.previewBeepTone(sound.uri, volume) }) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = t.previewTone,
                                modifier = Modifier.size(24.dp),
                                tint = accent,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val name = sounds.firstOrNull { it.uri == selectedUri }?.name
                    onSelect(selectedUri, name)
                    vm.stopBeepPreview()
                },
            ) {
                Text(t.select, color = accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = { stopAndDismiss() }) {
                Text(t.cancel, color = AppTheme.colors.textDim)
            }
        },
    )
}
