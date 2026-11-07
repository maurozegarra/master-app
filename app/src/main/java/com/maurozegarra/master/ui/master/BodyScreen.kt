package com.maurozegarra.master.ui.master

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.MaterialTheme
import com.maurozegarra.master.ui.SwipeAction
import com.maurozegarra.master.ui.SwipeActionsRow
import com.maurozegarra.master.ui.SwipeRowsController
import com.maurozegarra.master.ui.rememberSwipeRowsController
import com.maurozegarra.master.MasterViewModel
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.model.BodyEntry
import com.maurozegarra.master.model.BodyLog
import com.maurozegarra.master.ui.AppTextField
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.STATUS_DONE
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Los pesajes de los sábados (TD-169), en la pestaña Body del historial: el propio o el del
 * atleta elegido arriba, con la misma lógica que las sesiones.
 *
 * Arriba lo último y cuánto cambió; después las curvas, que es lo que se mira -una balanza
 * de bioimpedancia sirve para tendencia, no para el número del día-; y abajo la tabla.
 *
 * Todo con los componentes del app: [StatTile] y [LineChartCard] sobre [SectionCard], las
 * filas con [listCard], las pestañas con [SegmentToggle] y el campo con [AppTextField].
 */
@Composable
fun BodyTab(vm: MasterViewModel, accent: Color, t: Strings) {
    val b = t.more.body
    val entries = vm.historyBody
    val propio = vm.historyAthlete == null
    if (entries.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(if (propio) b.empty else b.emptyAthlete, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            // El + tambien anota por el atleta (TD-205): la pista vale para los dos.
            Text(b.emptyHint, color = AppTheme.colors.textDim, fontSize = 14.sp)
        }
        return
    }
    val alto = BodyLog.heightOf(entries)
    val conCintura = entries.filter { it.waistCm != null }
    val conPeso = entries.filter { it.weightKg != null }
    val conMusculo = entries.filter { it.skeletalKg != null }
    val listState = rememberLazyListState()
    val swipeController = rememberSwipeRowsController()
    // Como en las sesiones: al desplazarse se cierra la fila abierta.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) swipeController.closeAll()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "tiles") {
            // Las tarjetas no crecen, en ningún caso (TD-207, 7-nov): cada línea de StatTile es
            // una sola. Con "+0.3 vs previous" Weight y Waist pasaban a dos líneas; igualar las
            // alturas fue peor (crecían las tres) y poner la flecha al lado del número no cabía
            // en un tercio del ancho. El cambio va solo, con su flecha, en la línea de abajo.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // El cambio va en gris y no en verde o rojo: para uno bajar es bueno y para otra
                // no (NIKO tiene que construir).
                StatTile(
                    b.weight, conPeso.lastOrNull()?.weightKg?.let(::fmtNum) ?: "–", Modifier.weight(1f),
                    unit = t.kg, delta = change(conPeso.map { it.weightKg!! }),
                )
                StatTile(
                    b.waist, conCintura.lastOrNull()?.waistCm?.let(::fmtNum) ?: "–", Modifier.weight(1f),
                    unit = "cm", delta = change(conCintura.map { it.waistCm!! }),
                )
                val ratio = conCintura.lastOrNull()?.let { BodyLog.waistToHeight(it, alto) }
                StatTile(
                    b.waistHeight, ratio?.let(::two) ?: "–", Modifier.weight(1f),
                    caption = "${b.goal} < ${two(BodyLog.WAIST_HEIGHT_GOAL)}",
                    valueColor = if (ratio != null && ratio < BodyLog.WAIST_HEIGHT_GOAL) STATUS_DONE else AppTheme.colors.textPrimary,
                )
            }
        }
        if (conPeso.size >= 2) {
            item(key = "chart-weight") {
                LineChartCard(b.weight, conPeso.map { it.date to it.weightKg!! }, accent, ::fmtNum, ::dia, unit = t.kg)
            }
        }
        if (conCintura.size >= 2) {
            item(key = "chart-waist") {
                // La meta de la cintura es la de cintura / estatura llevada a centímetros.
                val meta = alto?.let { Math.round(it * BodyLog.WAIST_HEIGHT_GOAL * 10) / 10.0 }
                LineChartCard(
                    b.waist, conCintura.map { it.date to it.waistCm!! }, accent, ::fmtNum, ::dia, unit = "cm",
                    goal = meta, goalLabel = meta?.let { "${b.goal} < ${fmtNum(it)}" },
                )
            }
        }
        if (conMusculo.size >= 2) {
            item(key = "chart-skeletal") {
                LineChartCard(b.skeletal, conMusculo.map { it.date to it.skeletalKg!! }, accent, ::fmtNum, ::dia, unit = t.kg)
            }
        }
        // El conteo va en la pestaña ("Body · 4"), no en una fila antes de la tabla.
        items(entries.reversed(), key = { "row-${it.date}" }) { e ->
            // Las del atleta tambien se corrigen y se borran: el coach las anota por el (TD-205).
            EntryRow(e, alto, true, t, swipeController, onEdit = { vm.editWeighIn(e) }, onDelete = { vm.deleteWeighIn(e.date) })
        }
    }
}

