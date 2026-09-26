package com.maurozegarra.master.ui.master

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.MasterViewModel
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.model.ExerciseRecord
import com.maurozegarra.master.model.ExerciseStatus
import com.maurozegarra.master.model.SessionLog
import com.maurozegarra.master.ui.SwipeAction
import com.maurozegarra.master.ui.SwipeActionsRow
import com.maurozegarra.master.ui.SwipeRowsController
import com.maurozegarra.master.ui.rememberSwipeRowsController
import com.maurozegarra.master.model.SessionSource
import com.maurozegarra.master.model.SessionRecorder
import com.maurozegarra.master.model.SessionStatus
import com.maurozegarra.master.ui.theme.Dims
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.STATUS_DONE
import com.maurozegarra.master.model.hasFeedback
import androidx.compose.ui.text.font.FontStyle
import com.maurozegarra.master.ui.theme.STATUS_SKIPPED
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
fun HistoryScreen(vm: MasterViewModel, accent: Color, t: Strings) {
    // El propio o el de un atleta (TD-126): la misma pantalla. El de un atleta no se borra.
    val sessions = vm.historySessions
    val ajeno = vm.historyAthlete != null

    // De quien es, arriba, antes que nada (TD-168): el historial de NIKO se elige aqui y ya
    // no se busca en Settings. Sin atletas no hay nada que elegir y no se dibuja.
    if (vm.historyOwners.isNotEmpty()) {
        Column(Modifier.fillMaxSize()) {
            HistoryOwnerPicker(vm, accent, t)
            Box(Modifier.weight(1f)) { HistoryBody(vm, sessions, ajeno, accent, t) }
        }
        return
    }
    HistoryBody(vm, sessions, ajeno, accent, t)
}

/**
 * Chips con el propio y cada atleta (TD-168). Chips y no un desplegable: son dos o tres
 * nombres, y verlos todos a la vez es un toque menos que abrir un menu.
 */
@Composable
private fun HistoryOwnerPicker(vm: MasterViewModel, accent: Color, t: Strings) {
    val actual = vm.historyAthlete?.id
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OwnerChip(t.historyOf.me, actual == null, accent) { vm.closeAthleteHistory() }
        vm.historyOwners.forEach { p ->
            OwnerChip(p.name, actual == p.id, accent) { vm.openAthleteHistory(p) }
        }
    }
}

@Composable
private fun OwnerChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) accent else AppTheme.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) AppTheme.colors.onAccent else AppTheme.colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HistoryBody(vm: MasterViewModel, sessions: List<SessionLog>, ajeno: Boolean, accent: Color, t: Strings) {
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
    // Con .toList() y no la lista: el historial propio es SIEMPRE la misma lista mutable, y
    // como clave no cambiaba al borrar -la sesion borrada seguia en pantalla hasta salir y
    // volver-. La copia si cambia. (Se rompio al abrir el historial a los atletas, TD-126.)
    val groups = remember(sessions.toList()) {
        sessions
            .sortedByDescending { it.completedAt }
            .groupBy { Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate() }
            .toList()
            .sortedByDescending { it.first }
    }

    val listState = rememberLazyListState()
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
                        swipeController = swipeController,
                        onDelete = if (ajeno) null else { { vm.deleteSession(s.id) } },
                        onExerciseClick = { exerciseId -> vm.openExerciseHistory(exerciseId) },
                    )
                }
            }
        }
    }
}

