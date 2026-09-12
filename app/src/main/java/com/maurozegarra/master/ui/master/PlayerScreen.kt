package com.maurozegarra.master.ui.master

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shadow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.MasterViewModel
import com.maurozegarra.master.data.ExerciseCatalog
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.ui.AnimatedGlowBorder
import com.maurozegarra.master.ui.ExerciseVideo
import com.maurozegarra.master.ui.glowColors
import com.maurozegarra.master.model.DisplayMode
import com.maurozegarra.master.model.PlayerStep
import com.maurozegarra.master.model.StepKind
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.ON_ACCENT
import com.maurozegarra.master.ui.theme.SURFACE
import com.maurozegarra.master.ui.theme.TEXT_DIM
import com.maurozegarra.master.ui.theme.TRACK
import com.maurozegarra.master.util.formatPlayerClock
import com.maurozegarra.master.util.formatRemaining
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(vm: MasterViewModel, accent: Color, t: Strings, onStart: () -> Unit = { vm.startPlayerRun() }) {
    when {
        vm.playerFinished -> FinishedView(vm, accent, t)
        !vm.playerStarted -> PreviewView(vm, accent, t, onStart)
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
private fun PreviewView(vm: MasterViewModel, accent: Color, t: Strings, onStart: () -> Unit) {
    val steps = vm.playerSteps
    val groups = remember(steps) { buildPreviewGroups(steps) }
    val totalExercises = groups.sumOf { it.exercises.size }
    val expanded = remember(steps) { mutableStateMapOf<Int, Boolean>() }

    // Revisar el training antes de hacerlo es el momento en que se sabe qué ejercicios
    // vienen y todavía queda tiempo para traer sus vídeos. Los que este training lleva
    // apagados no se piden: no se van a ver, así que bajarlos sería gastar datos en balde.
    LaunchedEffect(steps) {
        vm.prefetchVideos(
            steps.filter { it.showVideo && it.ownerExerciseId.isNotBlank() }
                .map { it.ownerExerciseId }
                .distinct(),
        )
    }

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
            onClick = onStart,
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
            .padding(14.dp)
            .animateContentSize(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .pointerInput(Unit) { detectTapGestures(onTap = { onToggle() }) },
            ) {
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
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onToggle)
                    .rotate(if (open) 90f else 0f),
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
private fun RoutineProgressBar(
    vm: MasterViewModel,
    accent: Color,
    trailing: @Composable () -> Unit = {},
) {
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
        // Alto fijo: el contenido de [trailing] aparece y desaparece según el ejercicio,
        // y sin esto la franja cambiaría de alto y empujaría todo lo de abajo al cambiar
        // de paso.
        Row(
            Modifier.fillMaxWidth().height(36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (totalExercises > 0) "$curExercise / $totalExercises" else "",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$percent%",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                trailing()
            }
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
private fun RunningView(vm: MasterViewModel, accent: Color, t: Strings) {
    val step = vm.playerStep ?: return
    val color = Color(step.colorArgb)
    val stageLabel = when (step.kind) {
        StepKind.PREP -> t.prepare.uppercase()
        StepKind.WORK -> step.title.ifBlank { t.exercise }.uppercase()
        StepKind.REST -> t.rest.uppercase()
        StepKind.COOLDOWN -> t.cooldown.uppercase()
    }


    // El vídeo del ejercicio que viene, con prioridad sobre lo que encolara el preview:
    // es el único que tiene una fecha límite.
    LaunchedEffect(vm.playerIndex) {
        vm.playerSteps.drop(vm.playerIndex + 1)
            .firstOrNull { it.kind == StepKind.WORK && it.showVideo }
            ?.let { vm.requestVideoNow(it.ownerExerciseId) }
    }

    val padClock = remember { vm.padPlayerClock() }
    // El sheet de instrucciones se compone fuera del chrome, así que sobrevive a que las
    // franjas se vayan. Guarda el contenido, no el id: se captura al abrirlo, y si el
    // training avanza de ejercicio mientras lees no se te cambia el texto por debajo.
    var sheetTarget by remember { mutableStateOf<InstructionsTarget?>(null) }
    // Color de fase completo, oscurecido 12% para legibilidad del texto blanco.
    // En pausa se oscurece adicionalmente como indicador visual.
    val isPaused = !step.manual && !vm.playerRunning
    val dimAlpha by animateFloatAsState(if (isPaused) 0.55f else 0f, label = "pauseDim")
    val bg = lerp(color, Color.Black, 0.12f)
    // Lectura de un mapa en memoria: esto se evalua en cada recomposicion, varias
    // veces por segundo mientras corre el reloj, y no puede tocar disco ni red.
    // step.showVideo es de este ejercicio en ESTE training: apagarlo en una copia no
    // toca al training del que salió.
    val videoFile = if (step.showVideo && step.ownerName.isNotBlank()) {
        vm.videoFileFor(step.ownerExerciseId)
    } else {
        null
    }
    // Auto-ocultado: a los OSD_HIDE_MS sin tocar nada se desvanecen las DOS FRANJAS DE
    // ARRIBA —la de rutina y la de workout—, y con ellas los botones de instrucciones y
    // editar que viven dentro. Nada más.
    //
    // Lo que NO se oculta nunca: los controles de reproducción, el "Next", el reloj y el
    // nombre. Son el mando y la información de la corrida; que aparezcan y desaparezcan
    // solo obliga a tocar dos veces para hacer una cosa.
    //
    // Sin excepciones por etapa ni por estado, y eso es justamente el arreglo: antes esto
    // llevaba un `keepChrome = isPaused || step.manual` para no dejar sin botones un paso
    // que solo avanza confirmando. Pero `PlayerStep.manual` es true en TODO WORK por
    // repeticiones, así que el chrome quedaba clavado en los ejercicios por reps y se
    // ocultaba en los de tiempo; peor aún, dentro de un mismo ejercicio por reps se
    // ocultaba en PREP y REST —que sí son por tiempo— y no en WORK. Con los controles
    // siempre visibles esa excepción ya no protege nada, así que desaparece.
    //
    // Las franjas nacen ocultas y solo las saca el tap, así que el único camino para
    // mostrarlas ya cambia `playerControlsVisible`: basta con esa clave. Antes hacía falta
    // además un contador (`osdNonce`) porque los botones podían pedir "muéstrate" estando
    // ya visibles, y entonces la clave no cambiaba y la cuenta no se reiniciaba.
    val chromeAlpha by animateFloatAsState(
        if (vm.playerControlsVisible) 1f else 0f,
        label = "osdFade",
    )
    LaunchedEffect(vm.playerControlsVisible) {
        if (vm.playerControlsVisible) {
            delay(OSD_HIDE_MS)
            vm.hidePlayerControls()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures { vm.togglePlayerControls() }
            }
            .pointerInput(Unit) {
                var accumulated = 0f
                detectHorizontalDragGestures(
                    onDragStart = { accumulated = 0f },
                    // Sin sacar las franjas: arrastrar avanza o retrocede, y el resultado
                    // ya se ve en el reloj y en el nombre. Solo el tap las pide.
                    onDragEnd = {
                        if (accumulated < -200f) {
                            vm.checkStep()
                        } else if (accumulated > 200f) {
                            vm.prevStep()
                        }
                    },
                ) { _, dragAmount ->
                    accumulated += dragAmount
                }
            },
    ) {
        // El vídeo, en su propia capa y al fondo: se queda con la pantalla entera en vez
        // de pelear por la altura que sobre en la columna. `aspectRatio` sigue mandando,
        // así que no se recorta; al no caber por alto pasa a ajustar por ancho y deja
        // color de etapa arriba y abajo. El chrome va después, o sea encima.
        if (videoFile != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ExerciseVideo(
                    file = videoFile,
                    // Solo se mueve mientras se ejecuta: en PREP, REST y COOLDOWN queda
                    // el primer fotograma quieto.
                    playing = step.kind == StepKind.WORK,
                    paused = isPaused,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                )
            }
            // Degradados hacia el COLOR DE ETAPA, no hacia negro: el chrome se lee sobre
            // cualquier fotograma y la fase se sigue distinguiendo, que es justo lo que
            // se perdería tirando de negro. Solo existen cuando hay vídeo detrás; sobre
            // el fondo liso no tendrían nada que resolver y solo lo ensuciarían.
            //
            // El tramo opaco cubre la franja de color que el vídeo deja libre (~75dp
            // arriba y abajo) y se desvanece ya sobre la imagen, para no apagarla.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(SCRIM_TOP)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to bg,
                            0.30f to bg.copy(alpha = 0.85f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
            // Más alto que el de arriba porque abajo se apilan reloj, controles y "Next".
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(SCRIM_BOTTOM)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.70f to bg.copy(alpha = 0.85f),
                            1f to bg,
                        ),
                    ),
            )
        }
        // Va superpuesta en el Box, así que desvanecerla no mueve nada de sitio. Se deja
        // de componer al llegar a 0 para que sus botones dejen de capturar el toque: si
        // siguieran ahí, tocar donde estaba el lápiz abriría el editor en vez de devolver
        // el chrome.
        if (chromeAlpha > 0f) {
            Box(Modifier.graphicsLayer { alpha = chromeAlpha }) {
                RoutineProgressBar(vm, accent) {
                    // Abrir el sheet se lleva las franjas por delante: no conviven. Se lee
                    // lo que se ha pedido leer, y al cerrarlo la pantalla queda limpia; las
                    // franjas vuelven con un tap, como cualquier otra vez.
                    InstructionsButton(vm, step.ownerExerciseId, t) {
                        sheetTarget = InstructionsTarget(
                            title = ownerNameFor(step, t),
                            steps = vm.mediaFor(step.ownerExerciseId)?.instructions.orEmpty(),
                        )
                        vm.hidePlayerControls()
                    }
                    EditExerciseButton(vm, step, t)
                }
            }
        }
        if (dimAlpha > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha)),
            )
        }
        Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // La barra de progreso de arriba va superpuesta y mide 55dp (6 + 36 + 4 + 3 + 6).
        // Con 12 aquí el nombre empezaba a 40dp, o sea dentro de ella. 44 + los 20 del
        // padding lo dejan ~17dp por debajo.
        Spacer(Modifier.height(44.dp))
        // Se desvanece en vez de colapsar: esto sí está en el flujo de la columna, y
        // plegarlo cada cuatro segundos daría un salto al reloj y a los controles. El
        // hueco se reserva mientras haya más de un workout, que es cuando la franja
        // tiene algo que contar.
        if (step.totalWorkouts > 1) {
            Box(Modifier.graphicsLayer { alpha = chromeAlpha }) {
                WorkoutProgressBar(step, accent, t)
            }
        }
        Spacer(Modifier.height(8.dp))
        val ownerLabel = ExerciseCatalog.display(step.ownerExerciseId, step.ownerName, t.locale.language)
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
        ExerciseTitle(bigTitle)
        if (step.note.isNotBlank()) {
            Text(step.note.uppercase(), color = TEXT_DIM, fontWeight = FontWeight.Bold, fontSize = 40.sp, textAlign = TextAlign.Center)
        }
        if (subStage.isNotBlank()) {
            Text(subStage, color = TEXT_DIM, fontWeight = FontWeight.Bold, fontSize = 40.sp)
        }
        if (showSeries) {
            Text("${step.setIndex + 1} / ${step.totalSets}", color = TEXT_DIM, fontWeight = FontWeight.Bold, fontSize = 40.sp)
        }

        // Las instrucciones se probaron aquí, llenando el hueco del vídeo, y el usuario las
        // descartó: viven detrás de su botón. Así que el hueco vuelve a ser solo del vídeo,
        // y sin vídeo el reloj se queda con él, centrado, como antes de aquella prueba.
        if (videoFile != null) {
            // El vídeo no está en la columna: vive en su propia capa, al fondo del Box. Aquí
            // solo queda el hueco elástico que empuja el reloj junto a los controles, donde
            // está al alcance de la vista sin disputarle el centro al vídeo.
            Spacer(Modifier.weight(1f))
            ClockOrReps(vm, step, repByRep, padClock, t)
        } else {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ClockOrReps(vm, step, repByRep, padClock, t)
            }
        }
        Spacer(Modifier.height(16.dp))

        if (step.weighted) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WeightFeedback(vm, step, accent, t)
                Spacer(Modifier.height(12.dp))
            }
        }

        // Siempre visibles, en toda etapa y en cualquier modo: son el mando de la corrida.
        // Además es lo que hace innecesaria cualquier excepción en el auto-ocultado, porque
        // el botón de confirmar de un paso manual nunca llega a irse.
        Controls(vm, step, accent, t)
        Spacer(Modifier.height(8.dp))
        NextExerciseLabel(vm, t)
        Spacer(Modifier.height(8.dp))
    }
        // Hermano del chrome, no hijo suyo: por eso sigue abierto cuando las franjas ya se
        // fueron. Solo lo cierra el usuario.
        sheetTarget?.let { InstructionsSheet(it) { sheetTarget = null } }
        AnimatedGlowBorder(cornerRadius = 0.dp, colors = glowColors(color), strokeWidth = 3.dp)
    }
}

