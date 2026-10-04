package com.maurozegarra.master.ui.master

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.MasterViewModel
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.model.BodyEntry
import com.maurozegarra.master.model.BodyLog
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
            if (propio) Text(b.emptyHint, color = AppTheme.colors.textDim, fontSize = 14.sp)
        }
        return
    }
    val alto = BodyLog.heightOf(entries)
    val ultimo = entries.last()
    val conCintura = entries.filter { it.waistCm != null }
    val conPeso = entries.filter { it.weightKg != null }
    val conMusculo = entries.filter { it.skeletalKg != null }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "tiles") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(Modifier.weight(1f), b.weight, conPeso.lastOrNull()?.weightKg, "kg", delta(conPeso.map { it.weightKg!! }), t)
                Tile(Modifier.weight(1f), b.waist, conCintura.lastOrNull()?.waistCm, "cm", delta(conCintura.map { it.waistCm!! }), t)
                val ratio = conCintura.lastOrNull()?.let { BodyLog.waistToHeight(it, alto) }
                RatioTile(Modifier.weight(1f), ratio, t)
            }
        }
        if (conPeso.size >= 2) {
            item(key = "chart-weight") {
                ChartCard(b.weight, "kg", conPeso.map { it.date to it.weightKg!! }, goal = null, accent = accent)
            }
        }
        if (conCintura.size >= 2) {
            item(key = "chart-waist") {
                // La meta de la cintura es la de cintura / estatura llevada a centímetros.
                val meta = alto?.let { Math.round(it * BodyLog.WAIST_HEIGHT_GOAL * 10) / 10.0 }
                ChartCard(b.waist, "cm", conCintura.map { it.date to it.waistCm!! }, goal = meta, accent = accent, goalLabel = meta?.let { "${b.goal} < ${fmt(it)}" })
            }
        }
        if (conMusculo.size >= 2) {
            item(key = "chart-skeletal") {
                ChartCard(b.skeletal, "kg", conMusculo.map { it.date to it.skeletalKg!! }, goal = null, accent = accent)
            }
        }
        item(key = "table-head") {
            Text(
                "${entries.size} · ${b.add}",
                color = AppTheme.colors.textDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        items(entries.reversed(), key = { "row-${it.date}" }) { e ->
            EntryRow(e, alto, propio, t) { vm.editWeighIn(e) }
        }
        item(key = "last") { Spacer(Modifier.height(1.dp).background(Color.Transparent)) }
    }
    // Sin uso de [ultimo] fuera del resumen: queda nombrado por legibilidad del orden de arriba.
    ultimo.date
}

private val DIA = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

private fun dia(date: String): String = runCatching { LocalDate.parse(date).format(DIA) }.getOrDefault(date)

/** 84.6 -> "84.6", 94.0 -> "94". */
private fun fmt(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString()
}

/** El cambio contra el pesaje anterior, o null con uno solo. */
private fun delta(values: List<Double>): Double? =
    if (values.size < 2) null else Math.round((values.last() - values[values.size - 2]) * 10) / 10.0

private fun signed(d: Double): String = when {
    d > 0 -> "+${fmt(d)}"
    d < 0 -> "−${fmt(-d)}"
    else -> "±0"
}

@Composable
private fun Tile(modifier: Modifier, label: String, value: Double?, unit: String, change: Double?, t: Strings) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.colors.surface)
            .padding(12.dp),
    ) {
        Text(label, color = AppTheme.colors.textDim, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value?.let(::fmt) ?: "–", color = AppTheme.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(3.dp))
            Text(unit, color = AppTheme.colors.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        // El cambio va en gris y no en verde o rojo: para uno bajar es bueno y para otra no
        // (NIKO tiene que construir).
        Text(change?.let { "${signed(it)} ${t.more.body.vsPrevious}" } ?: " ", color = AppTheme.colors.textDim, fontSize = 11.sp)
    }
}

@Composable
private fun RatioTile(modifier: Modifier, ratio: Double?, t: Strings) {
    val b = t.more.body
    val bien = ratio != null && ratio < BodyLog.WAIST_HEIGHT_GOAL
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.colors.surface)
            .padding(12.dp),
    ) {
        Text(b.waistHeight, color = AppTheme.colors.textDim, fontSize = 12.sp)
        Text(
            ratio?.let { String.format(Locale.US, "%.2f", it) } ?: "–",
            color = if (bien) STATUS_DONE else AppTheme.colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text("${b.goal} < ${String.format(Locale.US, "%.2f", BodyLog.WAIST_HEIGHT_GOAL)}", color = AppTheme.colors.textDim, fontSize = 11.sp)
    }
}

/**
 * Una curva sábado a sábado: puntos unidos, el último resaltado y, si hay meta, su línea
 * punteada. La escala se ajusta a los valores y a la meta, con un margen, para que medio
 * centímetro se vea como lo que es y no como un precipicio.
 */
