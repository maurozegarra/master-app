package com.athletic.ui.athlete

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athletic.AthleteViewModel
import com.athletic.data.ExerciseCatalog
import com.athletic.i18n.Strings
import com.athletic.ui.AnimatedGlowBorder
import com.athletic.ui.glowColors
import com.athletic.model.DisplayMode
import com.athletic.model.PlayerStep
import com.athletic.model.StepKind
import com.athletic.ui.theme.AppTheme
import com.athletic.ui.theme.ON_ACCENT
import com.athletic.ui.theme.SURFACE
import com.athletic.ui.theme.TEXT_DIM
import com.athletic.ui.theme.TRACK
import com.athletic.util.formatPlayerClock
import com.athletic.util.formatRemaining
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(vm: AthleteViewModel, accent: Color, t: Strings) {
    when {
        vm.playerFinished -> FinishedView(vm, accent, t)
        !vm.playerStarted -> PreviewView(vm, accent, t)
        else -> RunningView(vm, accent, t)
    }
}

private data class PreviewExercise(val name: String, val exerciseId: String, val meta: String)

private data class PreviewGroup(
    val index: Int,
    val title: String,
    val rotating: Boolean,
    val variant: String,
    val durationSec: Int,
    val exercises: List<PreviewExercise>,
)

private fun metaFor(s: PlayerStep): String = when {
    s.timeBased -> formatRemaining(s.durationSec * 1000L)
    s.totalSets > 1 && s.reps > 1 -> "${s.totalSets}×${s.reps}"
    s.totalSets > 1 -> "${s.totalSets}×"
    s.reps > 0 -> "×${s.reps}"
    else -> ""
}

private fun buildPreviewGroups(steps: List<PlayerStep>): List<PreviewGroup> =
    steps.groupBy { it.workoutIndex }.entries.sortedBy { it.key }.map { (idx, list) ->
        val first = list.first()
        val exercises = list.filter { it.kind == StepKind.WORK }
            .distinctBy { it.ownerName + "|" + it.ownerExerciseId }
            .map { s -> PreviewExercise(s.ownerName, s.ownerExerciseId, metaFor(s)) }
        PreviewGroup(
            index = idx,
            title = first.workoutBaseName.ifBlank { first.workoutName },
            rotating = first.rotating,
            variant = first.variantName,
            durationSec = list.sumOf { it.durationSec },
            exercises = exercises,
        )
    }

@Composable
private fun PreviewView(vm: AthleteViewModel, accent: Color, t: Strings) {
    val steps = vm.playerSteps
    val groups = remember(steps) { buildPreviewGroups(steps) }
    val totalExercises = groups.sumOf { it.exercises.size }
    val expanded = remember(steps) { mutableStateMapOf<Int, Boolean>() }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(vm.playerName, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(
                    "$totalExercises ${t.exercise} · ${groups.size} ${t.workout}",
                    color = AppTheme.colors.textDim,
                    fontSize = 14.sp,
                )
            }
            items(groups, key = { it.index }) { g ->
                val open = expanded[g.index] ?: false
                WorkoutGroupCard(g, open, accent, t) { expanded[g.index] = !open }
            }
        }
        PrimaryButton(
            label = t.start,
            accent = accent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            onClick = { vm.startPlayerRun() },
        )
    }
}

@Composable
private fun WorkoutGroupCard(
    g: PreviewGroup,
    open: Boolean,
    accent: Color,
    t: Strings,
    onToggle: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.surface)
            .clickable(onClick = onToggle)
            .padding(14.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        g.title.ifBlank { t.workout },
                        color = AppTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    if (g.rotating) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(accent.copy(alpha = 0.22f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(t.rotatingTag, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                val sub = buildString {
                    if (g.rotating && g.variant.isNotBlank()) {
                        append("${t.activeVariantLabel}: ${g.variant}")
                    } else {
                        append("${g.exercises.size} ${t.exercise}")
                    }
                    if (g.durationSec > 0) append(" · ${formatRemaining(g.durationSec * 1000L)}")
                }
                Text(sub, color = AppTheme.colors.textDim, fontSize = 12.sp)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppTheme.colors.textDim,
                modifier = Modifier.rotate(if (open) 90f else 0f),
            )
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                g.exercises.forEachIndexed { i, ex ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TimelineRail(isFirst = i == 0, isLast = i == g.exercises.lastIndex, accent = accent)
                        Spacer(Modifier.width(10.dp))
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppTheme.colors.track)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val exLabel = ExerciseCatalog.display(ex.exerciseId, ex.name, t.locale.language)
                            ExerciseGlyph(name = exLabel, color = 0xFF2E9E5BL, sizeDp = 30, exerciseId = ex.exerciseId)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                exLabel,
                                color = AppTheme.colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            if (ex.meta.isNotBlank()) {
                                Text(ex.meta, color = AppTheme.colors.textDim, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRail(isFirst: Boolean, isLast: Boolean, accent: Color) {
    Box(
        Modifier
            .width(18.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isFirst) Color.Transparent else AppTheme.colors.track),
            )
            Box(
                Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else AppTheme.colors.track),
            )
        }
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isFirst) accent else AppTheme.colors.textDim),
        )
    }
}