/** Reloj o repeticiones, según el paso. Extraído porque el vídeo lo cambia de sitio. */
@Composable
private fun ClockOrReps(
    vm: MasterViewModel,
    step: PlayerStep,
    repByRep: Boolean,
    padClock: Boolean,
    t: Strings,
) {
    if (step.kind == StepKind.WORK && !step.timeBased) {
        RepsDisplay(step, repByRep, t)
    } else {
        ClockDisplay(step, vm.playerRemainingMs, padClock)
    }
}

private fun ownerNameFor(step: PlayerStep, t: Strings): String =
    ExerciseCatalog.display(step.ownerExerciseId, step.ownerName.ifBlank { step.title }, t.locale.language)

/**
 * Acceso a las instrucciones del ejercicio: icono arriba a la derecha que abre un sheet
 * con los pasos numerados.
 *
 * Es un icono y no un "swipe up" como el de Freeletics porque el player ya tiene el tap
 * (alterna el OSD) y el arrastre horizontal (check / anterior) ocupados. Un tercer gesto
 * vertical sería invisible sin un texto de ayuda, y la zona inferior ya carga con los
 * controles, el label "Next" y las barras de progreso.
 *
 * Va **dentro** de la franja de progreso, después del porcentaje, y no flotando sobre
 * ella: flotando aterrizaba justo encima del porcentaje y de la barra. Al estar maquetado
 * no puede volver a superponerse, y la posición no depende de si el ejercicio tiene vídeo.
 */
