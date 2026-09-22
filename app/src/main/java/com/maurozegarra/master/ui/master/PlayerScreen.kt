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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.MasterViewModel
import com.maurozegarra.master.data.ExerciseCatalog
import com.maurozegarra.master.data.VideoState
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.ui.AnimatedGlowBorder
import com.maurozegarra.master.ui.ExerciseThumb
import com.maurozegarra.master.ui.ExerciseVideo
import com.maurozegarra.master.ui.glowColors
import com.maurozegarra.master.model.DisplayMode
import com.maurozegarra.master.model.PlayerStep
import com.maurozegarra.master.model.SPEED_STEP
import com.maurozegarra.master.model.Plates
import com.maurozegarra.master.model.WeightType
import com.maurozegarra.master.model.SideMark
import com.maurozegarra.master.model.StepKind
import com.maurozegarra.master.ui.theme.Dims
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.ON_ACCENT
import com.maurozegarra.master.ui.theme.SURFACE
import com.maurozegarra.master.ui.theme.STATUS_SKIPPED
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

private data class PreviewExercise(
    val name: String,
    val exerciseId: String,
    val meta: String,
    /** Del paso, o sea de ESTA instancia: un ejercicio con el vídeo apagado no enseña miniatura. */
    val showVideo: Boolean,
)

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
            .map { s -> PreviewExercise(s.ownerName, s.ownerExerciseId, metaFor(s), s.showVideo) }
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

    // El bloque de abajo -la pregunta del dolor y el boton de empezar- flota encima de la
    // lista, asi que la lista tiene que terminar donde ese bloque empieza. Su alto se MIDE y
    // no se escribe: con un 96.dp fijo, pensado para cuando ahi solo habia un boton, la
    // pregunta del dolor (TD-089) tapaba la ultima tarjeta del training.
    var bottomPx by remember { mutableStateOf(0) }
    val bottomHeight = with(LocalDensity.current) { bottomPx.toDp() }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, bottomHeight + 16.dp),
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
                WorkoutGroupCard(g, open, accent, t, vm::videoFileFor) { expanded[g.index] = !open }
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomPx = it.height }
                // Opaco a proposito: aunque la lista ya termine donde este bloque empieza,
                // al hacer scroll las tarjetas pasan por detras, y sin fondo se leerian
                // encima de la pregunta.
                .background(AppTheme.colors.bg)
                .padding(16.dp),
        ) {
            // Se pregunta AQUI, en la pantalla previa, y no al terminar: "mientras mas
            // inmediata la pregunta, mas pegada a la realidad sera la respuesta". Al final,
            // el dolor de antes ya es un recuerdo de hace una hora.
            //
            // Contestar es opcional: el boton de empezar no espera a nadie. Una pregunta que
            // bloquea el entrenamiento se contesta de cualquier forma con tal de pasar, y
            // ese dato vale menos que ninguno.
            if (vm.asksHowItWent()) {
                PainScale(t.painNow, vm.pendingPainBefore, accent, t) { vm.setPainBefore(it) }
                Spacer(Modifier.height(12.dp))
            }
            PrimaryButton(
                label = t.start,
                accent = accent,
                modifier = Modifier.fillMaxWidth(),
                onClick = onStart,
            )
        }
    }
}