@Composable
private fun WorkoutProgressBar(step: PlayerStep, accent: Color, t: Strings) {
    val name = step.workoutName.ifBlank { step.workoutBaseName }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.20f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${t.workout} ${step.workoutIndex + 1} / ${step.totalWorkouts}",
                color = TEXT_DIM,
                fontSize = 12.sp,
            )
            if (name.isNotBlank()) {
                Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(step.totalWorkouts) { i ->
                val c = when {
                    i < step.workoutIndex -> accent
                    i == step.workoutIndex -> Color.White
                    else -> Color.White.copy(alpha = 0.25f)
                }
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(c),
                )
            }
        }
    }
}

@Composable
private fun RoutineProgressBar(vm: AthleteViewModel, accent: Color) {
    val steps = vm.playerSteps
    if (steps.isEmpty()) return
    val idx = vm.playerIndex.coerceIn(0, steps.lastIndex)
    val remainingMs = vm.playerRemainingMs

    val totalSec = steps.sumOf { it.estimatedSec }
    if (totalSec <= 0) return

    val completedSec = steps.take(idx).sumOf { it.estimatedSec }
    val cur = steps[idx]
    val curPartial = if (cur.timeBased && cur.durationSec > 0) {
        (cur.durationSec - remainingMs / 1000.0).coerceIn(0.0, cur.durationSec.toDouble())
    } else 0.0
    val elapsedSec = completedSec + curPartial
    val rawFraction = (elapsedSec / totalSec).toFloat().coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(targetValue = rawFraction, label = "routineProgress")
    val percent = (rawFraction * 100).toInt()

    val totalExercises = steps.count { it.kind == StepKind.WORK }
    val curExercise = steps.take(idx + 1).count { it.kind == StepKind.WORK }

    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (totalExercises > 0) "$curExercise / $totalExercises" else "",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "$percent%",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.15f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animatedFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
        }
    }
}

@Composable
private fun RunningView(vm: AthleteViewModel, accent: Color, t: Strings) {
    val step = vm.playerStep ?: return
    val color = Color(step.colorArgb)
    val stageLabel = when (step.kind) {
        StepKind.PREP -> t.prepare.uppercase()
        StepKind.WORK -> step.title.ifBlank { t.exercise }.uppercase()
        StepKind.REST -> t.rest.uppercase()
        StepKind.COOLDOWN -> t.cooldown.uppercase()
    }

    // Auto-oculta el OSD tras 4 s; se re-arma con cada interacción (osdNonce).
    LaunchedEffect(vm.playerControlsVisible, vm.osdNonce) {
        if (vm.playerControlsVisible) {
            delay(4000)
            vm.hidePlayerControls()
        }
    }

    val padClock = remember { vm.padPlayerClock() }
    // Color de fase completo, oscurecido 12% para legibilidad del texto blanco.
    val bg = lerp(color, Color.Black, 0.12f)
    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .pointerInput(Unit) { detectTapGestures { vm.togglePlayerControls() } },
    ) {
        RoutineProgressBar(vm, accent)
        Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        AnimatedVisibility(visible = vm.playerControlsVisible && step.totalWorkouts > 1) {
            WorkoutProgressBar(step, accent, t)
        }
        Spacer(Modifier.height(8.dp))
        val ownerLabel = ExerciseCatalog.display(step.ownerExerciseId, step.ownerName, t.locale.language)
        if (step.kind != StepKind.WORK && step.ownerName.isNotBlank()) {
            ExerciseGlyph(name = ownerLabel, color = step.colorArgb, sizeDp = 40, exerciseId = step.ownerExerciseId)
            Spacer(Modifier.height(8.dp))
        }
        val repByRep = step.kind == StepKind.WORK && !step.timeBased && step.reps == 1 && step.totalSets > 1
        val bigTitle = when (step.kind) {
            StepKind.WORK -> step.title.ifBlank { t.exercise }
            else -> ownerLabel.ifBlank { stageLabel }
        }.uppercase()
        val subStage = when (step.kind) {
            StepKind.PREP -> t.prepare.uppercase()
            StepKind.COOLDOWN -> t.cooldown.uppercase()
            else -> ""
        }
        val showSeries = (step.kind == StepKind.WORK || step.kind == StepKind.REST) &&
            step.totalSets > 1 && !repByRep
        Text(bigTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 52.sp, textAlign = TextAlign.Center)
        if (step.note.isNotBlank()) {
            Text(step.note.uppercase(), color = TEXT_DIM, fontWeight = FontWeight.Bold, fontSize = 40.sp, textAlign = TextAlign.Center)
        }
        if (subStage.isNotBlank()) {
            Text(subStage, color = TEXT_DIM, fontWeight = FontWeight.Bold, fontSize = 40.sp)
        }
        if (showSeries) {
            Text("${step.setIndex + 1} / ${step.totalSets}", color = TEXT_DIM, fontWeight = FontWeight.Bold, fontSize = 40.sp)
        }

        Spacer(Modifier.height(28.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (step.kind == StepKind.WORK && !step.timeBased) {
                RepsDisplay(step, repByRep, t)
            } else {
                ClockDisplay(step, vm.playerRemainingMs, padClock)
            }
        }

        AnimatedVisibility(visible = vm.playerControlsVisible && step.weighted) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WeightFeedback(vm, step, accent, t)
                Spacer(Modifier.height(12.dp))
            }
        }

        AnimatedVisibility(visible = vm.playerControlsVisible) {
            Controls(vm, step, accent, t)
        }
    }
        AnimatedGlowBorder(cornerRadius = 0.dp, colors = glowColors(color), strokeWidth = 3.dp)
    }
}