@Composable
private fun ChartCard(title: String, unit: String, points: List<Pair<String, Double>>, goal: Double?, accent: Color, goalLabel: String? = null) {
    val dim = AppTheme.colors.textDim
    val faded = AppTheme.colors.textFaded
    val valores = points.map { it.second } + listOfNotNull(goal)
    val lo0 = valores.min()
    val hi0 = valores.max()
    val margen = maxOf((hi0 - lo0) * 0.25, 0.5)
    val lo = lo0 - margen
    val hi = hi0 + margen
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.colors.surface)
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = AppTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (goalLabel != null) Text(goalLabel, color = dim, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.height(120.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(hi0), color = dim, fontSize = 10.sp)
                Text(fmt(lo0), color = dim, fontSize = 10.sp)
            }
            Spacer(Modifier.width(6.dp))
            Canvas(Modifier.weight(1f).height(120.dp)) {
                fun x(i: Int) = if (points.size == 1) size.width / 2 else size.width * i / (points.size - 1)
                fun y(v: Double) = (size.height * (1 - (v - lo) / (hi - lo))).toFloat()
                // Las líneas de referencia del valor más alto y más bajo, tenues.
                listOf(hi0, lo0).forEach { v ->
                    drawLine(faded.copy(alpha = 0.35f), Offset(0f, y(v)), Offset(size.width, y(v)), strokeWidth = 1f)
                }
                if (goal != null) {
                    drawLine(
                        STATUS_DONE,
                        Offset(0f, y(goal)),
                        Offset(size.width, y(goal)),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    )
                }
                val path = Path()
                points.forEachIndexed { i, (_, v) ->
                    val p = Offset(x(i), y(v))
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, accent, style = Stroke(width = 4f))
                points.forEachIndexed { i, (_, v) ->
                    val ultimo = i == points.lastIndex
                    drawCircle(accent, radius = if (ultimo) 9f else 5f, center = Offset(x(i), y(v)))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(30.dp))
            Text(dia(points.first().first), color = dim, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Text("${dia(points.last().first)} · ${fmt(points.last().second)} $unit", color = AppTheme.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EntryRow(e: BodyEntry, alto: Double?, editable: Boolean, t: Strings, onClick: () -> Unit) {
    val b = t.more.body
    val partes = listOfNotNull(
        e.weightKg?.let { "${fmt(it)} kg" },
        e.waistCm?.let { "${fmt(it)} cm" },
        BodyLog.waistToHeight(e, alto)?.let { String.format(Locale.US, "%.2f", it) },
        e.skeletalKg?.let { "${b.skeletal.lowercase()} ${fmt(it)}" },
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.colors.surface)
            .then(if (editable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dia(e.date), color = AppTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(96.dp))
        Text(partes.joinToString("  ·  "), color = AppTheme.colors.textDim, fontSize = 13.sp)
    }
}

/**
 * Anotar o corregir el pesaje de un día. Todo opcional menos tener algo: la balanza y la
 * cinta no siempre van juntas. La estatura viene del pesaje anterior; se escribe una vez.
 */
@Composable
fun WeighInDialog(vm: MasterViewModel, t: Strings) {
    val e = vm.weighIn ?: return
    val b = t.more.body
    val existe = vm.bodyMine.any { it.date == e.date }
    var peso by remember(e.date) { mutableStateOf(e.weightKg?.let(::fmt) ?: "") }
    var cintura by remember(e.date) { mutableStateOf(e.waistCm?.let(::fmt) ?: "") }
    var musculo by remember(e.date) { mutableStateOf(e.skeletalKg?.let(::fmt) ?: "") }
    var alto by remember(e.date) { mutableStateOf(e.heightCm?.let(::fmt) ?: "") }

    fun num(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val algo = num(peso) != null || num(cintura) != null || num(musculo) != null

    AlertDialog(
        onDismissRequest = { vm.closeWeighIn() },
        title = { Text("${b.editTitle} · ${dia(e.date)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(b.weight + " (kg)", peso) { peso = it }
                NumberField(b.waist + " (cm)", cintura) { cintura = it }
                NumberField("${b.skeletal} (kg) · ${b.optional}", musculo) { musculo = it }
                NumberField(b.height + " (cm)", alto) { alto = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = algo,
                onClick = {
                    vm.saveWeighIn(BodyEntry(e.date, num(peso), num(cintura), num(musculo), num(alto)))
                },
            ) { Text(b.save) }
        },
        dismissButton = {
            Row {
                if (existe) TextButton(onClick = { vm.deleteWeighIn(e.date) }) { Text(b.delete) }
                TextButton(onClick = { vm.closeWeighIn() }) { Text(b.cancel) }
            }
        },
    )
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onChange(s.filter { it.isDigit() || it == '.' || it == ',' }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Sesiones o pesajes, debajo de los nombres. Dos pestañas de texto, como las del resto del app. */
@Composable
fun HistoryTabs(vm: MasterViewModel, accent: Color, t: Strings) {
    val b = t.more.body
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        listOf(MasterViewModel.HistoryTab.SESSIONS to b.tabSessions, MasterViewModel.HistoryTab.BODY to b.tabBody).forEach { (tab, label) ->
            val sel = vm.historyTab == tab
            Column(
                Modifier.clickable { vm.historyTab = tab }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, color = if (sel) AppTheme.colors.textPrimary else AppTheme.colors.textDim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(28.dp).height(2.dp).background(if (sel) accent else Color.Transparent))
            }
        }
    }
}