@Composable
private fun WorkoutGroupCard(
    g: PreviewGroup,
    open: Boolean,
    accent: Color,
    t: Strings,
    /** El vídeo ya descargado de ese ejercicio, o null. Lambda y no el ViewModel: la tarjeta solo pinta. */
    videoFor: (String) -> java.io.File?,
    onToggle: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.row))
            .background(AppTheme.colors.surface)
            .padding(Dims.rowPadding)
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
                        StatusBadge(text = t.rotatingTag, color = accent)
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
                            // Miniatura del propio vídeo si lo hay; si no, el emoji. En una
                            // lista el emoji sí trabaja —distingue filas de un vistazo—, pero
                            // el fotograma real lo hace mejor y sin colisiones entre ejercicios.
                            val thumb = if (ex.showVideo) videoFor(ex.exerciseId) else null
                            if (thumb != null) {
                                ExerciseThumb(file = thumb, sizeDp = 30)
                            } else {
                                ExerciseGlyph(name = exLabel, color = 0xFF2E9E5BL, sizeDp = 30, exerciseId = ex.exerciseId)
                            }
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
                    ToggleVideoButton(vm, step, t)
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
        val ownerLabel = ExerciseCatalog.display(step.ownerExerciseId, step.ownerName, t.locale.language)
        val repByRep = step.kind == StepKind.WORK && !step.timeBased && step.reps == 1 && step.totalSets > 1
        // La palabra del lado va aqui, entera: la flecha junto al numero dice cual, y el
        // titulo -que tiene todo el ancho y se encoge para caber- lo dice con letras.
        val bigTitle = (when (step.kind) {
            StepKind.WORK -> step.title.ifBlank { t.exercise }
            else -> ownerLabel.ifBlank { stageLabel }
        } + (step.side.takeIf { it.isNotBlank() && step.kind != StepKind.PREP }?.let { " · $it" } ?: "")).uppercase()
        // PREPARE y COOLDOWN se fueron los dos: el color de la etapa ya es el indicador,
        // y cada palabra ocupaba 40sp justo encima del video.
        val showSeries = (step.kind == StepKind.WORK || step.kind == StepKind.REST) &&
            step.totalSets > 1 && !repByRep

        // La barra de rutina va superpuesta y mide 55dp (6 + 36 + 4 + 3 + 6): estos 44 más
        // los 20 del padding dejan lo de abajo justo por fuera de ella.
        Spacer(Modifier.height(44.dp))

        // UN SOLO HUECO para dos cosas que nunca se ven a la vez: el nombre del ejercicio
        // mientras el chrome está oculto, y la tarjeta de workout cuando sale.
        //
        // Antes el nombre vivía más abajo, en una caja de 104dp que en un vídeo vertical
        // caía justo sobre la cabeza; y encima de él quedaba el hueco de la tarjeta, que
        // desde TD-079 nace invisible, así que se veía un vacío que no explicaba nada.
        //
        // La regla de TD-079 sigue intacta —las franjas solo salen con el tap—; lo único
        // nuevo es que al salir ocupan el sitio del nombre, que es literalmente el mismo.
        // Se cruzan con el mismo alpha en vez de aparecer y desaparecer: así el cambio se
        // lee como un relevo y no como un parpadeo.
        // El alto lo pone la tarjeta, no un número escrito a mano: se compone SIEMPRE y solo
        // cambia su opacidad. Fijarlo a ojo fue el error de la primera pasada —me quedé en
        // 52dp cuando con el relleno de fuente real pide ~63— y la segunda línea salía
        // cortada. Midiéndolo así no hay nada que recalcular si cambia un tamaño de texto.
        // Alineados ARRIBA los dos. En un ejercicio sin vídeo el nombre llega a 48sp y pide
        // ~104dp, mientras la tarjeta sigue midiendo sus ~63: el sobrante cae debajo y se
        // lee como separación, no como una tarjeta descolocada en mitad de una banda.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            if (step.totalWorkouts > 1) {
                Box(Modifier.graphicsLayer { alpha = chromeAlpha }) {
                    WorkoutProgressBar(step, accent, t)
                }
            }
            // Con vídeo el nombre se queda pequeño: la imagen ya cuenta el ejercicio mejor
            // que el texto, y un titular grande solo taparía lo que hay que ver. Sin vídeo
            // el nombre ES el contenido, así que recupera los 48sp de antes.
            Box(Modifier.graphicsLayer { alpha = 1f - chromeAlpha }) {
                ExerciseTitle(bigTitle, if (videoFile != null) TITLE_WITH_VIDEO else TITLE_ALONE)
            }
        }
        Spacer(Modifier.height(8.dp))
        // La nota se encoge para caber en dos lineas, igual que el nombre del ejercicio, y
        // ocupa SIEMPRE el mismo alto.
        //
        // Arreglar su interlineado (TD-092) tuvo un efecto que no se vio venir: al dejar de
        // dibujarse encimada, "BAR ON THE HIPS, PUSH THROUGH THE HEELS" paso de ocupar una
        // linea a cuatro, y esos ~170dp salieron del hueco elastico, que es de donde cuelga
        // la tarjeta del peso. La tarjeta se quedo sin sitio y sus botones -"how did the
        // weight feel"- dejaron de dibujarse: el usuario no pudo contestar.
        //
        // Con alto fijo, el hueco elastico deja de depender de lo larga que sea la nota.
        if (step.note.isNotBlank()) {
            FittedText(step.note.uppercase(), NOTE_SIZE, TEXT_DIM)
        }


        // Las instrucciones se probaron aquí, llenando el hueco del vídeo, y el usuario las
        // descartó: viven detrás de su botón. Así que el hueco vuelve a ser solo del vídeo,
        // y sin vídeo el reloj se queda con él, centrado, como antes de aquella prueba.
        // EL RELOJ SIEMPRE EN EL MISMO SITIO, haya vídeo o no. Antes con vídeo iba abajo y
        // sin vídeo se centraba en el hueco, así que saltaba al centro al pasar de un
        // ejercicio con vídeo a uno sin: exactamente el salto que el lineamiento prohíbe.
        // Venía de cuando se revirtió el panel de instrucciones y se le devolvió el centro.
        //
        // El hueco elástico es un Box y no un Spacer para poder colgar de él la tarjeta del
        // peso: dentro de la zona elástica su alto no se lo quita a nadie, así que aparece
        // pegada justo encima del reloj sin desplazar nada. Arriba del todo quedaba lejos de
        // donde se mira, y abajo movía el reloj ~126dp al cambiar de ejercicio.
        //
        // Durante la SERIE, la tarjeta del peso con su feedback. Fuera de la serie
        // -preparacion, descanso, enfriamiento- lo UNICO que se ensena es lo que hay que
        // cargar para la proxima (TD-130). Se probo poner tambien el feedback en el descanso
        // (TD-122) y confundia: arriba "45 kg" de la serie que paso, abajo "55 kg" de la que
        // viene, y no se sabia cual cargar. El feedback se puede volver a marcar yendo atras,
        // asi que en el descanso sobra.
        val proximaCarga = if (step.kind == StepKind.WORK) {
            null
        } else {
            vm.playerSteps.drop(vm.playerIndex + 1).firstOrNull { it.kind == StepKind.WORK }?.takeIf { it.weighted }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.align(Alignment.BottomCenter)) {
                when {
                    step.kind == StepKind.WORK && step.weighted -> WeightFeedback(vm, step, accent, t)
                    step.kind == StepKind.WORK && step.speedKmh != null -> SpeedCard(vm, step, t)
                    proximaCarga != null -> LoadCard(proximaCarga, t)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // El contador de series va COLGADO del numero grande, a su izquierda, y ya no en una
        // linea propia encima. Ocupaba el alto de una nota entera para decir "1 / 3", y la
        // nota es lo que hay que leer mientras se entrena. Colgar no descentra el numero:
        // igual que la unidad a la derecha, se mide y se desplaza.
        // El lado va colgado del numero, junto al contador de series (TD-147): "L · 3/12".
        // Tiene que verse SIEMPRE que exista, incluso con una sola serie -el isometrico de
        // cuello son cuatro direcciones de una serie cada una-, que es justo cuando el
        // contador no sale y antes no quedaba nada que dijera cual toca.
        // El lado es una flecha y no la palabra (TD-153): "Derecha" junto a un reloj ancho
        // se salia por el borde y NIKO leia "HA".
        val lead = listOfNotNull(
            SideMark.of(step.side, step.sideIndex, step.sideCount),
            "${step.setIndex + 1}/${step.totalSets}".takeIf { showSeries },
        ).joinToString(" · ").ifBlank { null }
        ClockOrReps(vm, step, repByRep, padClock, t, lead = lead)
        Spacer(Modifier.height(16.dp))

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
    lead: String? = null,
) {
    if (step.kind == StepKind.WORK && !step.timeBased) {
        RepsDisplay(step, repByRep, t, lead)
    } else {
        ClockDisplay(step, vm.playerRemainingMs, padClock, lead)
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
private fun ExerciseTitle(text: String, maxSize: TextUnit) = FittedText(text, maxSize, Color.White)

/**
 * Texto que se encoge hasta caber en [TITLE_LINES] lineas, dentro de una caja de alto FIJO:
 * el que ocupan esas lineas al tamano maximo.
 *
 * El alto fijo es lo que hace que nada de abajo se mueva -ni el reloj, ni los controles, ni
 * la tarjeta del peso- por larga que sea la frase de un ejercicio u otro.
 */
@Composable
private fun FittedText(text: String, maxSize: TextUnit, color: Color) {
    val measurer = rememberTextMeasurer()
    // El alto de dos líneas al tamaño máximo, MEDIDO y no calculado. 2 × interlineado se
    // quedaba corto por el relleno que la fuente añade arriba y abajo, y con la caja justa
    // Compose recortaba a una línea con puntos suspensivos: "COBRA TO C...".
    val boxPx = remember(measurer, maxSize) { measurer.measure("A\nA", titleStyle(maxSize)).size.height }
    val boxHeight = with(LocalDensity.current) { boxPx.toDp() }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(boxHeight),
        contentAlignment = Alignment.TopCenter,
    ) {
        val maxWidth = constraints.maxWidth
        // Se mide una vez por nombre y ancho, no en cada recomposición: el reloj repinta
        // esta pantalla varias veces por segundo.
        val size = remember(text, maxWidth, maxSize) { fitTitleSize(measurer, text, maxWidth, boxPx, maxSize) }
        Text(
            text,
            style = titleStyle(size, color),
            maxLines = TITLE_LINES,
            // Solo se llega aquí con un nombre absurdo que no cabe ni al tamaño mínimo:
            // mejor puntos suspensivos que una tercera línea.
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun titleStyle(size: TextUnit, color: Color = Color.White) = TextStyle(
    color = color,
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
private fun fitTitleSize(
    measurer: TextMeasurer,
    text: String,
    maxWidth: Int,
    maxHeight: Int,
    maxSize: TextUnit,
): TextUnit {
    val words = text.split(' ').filter { it.isNotBlank() }
    var size = maxSize.value
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

/** Tamaño del dato principal —reloj o repeticiones—, el mismo para los dos. */
private val READOUT_SIZE = 84.sp

/**
 * La unidad que acompaña al número: «REPS».
 *
 * Bastante más chica que el número —que sigue siendo el dato— pero en blanco y lo bastante
 * grande para leerse de un vistazo. Apagada no servía: era lo que fallaba con la «×».
 */
private val READOUT_MARK_SIZE = 26.sp

/** Aire entre la marca y el número. */
private val READOUT_GAP = 10.dp

/**
 * Techo del nombre **cuando hay vídeo**: 28sp.
 *
 * Con vídeo el texto no debe competir: la imagen cuenta el ejercicio mejor que el nombre, y
 * un titular grande solo taparía lo que hay que mirar. 28 es además lo máximo que cabe sin
 * que el nombre pase a mandar sobre el alto de la franja —dos líneas piden 60.7dp y la
 * tarjeta de workout mide ~63—, así que con vídeo la franja la sigue midiendo la tarjeta.
 */
private val TITLE_WITH_VIDEO = 28.sp

/**
 * Techo del nombre **cuando no hay vídeo**: los 48sp de siempre.
 *
 * Sin vídeo el nombre es el contenido de la pantalla, no un rótulo encima de otra cosa, así
 * que no hay razón para encogerlo. Dos líneas piden ~104dp, más que la tarjeta, de modo que
 * en estos ejercicios es el nombre quien marca el alto de la franja.
 */
private val TITLE_ALONE = 48.sp
/**
 * Suelo del ajuste automático, común a los dos techos.
 *
 * Baja de 20 a 12sp porque con vídeo el techo es 28: un suelo de 20 dejaba un recorrido de
 * cuatro escalones y un nombre largo se quedaba sin sitio adonde encogerse.
 */
private val TITLE_MIN_SIZE = 12.sp
private const val TITLE_SIZE_STEP = 2f
private const val TITLE_LINES = 2
/** 52/48: el interlineado que ya tenía el título. */
private const val TITLE_LINE_RATIO = 52f / 48f

/** Tamano de la nota del ejercicio y del contador de series, que comparten sitio y peso. */
/**
 * Techo de la nota y del contador de series, no su tamano: una nota corta -"each side"- sale
 * a 28sp y una frase entera se encoge hasta caber en dos lineas.
 *
 * Se queda en 40 a peticion del usuario: las notas son las pistas para ejecutar bien el
 * ejercicio -"me dan las pistas claves"-, asi que mandan sobre lo demas. El sitio salio de
 * dos cosas que ocupaban linea propia sin merecerla: el contador de series, que ahora cuelga
 * del numero grande, y la tarjeta del peso, que paso de cuatro filas a dos.
 */
private val NOTE_SIZE = 40.sp

/**
 * Editar el ejercicio en curso sin parar el reloj.
 *
 * Va aquí arriba, discreto, y no entre los botones grandes: esos son para correr, y uno de
 * editar del mismo tamaño al lado de saltar y confirmar se pulsaría por error con las manos
 * sudadas. No aparece en un training asignado, que no es editable.
 */
/**
 * Apagar o encender el vídeo del ejercicio en curso, sin salir del player (TD-146).
 *
 * Antes esto costaba cuatro toques —lápiz, bajar hasta VIDEO, interruptor, Save— y encima
 * devolvía al usuario a la lista de trainings: *"yo solo buscaba dejar de ver el vídeo"*.
 *
 * **Se dibuja siempre, aunque el ejercicio no tenga vídeo**, apagado en gris. Que un icono
 * aparezca y desaparezca entre ejercicios movería de sitio a los otros dos, y en este app
 * ningún control cambia de posición al pasar de etapa o de ejercicio.
 */
@Composable
private fun ToggleVideoButton(vm: MasterViewModel, step: PlayerStep, t: Strings) {
    // Tambien en un training asignado (TD-154): la preferencia es del telefono y no del
    // training, asi que la sincronizacion no la deshace.
    vm.playerTrainingId ?: return
    val hayVideo = vm.videoStateFor(step.ownerExerciseId) !is VideoState.None

    Box(
        modifier = Modifier
            .padding(start = 12.dp)
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = hayVideo) { vm.toggleRunningVideo(step) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (step.showVideo) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
            contentDescription = t.showVideoHere,
            tint = Color.White.copy(alpha = if (hayVideo) 0.7f else 0.25f),
            modifier = Modifier.size(20.dp),
        )
    }
}

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
        // Con scroll: el contenido no tiene tope, y sin el, lo que no entraba en la pantalla
        // se cortaba sin aviso. No se noto mientras las instrucciones eran cortas; las de la
        // abduccion en español fueron las primeras que no entraron, y el ultimo paso -justo el
        // que dice como corregirlo si arde donde no toca- quedo partido por la mitad.
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
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
    // Cuando no hay siguiente se pinta VACIO, no se deja de pintar. Antes hacia `return` y
    // en el ultimo ejercicio los controles, que estan justo encima, se descolgaban ~20dp:
    // un control cambiando de sitio solo porque se acaba el training. Un Text vacio ocupa
    // su linea igual, asi que el hueco es identico por construccion y no hay numero que
    // mantener a mano.
    val steps = vm.playerSteps
    val idx = vm.playerIndex
    val nextWork = steps.drop(idx + 1).firstOrNull { it.kind == StepKind.WORK }
    // El peso de la siguiente serie va AQUI.
    //
    // Entre series es cuando se cambian los discos, y hasta ahora el numero no aparecia
    // hasta que el descanso terminaba y empezaba el trabajo: "tengo que esperar que pasen
    // los tiempos para ver: ah, eran x kilos que tenia que ponerle". Se lee justo cuando
    // sirve para algo, que es mientras todavia se puede ajustar la barra.
    //
    // Destaca por BRILLO y no por color: el peso va en blanco puro y en negrita, y el resto
    // de la linea baja a un blanco apagado. El acento no vale aqui -el fondo del player ES
    // el acento, asi que el numero se hundia en el: "el color no quedo bien"-, y como cada
    // training trae el suyo, cualquier color elegido chocaria con alguno. El blanco se lee
    // sobre todos.
    val text = buildAnnotatedString {
        if (nextWork == null) return@buildAnnotatedString
        val nextName = ExerciseCatalog.display(
            nextWork.ownerExerciseId,
            nextWork.title.ifBlank { nextWork.ownerName },
            t.locale.language,
        )
        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.72f))) {
            append("${t.nextLabel}: $nextName".uppercase())
        }
        if (nextWork.weightTotal > 0.0) {
            withStyle(SpanStyle(color = Color.White.copy(alpha = 0.72f))) { append("  ·  ") }
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                append("${fmtKg(nextWork.weightTotal)} ${t.kg}".uppercase())
            }
        }
    }

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
private fun RepsDisplay(step: PlayerStep, repByRep: Boolean, t: Strings, lead: String? = null) {
    // La unidad, no el simbolo. "15" a secas se confunde con un reloj en 15 —y mas con los
    // ceros a la izquierda apagados—, y la "x" pequena y gris no peleaba contra eso. Una
    // palabra no hay que interpretarla. El reloj no la necesita: se delata solo, porque baja.
    BigReadout(
        value = if (repByRep) "${step.setIndex + 1} / ${step.totalSets}" else "${step.reps}",
        mark = if (repByRep) t.repLabel else t.repsUnit.uppercase(),
        lead = lead,
    )
}

/**
 * El dato principal de la corrida: el reloj o las repeticiones.
 *
 * Los dos son lo mismo —cuánto te queda de este paso— y por eso comparten tamaño y sitio.
 * Que un ejercicio sea por tiempo o por repeticiones no puede encoger ese número ni moverlo
 * de donde estaba: antes el reloj iba a 84sp y las reps a 56.
 *
 * [mark] es la marca que acompaña al valor —la «×» de «× 15», el «REP» del modo repetición
 * a repetición— y va **al costado, sin contar para el centrado**. Centrar la cadena entera
 * dejaría el número desplazado a la derecha respecto al reloj: lo que tiene que caer en el
 * centro es el número, no el conjunto. Por eso se mide el valor y se cuelga la marca de su
 * borde izquierdo, en vez de ponerlos en fila.
 */
@Composable
private fun BigReadout(value: String, mark: String? = null, lead: String? = null) {
    val measurer = rememberTextMeasurer()
    val valueStyle = TextStyle(
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = READOUT_SIZE,
    )
    // En blanco, no apagada: si la unidad no se nota, volvemos a tener un numero suelto que
    // se confunde con el reloj, que es justo lo que se quiere resolver.
    val markStyle = TextStyle(
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = READOUT_MARK_SIZE,
    )
    // Medir es lo que permite colgar la marca del borde del número sin que el número se
    // entere. Se mide una vez por texto: esto repinta varias veces por segundo.
    // El contador de series va apagado: es contexto, no el dato. Si pesara lo mismo que la
    // unidad, el ojo tendria que elegir entre tres cosas del mismo tamano.
    val leadStyle = markStyle.copy(color = Color.White.copy(alpha = 0.7f))
    val valueHalf = remember(value) { measurer.measure(value, valueStyle).size.width / 2 }
    val markHalf = remember(mark) {
        if (mark == null) 0 else measurer.measure(mark, markStyle).size.width / 2
    }
    val leadHalf = remember(lead) {
        if (lead == null) 0 else measurer.measure(lead, leadStyle).size.width / 2
    }
    val gap = with(LocalDensity.current) { READOUT_GAP.roundToPx() }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(value, style = valueStyle)
        if (lead != null) {
            Text(
                lead,
                style = leadStyle,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(-(valueHalf + gap + leadHalf), 0) },
            )
        }
        if (mark != null) {
            Text(
                mark,
                style = markStyle,
                // A la DERECHA del numero: asi se lee en el orden natural, "quince reps".
                // A la izquierda obligaba a leer al reves.
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(valueHalf + gap + markHalf, 0) },
            )
        }
    }
}