@Composable
private fun RepsDisplay(step: PlayerStep, repByRep: Boolean, t: Strings) {
    val transition = rememberInfiniteTransition(label = "bob")
    val offset by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "bobOffset",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.graphicsLayer { translationY = offset }) {
            ExerciseGlyph(
                name = ExerciseCatalog.display(step.ownerExerciseId, step.ownerName, t.locale.language),
                color = step.colorArgb, sizeDp = 96, exerciseId = step.ownerExerciseId,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (repByRep) {
            Text(t.repLabel, color = TEXT_DIM, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("${step.setIndex + 1} / ${step.totalSets}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 56.sp)
        } else {
            Text("× ${step.reps}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 56.sp)
        }
    }
}

@Composable
private fun ClockDisplay(step: PlayerStep, remainingMs: Long, padded: Boolean) {
    val shown = if (step.display == DisplayMode.COUNTUP) {
        (step.durationSec * 1000L - remainingMs).coerceAtLeast(0L)
    } else remainingMs
    Text(formatPlayerClock(shown, padded), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 84.sp)
}

@Composable
private fun WeightFeedback(vm: AthleteViewModel, step: PlayerStep, accent: Color, t: Strings) {
    val current = vm.weightFeedback[step.ownerName]?.second
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SURFACE)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${fmtKg(step.weightTotal)} ${t.kg}" + if (step.weightLabel.isNotBlank()) "  ·  ${step.weightLabel}" else "",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(t.howWeightFelt, color = TEXT_DIM, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedbackChip("${t.tooHeavy} ↓", current == -2.5, accent) {
                vm.showPlayerControls()
                vm.recordFeedback(step.ownerName, step.weightTotal, -2.5)
            }
            FeedbackChip(t.justRight, current == 0.0, accent) {
                vm.showPlayerControls()
                vm.recordFeedback(step.ownerName, step.weightTotal, 0.0)
            }
            FeedbackChip("${t.tooLight} ↑", current == 2.5, accent) {
                vm.showPlayerControls()
                vm.recordFeedback(step.ownerName, step.weightTotal, 2.5)
            }
        }
    }
}

@Composable
private fun FeedbackChip(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) accent else TRACK)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) ON_ACCENT else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Controls(vm: AthleteViewModel, step: PlayerStep, accent: Color, t: Strings) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!step.manual) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(SURFACE)
                    .clickable {
                        vm.showPlayerControls()
                        if (vm.playerRunning) vm.pausePlayer() else vm.resumePlayer()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (vm.playerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
        PrimaryButton(
            label = if (step.manual) t.doneLabel else t.nextLabel,
            accent = accent,
            modifier = Modifier.weight(1f),
            onClick = { vm.showPlayerControls(); vm.nextStep() },
        )
    }
}

@Composable
private fun FinishedView(vm: AthleteViewModel, accent: Color, t: Strings) {
    val suggestions = vm.weightSuggestions()
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 32.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text("🎉", fontSize = 56.sp)
                Text(t.workoutComplete, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Spacer(Modifier.height(8.dp))
            }
            if (suggestions.isNotEmpty()) {
                item {
                    Text(t.nextSuggestions, color = AppTheme.colors.textDim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                }
                items(suggestions) { (name, _, next) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppTheme.colors.surface)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, color = AppTheme.colors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text("${fmtKg(next)} ${t.kg}", color = accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
        PrimaryButton(
            label = t.close,
            accent = accent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            onClick = { vm.closePlayer() },
        )
    }
}