private val DIA = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

private fun dia(date: String): String = runCatching { LocalDate.parse(date).format(DIA) }.getOrDefault(date)

/** Cintura / estatura y su meta, siempre con dos decimales: 0.52, 0.50. */
private fun two(v: Double): String = String.format(Locale.US, "%.2f", v)

/** La diferencia con el pesaje anterior; null con uno solo o sin cambio. */
private fun change(values: List<Double>): Double? {
    if (values.size < 2) return null
    return BodyLog.delta(values.last(), values[values.size - 2])
}

/**
 * Una fila de la tabla. Igual que una sesión: tocarla abre el pesaje para corregirlo, y
 * borrar es deslizar a la izquierda, con la misma confirmación (el 3-oct un "Delete" suelto
 * dentro del diálogo borró sus cuatro pesajes sin preguntar).
 */
@Composable
private fun EntryRow(
    e: BodyEntry,
    alto: Double?,
    editable: Boolean,
    t: Strings,
    swipeController: SwipeRowsController,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val b = t.more.body
    var confirmDelete by remember { mutableStateOf(false) }
    val actions = if (editable) listOf(SwipeAction(Icons.Outlined.Delete, t.delete) { confirmDelete = true }) else emptyList()
    val partes = listOfNotNull(
        e.weightKg?.let { "${fmtNum(it)} ${t.kg}" },
        e.waistCm?.let { "${fmtNum(it)} cm" },
        BodyLog.waistToHeight(e, alto)?.let(::two),
        e.skeletalKg?.let { "${b.skeletal.lowercase()} ${fmtNum(it)}" },
    )
    // El toque va por dentro de la tarjeta, como en la del training: listCard ya recorta y
    // rellena, y un clickable por fuera dibujaria el destello sin las esquinas redondeadas.
    SwipeActionsRow(actions = actions, controller = swipeController, enabled = editable) {
        Row(Modifier.listCard()) {
            Row(
                Modifier.fillMaxWidth().then(
                    if (editable) Modifier.clickable { if (!swipeController.consumeTapIfOpen()) onEdit() } else Modifier,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(dia(e.date), color = AppTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(96.dp))
                Text(partes.joinToString("  ·  "), color = AppTheme.colors.textDim, fontSize = 13.sp)
            }
        }
    }

    // El mismo diálogo que borrar una sesión (SessionRow): título, qué se borra, y el botón en rojo.
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = AppTheme.colors.surface,
            titleContentColor = AppTheme.colors.textPrimary,
            textContentColor = AppTheme.colors.textDim,
            title = { Text(t.delete) },
            text = { Text(t.deleteSessionConfirm("${b.editTitle} · ${dia(e.date)}")) },
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

/**
 * Anotar o corregir el pesaje de un día. Todo opcional menos tener algo: la balanza y la
 * cinta no siempre van juntas. La estatura viene del pesaje anterior; se escribe una vez.
 * El diálogo, con los colores de los demás del app (el del perfil, el del coach).
 */
@Composable
fun WeighInDialog(vm: MasterViewModel, t: Strings) {
    val e = vm.weighIn ?: return
    val b = t.more.body
    val accent = AppTheme.colors.accent
    var peso by remember(e.date) { mutableStateOf(e.weightKg?.let(::fmtNum) ?: "") }
    var cintura by remember(e.date) { mutableStateOf(e.waistCm?.let(::fmtNum) ?: "") }
    var musculo by remember(e.date) { mutableStateOf(e.skeletalKg?.let(::fmtNum) ?: "") }
    var alto by remember(e.date) { mutableStateOf(e.heightCm?.let(::fmtNum) ?: "") }

    fun num(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val algo = num(peso) != null || num(cintura) != null || num(musculo) != null

    // Contra el pesaje anterior de quien se está anotando (TD-207): "apenas registro ya sé si
    // subí o bajé". historyBody es el del chip elegido, el propio o el del atleta. Solo la
    // flecha, dentro del campo y en cuanto hay un número: con una línea debajo de cada campo
    // el diálogo quedaba "muy cargado".
    val serie = vm.historyBody
    fun delta(value: (BodyEntry) -> Double?, escrito: String): Double? {
        val ahora = num(escrito) ?: return null
        val (_, antes) = BodyLog.previous(serie, e.date, value) ?: return null
        return BodyLog.delta(ahora, antes)
    }

    AlertDialog(
        onDismissRequest = { vm.closeWeighIn() },
        containerColor = AppTheme.colors.surface,
        titleContentColor = AppTheme.colors.textPrimary,
        // Con el nombre del atleta si es por el o ella (TD-205): que no se anote en el propio sin querer.
        title = { Text(listOfNotNull(b.editTitle, vm.weighInFor?.name, dia(e.date)).joinToString(" · ")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("${b.weight} (${t.kg})", peso, delta({ it.weightKg }, peso)) { peso = it }
                NumberField("${b.waist} (cm)", cintura, delta({ it.waistCm }, cintura)) { cintura = it }
                NumberField("${b.skeletal} (${t.kg}) · ${b.optional}", musculo, delta({ it.skeletalKg }, musculo)) { musculo = it }
                // La estatura no: ya viene copiada del último.
                NumberField("${b.height} (cm)", alto) { alto = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = algo,
                onClick = { vm.saveWeighIn(BodyEntry(e.date, num(peso), num(cintura), num(musculo), num(alto))) },
            ) { Text(b.save, color = if (algo) accent else AppTheme.colors.textFaded, fontWeight = FontWeight.Bold) }
        },
        // Sin "Delete" aquí: borrar es deslizar la fila, con su confirmación, como en todo el app.
        dismissButton = {
            TextButton(onClick = { vm.closeWeighIn() }) { Text(b.cancel, color = AppTheme.colors.textDim) }
        },
    )
}

/**
 * Un número con decimales: el campo del app con el teclado numérico. [delta] va al final, dentro
 * del campo y en gris, como en las tarjetas: "↑0.3" contra el pesaje anterior (TD-207).
 */
@Composable
private fun NumberField(label: String, value: String, delta: Double? = null, onChange: (String) -> Unit) {
    AppTextField(
        value = value,
        onValueChange = { s -> onChange(s.filter { it.isDigit() || it == '.' || it == ',' }.take(6)) },
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        trailingIcon = delta?.let { { DeltaLabel(it, Modifier.padding(end = 12.dp)) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Sesiones o pesajes, encima de los nombres (TD-206), con el selector segmentado del app. Cada opción
 * lleva cuántos hay del elegido abajo ("Sessions · 37"): antes el conteo era una fila que se
 * interponía entre la pestaña y la lista (3-oct).
 */
@Composable
fun HistoryTabs(vm: MasterViewModel, accent: Color, t: Strings) {
    val b = t.more.body
    SegmentToggle(
        options = listOf(
            MasterViewModel.HistoryTab.SESSIONS.name to "${b.tabSessions} · ${vm.historySessions.size}",
            MasterViewModel.HistoryTab.BODY.name to "${b.tabBody} · ${vm.historyBody.size}",
        ),
        selected = vm.historyTab.name,
        accent = accent,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) { vm.historyTab = MasterViewModel.HistoryTab.valueOf(it) }
}