@Composable
private fun ClockDisplay(step: PlayerStep, remainingMs: Long, padded: Boolean, lead: String? = null) {
    val shown = if (step.display == DisplayMode.COUNTUP) {
        (step.durationSec * 1000L - remainingMs).coerceAtLeast(0L)
    } else remainingMs
    BigReadout(formatPlayerClock(shown, padded), lead = lead)
}

@Composable
private fun WeightFeedback(vm: MasterViewModel, step: PlayerStep, accent: Color, t: Strings) {
    val current = vm.weightFeedback[vm.feedbackKey(step.ownerExerciseId, step.workoutIndex, step.setIndex, step.side)]?.deltaKg
    // Translucida y no negra: todo lo demas de esta pantalla -los controles, la franja de
    // rutina- deja ver el color de la etapa, y un bloque solido rompia esa gramatica.
    //
    // Y de dos filas en vez de cuatro: el peso y la pregunta comparten linea, porque los
    // tres botones de abajo ya dicen de que va. Lo que se ahorra se lo lleva la nota.
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // Solo el total. La pregunta es por el peso que se movio, y desglosarlo en
                // "6 + 40" es trabajo de la tarjeta de carga del descanso (TD-130).
                "${fmtKg(step.weightTotal)} ${t.kg}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                "  ·  ${t.howWeightFelt}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedbackChip("${t.tooHeavy} ↓", current == -2.5, accent) {
                vm.recordFeedback(step.ownerExerciseId, step.workoutIndex, step.setIndex, step.ownerName, step.weightTotal, -2.5, step.side)
            }
            FeedbackChip(t.justRight, current == 0.0, accent) {
                vm.recordFeedback(step.ownerExerciseId, step.workoutIndex, step.setIndex, step.ownerName, step.weightTotal, 0.0, step.side)
            }
            FeedbackChip("${t.tooLight} ↑", current == 2.5, accent) {
                vm.recordFeedback(step.ownerExerciseId, step.workoutIndex, step.setIndex, step.ownerName, step.weightTotal, 2.5, step.side)
            }
        }
    }
}

