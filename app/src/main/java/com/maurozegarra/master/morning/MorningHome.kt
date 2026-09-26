package com.maurozegarra.master.morning

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.maurozegarra.master.i18n.I18n
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.MasterTheme
import com.maurozegarra.master.ui.theme.FEEL_DOWN
import com.maurozegarra.master.ui.theme.FEEL_STEADY
import com.maurozegarra.master.ui.theme.FEEL_UP
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/**
 * La casa de la alarma (TD-158): su propia entrada en el lanzador, "Morning", con su ícono.
 *
 * Nació en Settings para probarla; funcionando, el usuario pidió "hacerla un poquito más
 * independiente". Es otra ACTIVIDAD con su propia tarea -otra tarjeta en recientes, como un
 * app aparte- pero el mismo paquete: si un día se separa de verdad, la puerta ya existe.
 *
 * Arriba lo de hoy, en el centro la serie -el dato que se quiere mover, que hasta ahora solo
 * leía el coach en el respaldo- y abajo el horario.
 */
class MorningHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pantalla despierta mientras esta abierta, como MASTER: se mira recien levantado y
        // con el telefono en la mano quieta, y se apagaba a mitad de lectura (25-sep).
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MasterTheme(darkTheme = true) {
                MorningHome()
            }
        }
    }

    companion object {
        fun open(context: Context) {
            context.startActivity(Intent(context, MorningHomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** El color de los minutos: un azul tranquilo, lejos de los tres del dolor. */
private val FADE_BAR = Color(0xFF5B9BD5)

/** Cuántos días enseña la serie: cuatro semanas, lo justo para ver una tendencia. */
private const val DIAS = 28

@Composable
private fun MorningHome() {
    val ctx = LocalContext.current
    val t = I18n.EN
    val zone = remember { ZoneId.systemDefault() }
    // Al volver -de contestar, de los ajustes de Android- se relee todo.
    var vuelta by remember { mutableIntStateOf(0) }
    val ciclo = LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vuelta++ }
        ciclo.lifecycle.addObserver(obs)
        onDispose { ciclo.lifecycle.removeObserver(obs) }
    }
    val store = remember { MorningStore(ctx) }
    val mananas = remember(vuelta) { MorningLog.mornings(store.entries(), zone) }
    val hoy = LocalDate.now(zone)
    val deHoy = mananas.firstOrNull { it.date == hoy.toString() }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(t.morning.home, color = AppTheme.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        // El horario primero, pedido por el usuario el 25-sep: es lo que se viene a tocar.
        Card {
            Text(t.morning.schedule, color = AppTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            MorningSettings(t, AppTheme.colors.accent)
        }

        Card {
            Hoy(deHoy, zone, t, onEdit = { n ->
                MorningAlarm.edit(ctx, hoy, n)
                vuelta++
            }, onAnswer = {
                ctx.startActivity(Intent(ctx, MorningActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }, onEased = {
                MorningAlarm.eased(ctx)
                vuelta++
            })
        }

        Card {
            Text(t.morning.lastDays.format(DIAS), color = AppTheme.colors.textDim, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            if (mananas.isEmpty()) {
                Text(t.morning.noData, color = AppTheme.colors.textDim, fontSize = 14.sp)
            } else {
                val dias = (DIAS - 1 downTo 0).map { hoy.minusDays(it.toLong()) }
                val porDia = mananas.associateBy { LocalDate.parse(it.date) }
                // Dos graficos y no uno con dos ejes: el dolor va de 0 a 10 y los minutos a
                // otra escala, y dos escalas en un eje se leen mal siempre.
                BarChart(
                    title = t.morning.painChart,
                    dias = dias,
                    valores = dias.map { porDia[it]?.painOnWaking },
                    maximo = 10,
                    unidad = "",
                    color = { v -> painColor(v) },
                )
                Spacer(Modifier.height(20.dp))
                val minutos = dias.map { porDia[it]?.fadeMinutes }
                val tope = ((minutos.filterNotNull().maxOrNull() ?: 30).coerceAtLeast(30) + 14) / 15 * 15
                BarChart(
                    title = t.morning.fadeChart,
                    dias = dias,
                    valores = minutos,
                    maximo = tope,
                    unidad = " min",
                    // Neutro a proposito: el rojo aqui significa "dolor alto", y los minutos
                    // no son dolor.
                    color = { FADE_BAR },
                )
            }
        }

    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.surface)
            .padding(16.dp),
    ) { content() }
}

/** Lo de hoy: contestado o no, y si ya aflojó; con el botón que toca en cada caso. */
@Composable
private fun Hoy(entry: MorningEntry?, zone: ZoneId, t: Strings, onEdit: (Int) -> Unit, onAnswer: () -> Unit, onEased: () -> Unit) {
    Text(t.morning.today, color = AppTheme.colors.textDim, fontSize = 13.sp)
    Spacer(Modifier.height(10.dp))
    val pain = entry?.painOnWaking
    if (pain == null) {
        Text(t.morning.notAnswered, color = AppTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Boton(t.morning.answerNow, onAnswer)
        return
    }
    // Tocar el número lo corrige (TD-164): la escala se abre en el sitio.
    var corrigiendo by remember(entry.date) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(painColor(pain)).clickable { corrigiendo = !corrigiendo },
            contentAlignment = Alignment.Center,
        ) {
            Text("$pain", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(t.painScale.getOrElse(pain) { "" }, color = AppTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            entry.answeredAt?.let {
                val hora = Instant.ofEpochMilli(it).atZone(zone).toLocalTime().format(DateTimeFormatter.ofPattern("H:mm"))
                Text(t.morning.answeredAt.format(hora), color = AppTheme.colors.textDim, fontSize = 13.sp)
            }
        }
    }
    if (corrigiendo) {
        Spacer(Modifier.height(12.dp))
        Text(t.morning.change, color = AppTheme.colors.textDim, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        (0..10).chunked(6).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                fila.forEach { n ->
                    Box(
                        Modifier
                            .weight(1f)
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (n == pain) painColor(n) else AppTheme.colors.track)
                            .clickable {
                                onEdit(n)
                                corrigiendo = false
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$n", color = if (n == pain) Color.White else AppTheme.colors.textDim, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                repeat(6 - fila.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
    val minutos = entry.fadeMinutes
    if (minutos != null) {
        Text(t.morning.easedAfter.format(minutos), color = AppTheme.colors.textPrimary, fontSize = 15.sp)
    } else {
        // El mismo "It eased" que la notificación, sin tener que buscarla.
        Text(t.morning.notEased, color = AppTheme.colors.textDim, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Boton(t.morning.easedNow, onEased)
    }
}

@Composable
private fun Boton(texto: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.colors.accent)
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = AppTheme.colors.onAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * El color del dolor: verde hasta 1, ámbar hasta 3, rojo desde 4. Los mismos del feedback
 * del peso, que ya se leen como bien / cuidado / mal. Nunca va solo: siempre con el número.
 */
fun painColor(pain: Int): Color = when {
    pain <= 1 -> FEEL_UP
    pain <= 3 -> FEEL_STEADY
    else -> FEEL_DOWN
}

/**
 * Barras por día, una serie, un eje. Sin dato no hay barra; un cero es un palito, para que
 * "no contestó" y "0" no se confundan. Tocar una barra dice el día y el valor arriba.
 */
@Composable
private fun BarChart(
    title: String,
    dias: List<LocalDate>,
    valores: List<Int?>,
    maximo: Int,
    unidad: String,
    color: @Composable (Int) -> Color,
) {
    val t = I18n.EN
    var elegido by remember(valores) { mutableStateOf(valores.indexOfLast { it != null }) }
    val colores = valores.map { v -> v?.let { color(it) } }
    val grilla = AppTheme.colors.track
    val sel = valores.getOrNull(elegido)
    val etiqueta = if (elegido >= 0 && sel != null) {
        val d = dias[elegido]
        "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, t.locale)} ${d.dayOfMonth} · $sel$unidad"
    } else {
        ""
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = AppTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(etiqueta, color = AppTheme.colors.textDim, fontSize = 13.sp)
    }
    Spacer(Modifier.height(8.dp))
    Row {
        Column(Modifier.height(120.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("$maximo", color = AppTheme.colors.textDim, fontSize = 11.sp)
            Text("0", color = AppTheme.colors.textDim, fontSize = 11.sp)
        }
        Spacer(Modifier.width(6.dp))
        Canvas(
            Modifier
                .weight(1f)
                .height(120.dp)
                .pointerInput(valores) {
                    detectTapGestures { p ->
                        val ancho = size.width / valores.size
                        val i = (p.x / ancho).toInt().coerceIn(0, valores.lastIndex)
                        if (valores[i] != null) elegido = i
                    }
                },
        ) {
            val alto = size.height
            // La grilla, recesiva: el cero, la mitad y el tope.
            listOf(0f, 0.5f, 1f).forEach { f ->
                val y = alto - alto * f
                drawLine(grilla, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            val paso = size.width / valores.size
            val hueco = 2.dp.toPx()
            val radio = 4.dp.toPx()
            valores.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val c = colores[i] ?: return@forEachIndexed
                val h = if (v == 0) 2.dp.toPx() else (alto * v / maximo).coerceAtMost(alto)
                val x = i * paso + hueco / 2
                val w = paso - hueco
                val alfa = if (i == elegido) 1f else 0.75f
                // Redondeada arriba y apoyada en la base: se dibuja mas larga y se recorta en
                // la base, asi la esquina de abajo queda recta sin pintar dos veces encima.
                clipRect(bottom = alto) {
                    drawRoundRect(c.copy(alpha = alfa), Offset(x, alto - h), Size(w, h + radio), CornerRadius(radio, radio))
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth().padding(start = 18.dp)) {
        Text("${dias.first().dayOfMonth}/${dias.first().monthValue}", color = AppTheme.colors.textDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text("${dias.last().dayOfMonth}/${dias.last().monthValue}", color = AppTheme.colors.textDim, fontSize = 11.sp)
    }
}

/** La hora de la próxima alarma para la barra de MASTER, o null si está apagada. */
@Composable
fun rememberNextRing(): String? {
    val ctx = LocalContext.current
    var vuelta by remember { mutableIntStateOf(0) }
    val ciclo = LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vuelta++ }
        ciclo.lifecycle.addObserver(obs)
        onDispose { ciclo.lifecycle.removeObserver(obs) }
    }
    return remember(vuelta) { MorningAlarm.nextRing(ctx)?.toLocalTime()?.format(DateTimeFormatter.ofPattern("H:mm")) }
}