/**
 * El nombre del ejercicio en el player. Tres reglas, y cada una evita algo que se veía:
 *
 * - **Nunca más de dos líneas.** Un nombre largo se hace más pequeño en vez de crecer.
 * - **Nunca partido a mitad de palabra.** "HALF-KNEELIN / G THORACIC" no se lee. El tamaño
 *   baja hasta que la palabra más larga cabe entera en una línea, y solo entonces se mira
 *   que el conjunto quepa en dos.
 * - **Alto fijo: el de dos líneas al tamaño máximo**, aunque el nombre quepa en una. Si la
 *   caja creciera y menguara con el nombre, el vídeo de debajo daría un salto cada vez que
 *   cambia el ejercicio. El texto va arriba y el hueco sobrante queda debajo.
 */
@Composable
private fun ExerciseTitle(text: String) {
    val measurer = rememberTextMeasurer()
    // El alto de dos líneas al tamaño máximo, MEDIDO y no calculado. 2 × interlineado se
    // quedaba corto por el relleno que la fuente añade arriba y abajo, y con la caja justa
    // Compose recortaba a una línea con puntos suspensivos: "COBRA TO C...".
    val boxPx = remember(measurer) { measurer.measure("A\nA", titleStyle(TITLE_MAX_SIZE)).size.height }
    val boxHeight = with(LocalDensity.current) { boxPx.toDp() }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(boxHeight),
        contentAlignment = Alignment.TopCenter,
    ) {
        val maxWidth = constraints.maxWidth
        // Se mide una vez por nombre y ancho, no en cada recomposición: el reloj repinta
        // esta pantalla varias veces por segundo.
        val size = remember(text, maxWidth) { fitTitleSize(measurer, text, maxWidth, boxPx) }
        Text(
            text,
            style = titleStyle(size),
            maxLines = TITLE_LINES,
            // Solo se llega aquí con un nombre absurdo que no cabe ni al tamaño mínimo:
            // mejor puntos suspensivos que una tercera línea.
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun titleStyle(size: TextUnit) = TextStyle(
    color = Color.White,
    fontWeight = FontWeight.Bold,
    fontSize = size,
    lineHeight = size * TITLE_LINE_RATIO,
    textAlign = TextAlign.Center,
    // Reparte en líneas parejas —"HALF-KNEELING / THORACIC ROTATION"— en vez de llenar la
    // primera y dejar una palabra suelta en la segunda.
    lineBreak = LineBreak.Heading,
)

/**
 * El tamaño más grande con el que el nombre cabe en dos líneas sin partir ninguna palabra.
 *
 * Mirar solo el número de líneas no basta: una palabra más ancha que la línea se parte por
 * donde caiga y aun así pueden salir dos líneas. Por eso primero se exige que la palabra
 * más ancha quepa entera, y después que el conjunto no pase de dos.
 */
private fun fitTitleSize(measurer: TextMeasurer, text: String, maxWidth: Int, maxHeight: Int): TextUnit {
    val words = text.split(' ').filter { it.isNotBlank() }
    var size = TITLE_MAX_SIZE.value
    while (size > TITLE_MIN_SIZE.value) {
        val style = titleStyle(size.sp)
        val widest = words.maxOfOrNull {
            measurer.measure(it, style, softWrap = false, maxLines = 1).size.width
        } ?: 0
        if (widest <= maxWidth) {
            val layout = measurer.measure(text, style, constraints = Constraints(maxWidth = maxWidth))
            // El alto también, no solo las líneas: es lo que decide si Compose lo recorta.
            if (layout.lineCount <= TITLE_LINES && layout.size.height <= maxHeight) return size.sp
        }
        size -= TITLE_SIZE_STEP
    }
    return TITLE_MIN_SIZE
}

/**
 * Cuánto aguanta el chrome visible sin que toques nada, antes de desvanecerse.
 *
 * Cuatro segundos: lo justo para leer en qué ejercicio vas y llegar a un botón, sin que
 * el vídeo pase la mayor parte del tiempo tapado.
 */
private const val OSD_HIDE_MS = 4_000L

/**
 * Alto de los degradados que hacen legible el chrome sobre el vídeo.
 *
 * Salen de lo que ocupa el chrome, no de un número redondo: arriba, insets + padding +
 * el hueco de la barra superpuesta + el título de dos líneas llegan a ~216dp; abajo, el
 * reloj de 84sp, los controles, el "Next" y sus separaciones suman ~269dp. Se les da un
 * poco de margen para que el borde del degradado no coincida con el del texto.
 */
private val SCRIM_TOP = 240.dp
private val SCRIM_BOTTOM = 300.dp

private val TITLE_MAX_SIZE = 48.sp
private val TITLE_MIN_SIZE = 20.sp
private const val TITLE_SIZE_STEP = 2f
private const val TITLE_LINES = 2
/** 52/48: el interlineado que ya tenía el título. */
private const val TITLE_LINE_RATIO = 52f / 48f

/**
 * Editar el ejercicio en curso sin parar el reloj.
 *
 * Va aquí arriba, discreto, y no entre los botones grandes: esos son para correr, y uno de
 * editar del mismo tamaño al lado de saltar y confirmar se pulsaría por error con las manos
 * sudadas. No aparece en un training asignado, que no es editable.
 */
@Composable
private fun EditExerciseButton(vm: MasterViewModel, step: PlayerStep, t: Strings) {
    val id = vm.playerTrainingId ?: return
    if (vm.trainings.firstOrNull { it.id == id }?.assigned != false) return

    Box(
        modifier = Modifier
            .padding(start = 12.dp)
            .size(36.dp)
            .clip(CircleShape)
            .clickable { vm.editRunningExercise(step) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Edit,
            contentDescription = t.edit,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * El icono que pide las instrucciones. **Solo dispara**: el sheet lo compone
 * [RunningView], fuera del chrome.
 *
 * Tenerlo aquí dentro era el bug: el sheet quedaba colgando de la franja de arriba, y al
 * desvanecerse esta no se atenuaba el sheet, se iba de la composición con el usuario
 * leyendo. Un sheet no puede depender de que siga visible el botón que lo abrió.
 */
@Composable
private fun InstructionsButton(vm: MasterViewModel, exerciseId: String, t: Strings, onOpen: () -> Unit) {
    if (vm.mediaFor(exerciseId)?.instructions.orEmpty().isEmpty()) return

    Box(
        modifier = Modifier
            .padding(start = 12.dp)
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.List,
            contentDescription = t.instructions,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
    }

}

/** Lo que se enseña en el sheet, capturado al abrirlo. Ver [InstructionsSheet]. */
private data class InstructionsTarget(val title: String, val steps: List<String>)

/**
 * Los pasos del ejercicio, en un sheet que vive **fuera del chrome**: se abre con el
 * icono de la franja, las franjas se van, y él se queda hasta que el usuario lo cierra.
 *
 * Recibe el contenido ya resuelto y no el `exerciseId`, a propósito: quien lo abre captura
 * título y pasos en ese momento, así que si el training avanza de ejercicio mientras lees
 * no se te cambia el texto por debajo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstructionsSheet(target: InstructionsTarget, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.surface,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            Text(target.title, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Spacer(Modifier.height(16.dp))
            target.steps.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(AppTheme.colors.accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", color = AppTheme.colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        s,
                        color = AppTheme.colors.textPrimary,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NextExerciseLabel(vm: MasterViewModel, t: Strings) {
    val steps = vm.playerSteps
    if (steps.isEmpty()) return
    val idx = vm.playerIndex
    val nextWork = steps.drop(idx + 1).firstOrNull { it.kind == StepKind.WORK }
    if (nextWork == null) return
    val nextName = ExerciseCatalog.display(nextWork.ownerExerciseId, nextWork.title.ifBlank { nextWork.ownerName }, t.locale.language)
    val text = "${t.nextLabel}: $nextName".uppercase()

    Text(
        text,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start,
        style = LocalTextStyle.current.copy(
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.8f),
                offset = Offset(2f, 2f),
                blurRadius = 4f,
            ),
        ),
    )
}

@Composable
private fun RepsDisplay(step: PlayerStep, repByRep: Boolean, t: Strings) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
private fun WeightFeedback(vm: MasterViewModel, step: PlayerStep, accent: Color, t: Strings) {
    val current = vm.weightFeedback[vm.feedbackKey(step.ownerExerciseId, step.workoutIndex)]?.third
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
                vm.recordFeedback(step.ownerExerciseId, step.workoutIndex, step.ownerName, step.weightTotal, -2.5)
            }
            FeedbackChip(t.justRight, current == 0.0, accent) {
                vm.recordFeedback(step.ownerExerciseId, step.workoutIndex, step.ownerName, step.weightTotal, 0.0)
            }
            FeedbackChip("${t.tooLight} ↑", current == 2.5, accent) {
                vm.recordFeedback(step.ownerExerciseId, step.workoutIndex, step.ownerName, step.weightTotal, 2.5)
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
private fun Controls(vm: MasterViewModel, step: PlayerStep, accent: Color, t: Strings) {
    var showSkipDialog by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassButton(
            icon = Icons.Outlined.SkipNext,
            contentDescription = t.skip,
            size = 48.dp,
            iconSize = 24.dp,
            onClick = { vm.skipStep() },
            onLongClick = { showSkipDialog = true },
        )
        if (!step.manual) {
            GlassButton(
                icon = if (vm.playerRunning) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (vm.playerRunning) t.pause else t.resume,
                size = 72.dp,
                iconSize = 36.dp,
                onClick = {
                    if (vm.playerRunning) vm.pausePlayer() else vm.resumePlayer()
                },
            )
            GlassButton(
                icon = Icons.Outlined.Check,
                contentDescription = t.check,
                size = 48.dp,
                iconSize = 24.dp,
                onClick = { vm.checkStep() },
            )
        } else {
            GlassButton(
                icon = Icons.Outlined.Check,
                contentDescription = t.check,
                size = 72.dp,
                iconSize = 36.dp,
                onClick = { vm.checkStep() },
            )
            Spacer(Modifier.size(48.dp))
        }
    }

    if (showSkipDialog) {
        val exerciseName = ExerciseCatalog.display(step.ownerExerciseId, step.ownerName, t.locale.language)
        AlertDialog(
            onDismissRequest = { showSkipDialog = false },
            title = { Text(t.skipExercise) },
            text = { Text(t.skipExerciseConfirm(exerciseName)) },
            confirmButton = {
                TextButton(onClick = {
                    showSkipDialog = false
                    vm.skipExercise()
                }) { Text(t.skip) }
            },
            dismissButton = {
                TextButton(onClick = { showSkipDialog = false }) { Text(t.cancel) }
            },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GlassButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 56.dp,
    iconSize: androidx.compose.ui.unit.Dp = 28.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun FinishedView(vm: MasterViewModel, accent: Color, t: Strings) {
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