/**
 * Lo que hay que cargar para la próxima serie, dibujado (TD-130).
 *
 * Nace de una cuenta hecha entre series que salió mal: el player decía "55 kg" y había que
 * restar la barra, dividir entre dos y armar el lado con los discos que hay. El app ya sabe
 * la barra y los discos, así que dibuja la barra con los discos de UN lado -del tamaño de
 * su peso, como una barra de verdad- y la barra a la izquierda. Se ve de un golpe de ojo, a
 * dos metros, cansado.
 *
 * Con mancuernas no hay cuenta pero sí cantidad: "1 de 7.5" no es "2 de 7.5".
 *
 * Todo en blanco sobre el color de la etapa: el fondo del player ES el acento, así que un
 * disco del color del acento desaparecería -ya pasó con el peso del NEXT-.
 */
@Composable
private fun LoadCard(next: PlayerStep, t: Strings) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${t.setWord.uppercase()} ${next.setIndex + 1}/${next.totalSets}  \u00b7  ${fmtKg(next.weightTotal)} ${t.kg.uppercase()}",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        when (next.weightType) {
            WeightType.BARBELL -> {
                val discos = Plates.perSide(next.weightTotal - next.barWeight)
                if (discos == null) {
                    Text(
                        "${fmtKg(next.weightTotal)} ${t.kg} \u00b7 ${t.cantBuild}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    BarDrawing(next.barWeight, discos)
                    if (discos.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(t.barOnly, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                    }
                }
            }
            WeightType.DUMBBELL -> {
                val cada = if (next.dumbbellCount > 0) next.weightTotal / next.dumbbellCount else next.weightTotal
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(next.dumbbellCount.coerceIn(1, 2)) { DumbbellDrawing(cada) }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${next.dumbbellCount} \u00d7 ${fmtKg(cada)} ${t.kg}",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                )
            }
            else -> Text(
                "${fmtKg(next.weightTotal)} ${t.kg}",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Alto de cada disco respecto al de 20: se lee el tamaño antes que el número. */
private fun alturaDisco(kg: Double): Float = when {
    kg >= 20.0 -> 1.0f
    kg >= 10.0 -> 0.78f
    kg >= 5.0 -> 0.62f
    kg >= 2.5 -> 0.48f
    else -> 0.38f
}

/**
 * La barra COMPLETA, a todo el ancho de la tarjeta, con los discos en los dos extremos.
 *
 * Como una barra de verdad: los discos grandes hacia dentro y los chicos hacia fuera, el
 * peso de la barra en el centro, y NADA despues del ultimo disco -"termina el disco,
 * termina la barra"-. El largo de la barra es lo que la distingue de una mancuerna: la
 * primera version la dibujo con 52dp de centro y parecia una.
 *
 * El numero va en los discos de los DOS lados: se carga un lado y luego el otro, y cada lado
 * tiene que poder leerse solo.
 */
@Composable
private fun BarDrawing(barKg: Double, discos: List<Double>) {
    val alto = 88.dp
    val separacion = 2.dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(alto)) {
        // Los discos se quedan con hasta 22dp cada uno; la barra, con todo lo demas. Si hay
        // muchos discos se afinan antes que dejar la barra en menos de 80dp.
        val porLado = discos.size.coerceAtLeast(1)
        val ancho = ((maxWidth - 80.dp) / (2 * porLado) - separacion).coerceIn(10.dp, 22.dp)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // Izquierda: de fuera hacia dentro, del mas chico al mas grande.
            discos.reversed().forEach { kg -> Disco(kg, ancho, alto, separacion) }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(8.dp).background(Color.White.copy(alpha = 0.55f)))
                // Etiqueta OSCURA con numero blanco. Era blanca translucida sobre una barra
                // tambien clara, y el peso de la barra se leia apagado. Oscura se separa de la
                // barra y de los discos -que son blancos- en cualquier color de etapa.
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(fmtKg(barKg), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            // Derecha: de dentro hacia fuera, del mas grande al mas chico.
            discos.forEach { kg -> Disco(kg, ancho, alto, separacion) }
        }
    }
}

@Composable
private fun Disco(kg: Double, ancho: androidx.compose.ui.unit.Dp, alto: androidx.compose.ui.unit.Dp, separacion: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .padding(horizontal = separacion / 2)
            .width(ancho)
            .height(alto * alturaDisco(kg))
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        // Vertical: en un disco delgado el numero horizontal no entra ("1.25").
        Text(
            fmtKg(kg),
            color = Color.Black.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.rotate(-90f).wrapContentSize(unbounded = true),
        )
    }
}