@Composable
fun SessionRow(
    session: SessionLog,
    time: String,
    accent: Color,
    t: Strings,
    swipeController: SwipeRowsController,
    /** Null en el historial de un atleta: su registro no lo borra el coach. */
    onDelete: (() -> Unit)?,
    onExerciseClick: (String) -> Unit,
    initiallyExpanded: Boolean = false,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Una sola acción: una sesión no se duplica ni se edita, solo se borra. El menú
    // desplegable que había aquí gastaba 48dp en ofrecer exactamente eso mismo.
    val actions = if (onDelete == null) emptyList() else listOf(SwipeAction(Icons.Outlined.Delete, t.delete) { confirmDelete = true })

    // Borrar se desliza solo con la sesion CERRADA (TD-119). Abierta, la tarjeta es alta y
    // el deslizamiento arrastraba todo su contenido -las series quedaban cortadas por el
    // borde- y dejaba el tacho flotando a media altura. Y es cuando se esta leyendo: un
    // gesto lateral sin querer al hacer scroll ponia "borrar" delante de lo que se revisa.
    SwipeActionsRow(actions = actions, controller = swipeController, enabled = !expanded && onDelete != null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.row))
            .background(AppTheme.colors.surface)
            // La tarjeta entera despliega. Antes eso lo hacía un IconButton, que reserva
            // 48dp de área táctil y era quien marcaba el alto de la fila de abajo; con el
            // área en la tarjeta, el chevrón puede ser un icono de 24 y la tarjeta baja de
            // 99 a ~75dp, por debajo incluso de los 80 que medía antes de todo esto.
            .then(
                if (session.exercises.isEmpty()) {
                    Modifier
                } else {
                    Modifier.clickable {
                        if (!swipeController.consumeTapIfOpen()) expanded = !expanded
                    }
                }
            )
            .padding(Dims.rowPadding),
    ) {
        // El nombre tiene su propia línea, a todo el ancho de la tarjeta.
        //
        // Antes compartía línea con el badge y con los dos iconos, así que de los 320dp
        // que tiene la tarjeta por dentro le quedaban unos 156: "COLUMNA (asignado)(copy)"
        // no cabía, se recortaba, y dos sesiones distintas se leían igual. Bajando el
        // badge y los iconos a la segunda línea —que estaba casi vacía— pasa a tener los
        // 320 enteros, y ese nombre ocupa ~216.
        //
        // Sigue con una línea y puntos suspensivos, pero ya solo como último recurso:
        // recortar es lo que hay que hacer cuando de verdad no cabe, no la primera
        // respuesta ante un nombre normal.
        Text(
            session.trainingName.ifBlank { t.noName },
            color = AppTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(time, color = AppTheme.colors.textDim, fontSize = 13.sp)
            val isPartial = session.status == SessionStatus.PARTIAL
            StatusBadge(
                text = if (isPartial) t.partial else t.complete,
                color = if (isPartial) accent else STATUS_DONE,
            )
            // Solo cuando NO la midio el player. Lo normal no lleva sello: un historial con
            // una etiqueta en cada fila no distingue nada, y lo que hay que poder ver de un
            // vistazo es la excepcion. Va en gris y no en el color de la etapa porque no es
            // un estado del entrenamiento, es de donde salio el dato.
            when (session.source) {
                SessionSource.MEASURED -> Unit
                SessionSource.RECONSTRUCTED -> StatusBadge(t.sourceRebuilt, AppTheme.colors.textDim)
                SessionSource.EDITED -> StatusBadge(t.sourceEdited, AppTheme.colors.textDim)
            }
            // El dolor de entrada y el de salida, que es el dato por el que se hace todo
            // esto. Verde si bajo, ambar si subio, apagado si se quedo igual: la direccion
            // es lo que se lee de un vistazo, el numero viene despues.
            val antes = session.painBefore
            val despues = session.painAfter
            if (antes != null && despues != null) {
                StatusBadge(
                    text = "$antes → $despues",
                    color = when {
                        despues < antes -> STATUS_DONE
                        despues > antes -> STATUS_SKIPPED
                        else -> AppTheme.colors.textDim
                    },
                )
            }
            // Empuja los iconos a la derecha. La hora y el badge se quedan juntos a la
            // izquierda, y como la hora no cambia de ancho, el badge no se mueve de sitio
            // entre una tarjeta y otra.
            Spacer(Modifier.weight(1f))
            if (session.exercises.isNotEmpty()) {
                // Indicador, no botón: quien recoge el toque es la tarjeta entera. Un `>`
                // que gira 90°, la misma gramática que el preview del training, en vez de
                // dos iconos distintos para abierto y cerrado.
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = AppTheme.colors.textDim,
                    modifier = Modifier.rotate(if (expanded) 90f else 0f),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded && session.hasFeedback(),
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            SessionFeedback(session, t)
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
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = AppTheme.colors.surface,
            titleContentColor = AppTheme.colors.textPrimary,
            textContentColor = AppTheme.colors.textDim,
            title = { Text(t.delete) },
            text = { Text(t.deleteSessionConfirm(session.trainingName)) },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke(); confirmDelete = false }) {
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

/**
 * Lo que contesto el usuario de esa sesion: el dolor con su descripcion, donde, y su nota.
 *
 * Existe porque sin esto el dato entraba y desaparecia de su vista: el contestaba antes y
 * despues de entrenar y no habia ninguna pantalla donde volver a verlo. El numero suelto de
 * un dia dice poco; la serie es lo que dice algo, y para eso hay que poder mirarla.
 *
 * Lo que no se contesto no se rellena: un hueco es un hueco.
 */
@Composable
private fun SessionFeedback(session: SessionLog, t: Strings) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOfNotNull(
            session.painOnWaking?.let {
                "${t.painOnWaking}: $it · ${t.painScale.getOrElse(it) { "" }}" +
                    (session.painFadeMin?.let { m -> "  ·  ${if (m >= 60) "60+" else "$m"} min" } ?: "")
            },
            session.painBefore?.let { "${t.painBefore}: $it · ${t.painScale.getOrElse(it) { "" }}" },
            session.painAfter?.let { "${t.painAfter}: $it · ${t.painScale.getOrElse(it) { "" }}" },
            session.radiating?.let { if (it) t.painRadiating else t.painCentered },
        ).forEach {
            Text(it, color = AppTheme.colors.textDim, fontSize = 13.sp)
        }
        if (session.note.isNotBlank()) {
            Text(
                session.note,
                color = AppTheme.colors.textPrimary,
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
            )
        }
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
    val allSkipped = exercises.isNotEmpty() && exercises.all { it.status == ExerciseStatus.SKIPPED }
    val allComplete = !allSkipped && SessionRecorder.workoutComplete(exercises)
    val badgeColor = when {
        allSkipped -> STATUS_SKIPPED
        allComplete -> STATUS_DONE
        else -> accent
    }
    val badgeText = when {
        allSkipped -> t.skipped
        allComplete -> t.complete
        else -> t.partial
    }

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
            // Esta fila ya tenía el `weight`, y por eso su badge nunca se partió. Le
            // faltaba el límite de línea: un nombre de workout largo seguía estirando la
            // sección a lo alto.
            Text(
                workoutName,
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StatusBadge(text = badgeText, color = badgeColor)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppTheme.colors.textDim,
                modifier = Modifier.rotate(if (expanded) 90f else 0f),
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
        er.sets.forEachIndexed { i, sr -> SetLine(i, sr, er.timeBased, t, 12.sp) }
        // Solo para registros de antes de TD-117, que guardaban uno por ejercicio. Los de
        // ahora ya lo dicen serie a serie arriba, y repetirlo aqui seria contarlo dos veces.
        if (er.sets.none { it.feedbackDeltaKg != null } && er.feedbackDeltaKg != null && er.feedbackDeltaKg != 0.0) {
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
fun dayLabel(date: LocalDate, zone: ZoneId, t: Strings): String {
    val today = LocalDate.now(zone)
    return when (date) {
        today -> t.today
        today.minusDays(1) -> t.yesterday
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(t.locale))
    }
}