/**
 * Una mancuerna de cabeza hexagonal, como las de la casa, con su peso en el cuadrado del
 * centro de UNA cabeza -la derecha-: el mismo numero en las dos se leeria como el doble.
 *
 * La cabeza vista de lado: un rectangulo con las esquinas de fuera cortadas, y dentro las
 * caras del hexagono -el cuadrado central y las dos bandas de arriba y abajo-.
 */
@Composable
private fun DumbbellDrawing(kg: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CabezaHexagonal(fueraALaIzquierda = true, peso = null)
        Box(Modifier.width(34.dp).height(12.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.85f)))
        CabezaHexagonal(fueraALaIzquierda = false, peso = kg)
    }
}

@Composable
private fun CabezaHexagonal(fueraALaIzquierda: Boolean, peso: Double?) {
    val linea = Color.Black.copy(alpha = 0.35f)
    Box(Modifier.width(36.dp).height(52.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val corte = w * 0.22f
            // El lado de fuera lleva el corte grande; el de dentro, uno chico.
            val cFuera = corte
            val cDentro = corte * 0.35f
            val (cIzq, cDer) = if (fueraALaIzquierda) cFuera to cDentro else cDentro to cFuera
            val silueta = Path().apply {
                moveTo(cIzq, 0f)
                lineTo(w - cDer, 0f)
                lineTo(w, cDer * 1.6f)
                lineTo(w, h - cDer * 1.6f)
                lineTo(w - cDer, h)
                lineTo(cIzq, h)
                lineTo(0f, h - cIzq * 1.6f)
                lineTo(0f, cIzq * 1.6f)
                close()
            }
            drawPath(silueta, Color.White)
            // Las caras: dos cortes horizontales y el cuadrado del centro.
            val arriba = h * 0.30f
            val abajo = h * 0.70f
            val margen = w * 0.14f
            val trazo = 1.6.dp.toPx()
            drawLine(linea, Offset(0f, arriba), Offset(w, arriba), trazo)
            drawLine(linea, Offset(0f, abajo), Offset(w, abajo), trazo)
            drawLine(linea, Offset(margen, arriba), Offset(margen, abajo), trazo)
            drawLine(linea, Offset(w - margen, arriba), Offset(w - margen, abajo), trazo)
        }
        if (peso != null) {
            Text(fmtKg(peso), color = Color.Black.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/**
 * La velocidad de la caminata, ajustable mientras se camina (TD-124).
 *
 * Ocupa el mismo sitio que la tarjeta del peso y se lee igual: el número que dosifica este
 * ejercicio, con lo que hace falta para cambiarlo. Antes la velocidad iba escrita en la
 * nota -"6 km/h, arms loose"-, o sea texto que nadie puede comparar entre sesiones, y era
 * justo la variable que más había movido el dolor: 3, 4, 5 y 6 km/h en cuatro días.
 *
 * Lo que enseñe al terminar la serie es lo que queda en el registro, se haya tocado o no:
 * si caminó a lo prescrito, eso es lo que hizo.
 */
@Composable
private fun SpeedCard(vm: MasterViewModel, step: PlayerStep, t: Strings) {
    val actual = vm.speedOf(step) ?: return
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeedStep("\u2212") { vm.recordSpeed(step, actual - SPEED_STEP) }
        Text(
            "${fmtNum(actual)} ${t.kmh}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(110.dp),
        )
        SpeedStep("+") { vm.recordSpeed(step, actual + SPEED_STEP) }
    }
}

@Composable
private fun SpeedStep(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun FeedbackChip(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) accent else Color.White.copy(alpha = 0.15f))
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
        // El hueco grande del centro se reserva SIEMPRE, tenga botón o no. En un ejercicio
        // por repeticiones no hay nada que pausar, y antes el check se hinchaba y ocupaba
        // ese centro: al cambiar de un ejercicio por tiempo a uno por reps, los tres
        // controles cambiaban de sitio y de tamaño. Ahora la fila es siempre la misma
        // —pequeño, grande, pequeño— y lo único que cambia es si el centro está vacío.
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            if (step.manual) {
                // El boton se queda, apagado. Antes era un circulo de solo borde, y con el
                // cristal encima del video no se distinguia del fondo: "es tan imperceptible
                // que igual ni se nota". Un boton deshabilitado —mismo cristal, icono al 30%,
                // sin responder al toque— dice lo que pasa: aqui no hay nada que pausar
                // porque un ejercicio por repeticiones no corre contra el reloj.
                GlassButton(
                    icon = Icons.Outlined.Pause,
                    contentDescription = t.pause,
                    size = 72.dp,
                    iconSize = 36.dp,
                    enabled = false,
                    onClick = {},
                )
            } else {
                GlassButton(
                    icon = if (vm.playerRunning) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (vm.playerRunning) t.pause else t.resume,
                    size = 72.dp,
                    iconSize = 36.dp,
                    onClick = {
                        if (vm.playerRunning) vm.pausePlayer() else vm.resumePlayer()
                    },
                )
            }
        }
        GlassButton(
            icon = Icons.Outlined.Check,
            contentDescription = t.check,
            size = 48.dp,
            iconSize = 24.dp,
            onClick = { vm.checkStep() },
        )
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
    /**
     * Apagado, el boton NO se va: se queda con su mismo cristal y apaga el icono y el borde.
     * Es como lo resuelve YouTube, y es lo que hacia falta aqui: un hueco, o un circulo de
     * solo borde, se confunden con el fondo y parece que falta un boton.
     */
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 0.25f else 0.12f)), CircleShape)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.30f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * "Como te fue", en la pantalla de fin de sesion (TD-089).
 *
 * Se guarda **en cuanto toca cada cosa**, sin boton de enviar. El usuario lo pidio asi:
 * "va a ayudar bastante que el registro sea en caliente, porque despues se vuelve un
 * ejercicio de memoria y que muchas veces falla". Un formulario que hay que confirmar es
 * una ocasion mas de olvidarse, y media respuesta guardada vale mas que una completa que
 * no llego.
 *
 * Los numeros van en una rejilla de 0 a 10 y no en un stepper: llegar a 4 son cuatro
 * toques con el stepper y uno aqui, y esto se contesta de pie y sudando.
 *
 * "Dolor antes" se pregunta al final, que sigue siendo memoria aunque sea de hace media
 * hora. Preguntarlo al empezar es lo correcto y queda pendiente.
 */
@Composable
private fun HowItWent(vm: MasterViewModel, accent: Color, t: Strings) {
    val saved = vm.lastSessionFeedback()
    var note by remember(saved?.id) { mutableStateOf(saved?.note ?: "") }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.colors.surface)
            .padding(14.dp),
    ) {
        Text(t.howItWent, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))

        // Lo de la mañana va PRIMERO porque es lo que se quiere mover: el dolor de años, no
        // el de la sesión. Y se pregunta aquí, al terminar, y no antes de empezar: la
        // pantalla de arranque tiene que dejar darle Start, y a esa hora es lo único que se
        // quiere hacer.
        PainScale(t.painOnWaking, saved?.painOnWaking, accent, t) { vm.saveHowItWent(painOnWaking = it) }
        Spacer(Modifier.height(12.dp))
        FadeMinutes(saved?.painFadeMin, accent, t) { vm.saveHowItWent(painFadeMin = it) }
        Spacer(Modifier.height(12.dp))

        PainScale(t.painBefore, saved?.painBefore, accent, t) { vm.saveHowItWent(painBefore = it) }
        Spacer(Modifier.height(12.dp))
        PainScale(t.painAfter, saved?.painAfter, accent, t) { vm.saveHowItWent(painAfter = it) }
        Spacer(Modifier.height(12.dp))

        Text(t.painWhere, color = AppTheme.colors.textDim, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedbackChip(t.painCentered, saved?.radiating == false, accent) { vm.saveHowItWent(radiating = false) }
            FeedbackChip(t.painRadiating, saved?.radiating == true, accent) { vm.saveHowItWent(radiating = true) }
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(t.sessionNote, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = AppTheme.colors.textFaded,
                focusedTextColor = AppTheme.colors.textPrimary,
                unfocusedTextColor = AppTheme.colors.textPrimary,
                cursorColor = accent,
                focusedLabelColor = accent,
                unfocusedLabelColor = AppTheme.colors.textDim,
            ),
        )
        // La nota se guarda al salir del campo: escribir letra a letra en disco no aporta
        // nada, pero perderla por cerrar la pantalla si quita.
        DisposableEffect(note) {
            onDispose { vm.saveHowItWent(note = note) }
        }
    }
}

/**
 * Los once numeros del dolor, de un toque. Dos filas para que quepan a 400dp.
 *
 * [hint] ancla los extremos -"0 = nada, 10 = lo peor que has sentido"- y no es adorno: sin
 * anclas, un 4 de hoy y un 4 de dentro de un mes no son el mismo 4, y la serie deja de
 * servir para comparar. Es la idea que Freeletics aplica con palabras -cada opcion se
 * explica sola- traida a una escala numerica, que para el dolor es lo estandar.
 */
/**
 * Cuántos minutos tardó en aflojar el dolor de la mañana (TD-125).
 *
 * Opciones sueltas y no un contador: nadie mide esto con cronómetro, se dice "unos quince".
 * Los valores están elegidos para que el paso normal -entre 10 y 15 minutos- se conteste de
 * un toque, y para que la cifra que cambiaría el cuadro -45 o más- exista y se pueda marcar.
 */
@Composable
private fun FadeMinutes(value: Int?, accent: Color, t: Strings, onPick: (Int) -> Unit) {
    Text(t.painFadeMin, color = AppTheme.colors.textDim, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    listOf(listOf(0, 5, 10, 15), listOf(20, 30, 45, 60)).forEach { fila ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            fila.forEach { min ->
                val activo = value == min
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (activo) accent else AppTheme.colors.track)
                        .clickable { onPick(min) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (min == 60) "60+" else "$min",
                        color = if (activo) AppTheme.colors.onAccent else AppTheme.colors.textDim,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun PainScale(label: String, value: Int?, accent: Color, t: Strings, onPick: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    // El MISMO estilo con el que se pinta, no uno parecido: Text parte del estilo del tema y
    // le fusiona lo que se le pase, asi que un TextStyle construido a mano trae otra familia
    // y otro interlineado, y midiendo con el sale un alto que el texto de verdad no respeta.
    val estiloDescriptor = LocalTextStyle.current.merge(TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))

    Text(label, color = AppTheme.colors.textDim, fontSize = 13.sp)
    // El texto del numero elegido, no un ancla en los extremos: "5 es medio" se estima, pero
    // "4 exactamente que es" no se adivina, y sin eso un 4 de hoy y un 4 de dentro de un mes
    // no son el mismo 4. En ambar y en su sitio fijo, para que al tocar un numero se note
    // que la pantalla contesta y para que no empuje nada al aparecer.
    //
    // El sitio fijo sale de los propios descriptores: cuantas lineas necesita el mas largo
    // de los once a este ancho. Antes eran dos reservadas a mano y ninguno llega a dos, asi
    // que debajo quedaba siempre una linea vacia.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val ancho = constraints.maxWidth
        // Se miden LINEAS, no pixeles, y el alto lo reserva el propio Text con minLines. Un
        // alto en pixeles obliga a acertar al pixel con el estilo, el relleno de la fuente y
        // la densidad, y si se falla por poco el texto sale recortado -paso: la "y" de
        // "Hardly" se quedo sin cola-. Contando lineas, equivocarse cuesta una linea de mas,
        // nunca una letra partida.
        val lineas = remember(ancho, t, estiloDescriptor) {
            (t.painScale.mapIndexed { i, d -> "$i · $d" } + t.painScaleHint).maxOf {
                measurer.measure(it, estiloDescriptor, constraints = Constraints(maxWidth = ancho), maxLines = 2).lineCount
            }
        }
        Text(
            value?.let { "$it · ${t.painScale.getOrElse(it) { "" }}" } ?: t.painScaleHint,
            color = if (value != null) STATUS_SKIPPED else AppTheme.colors.textFaded,
            fontWeight = if (value != null) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 12.sp,
            maxLines = lineas,
            minLines = lineas,
        )
    }
    Spacer(Modifier.height(6.dp))
    (0..10).chunked(6).forEachIndexed { indice, fila ->
        // El hueco va ENTRE las filas y no despues de cada una: el de despues de la ultima
        // se sumaba al que ya pone quien coloca la escala, y eran seis puntos de nada.
        if (indice > 0) Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            fila.forEach { n ->
                val activo = value == n
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (activo) accent else AppTheme.colors.track)
                        .clickable { onPick(n) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$n",
                        color = if (activo) AppTheme.colors.onAccent else AppTheme.colors.textDim,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }
            // Rellena el hueco de la segunda fila para que los botones no se estiren.
            repeat(6 - fila.size) { Spacer(Modifier.weight(1f)) }
        }
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
            if (vm.asksHowItWent()) {
                item { HowItWent(vm, accent, t) }
            }
            // Las series con peso que se quedaron sin marcar, para cerrarlas de un toque
            // mientras se toma el agua (TD-122). La ultima serie de un ejercicio no tiene
            // descanso detras, asi que sin esto no hay ningun momento para contestarla.
            val pendientes = vm.unmarkedWeightSets()
            if (pendientes.isNotEmpty()) {
                item {
                    Text(t.pendingFeedback, color = AppTheme.colors.textDim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                }
                items(pendientes, key = { "${it.ownerExerciseId}:${it.workoutIndex}:${it.setIndex}" }) { paso ->
                    val marcado = vm.weightFeedback[vm.feedbackKey(paso.ownerExerciseId, paso.workoutIndex, paso.setIndex, paso.side)]?.deltaKg
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppTheme.colors.surface)
                            .padding(14.dp),
                    ) {
                        Text(
                            ExerciseCatalog.display(paso.ownerExerciseId, paso.ownerName, t.locale.language) +
                                "  ·  ${paso.setIndex + 1}/${paso.totalSets}  ·  ${fmtKg(paso.weightTotal)} ${t.kg}",
                            color = AppTheme.colors.textPrimary,
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FeedbackChip("${t.tooHeavy} ↓", marcado == -2.5, accent) { vm.markFinishedFeedback(paso, -2.5) }
                            FeedbackChip(t.justRight, marcado == 0.0, accent) { vm.markFinishedFeedback(paso, 0.0) }
                            FeedbackChip("${t.tooLight} ↑", marcado == 2.5, accent) { vm.markFinishedFeedback(paso, 2.5) }
                        }
                    }
                }
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
