package com.maurozegarra.master.water

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.maurozegarra.master.i18n.I18n
import com.maurozegarra.master.i18n.glassesWord
import com.maurozegarra.master.ui.AppOutlineButton
import com.maurozegarra.master.ui.SwipeAction
import com.maurozegarra.master.ui.SwipeActionsRow
import com.maurozegarra.master.ui.SwipeRowsController
import com.maurozegarra.master.ui.master.StatTile
import com.maurozegarra.master.ui.master.listCard
import com.maurozegarra.master.ui.rememberSwipeRowsController
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.MasterTheme
import com.maurozegarra.master.ui.theme.STATUS_DONE
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * La pantalla del agua (TD-190). Se entra por la gota de la barra de MASTER, como la alarma
 * por el despertador, y con la misma estructura que Morning: su barra con el + a la derecha.
 */
class WaterHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pantalla despierta mientras esta abierta, como MASTER y Morning: cada pantalla es una
        // ventana aparte y la opcion va en cada una (pedido suyo, 4-oct).
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MasterTheme(darkTheme = true) {
                WaterHome(onBack = { finish() })
            }
        }
    }

    companion object {
        fun open(context: Context) {
            // Sin NEW_TASK desde una actividad: queda encima de MASTER y el atras vuelve ahi.
            val i = Intent(context, WaterHomeActivity::class.java)
            if (context !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }
}

private val HORA = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val SOLO_HORA = DateTimeFormatter.ofPattern("h:mm", Locale.ENGLISH)
private val AM_PM = DateTimeFormatter.ofPattern("a", Locale.ENGLISH)

@Composable
private fun WaterHome(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val t = I18n.EN
    val w = t.more.water
    val accent = AppTheme.colors.accent
    val zone = remember { ZoneId.systemDefault() }
    // Se relee al volver (de la notificacion, de anotar desde ella) y despues de cada cambio.
    var vuelta by remember { mutableIntStateOf(0) }
    val ciclo = LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vuelta++ }
        ciclo.lifecycle.addObserver(obs)
        onDispose { ciclo.lifecycle.removeObserver(obs) }
    }
    val hoy = remember(vuelta) { WaterAlarm.today(ctx) }
    val proximo = remember(vuelta) { WaterAlarm.nextAt(ctx) }
    val tomado = WaterPlan.total(hoy.logs)
    val atras = remember(vuelta) { WaterPlan.behind(System.currentTimeMillis(), hoy.logs, hoy.start, hoy.slots) }

    var dialogo by remember { mutableStateOf<Dialogo?>(null) }

    fun anotar(ml: Int) {
        WaterAlarm.add(ctx, ml)
        vuelta++
    }

    com.maurozegarra.master.SettingsScaffold(
        title = w.title,
        subtitle = w.subtitle.format(tomado, WaterPlan.GOAL_ML),
        onBack = onBack,
        actions = {
            // El + anota siempre un vaso de 200: nunca repite "lo ultimo", que es lo que le
            // molestaba de su app (despues de 600 sugeria 600).
            IconButton(onClick = { anotar(WaterPlan.GLASS_ML) }) {
                Icon(Icons.Filled.Add, contentDescription = w.add, tint = accent)
            }
        },
    ) {
        val listState = rememberLazyListState()
        val swipe = rememberSwipeRowsController()
        LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) swipe.closeAll() }
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "tiles") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        w.today, "%,d".format(tomado), Modifier.weight(1f), unit = "ml",
                        caption = "${tomado * 100 / WaterPlan.GOAL_ML}%",
                        valueColor = if (tomado >= WaterPlan.GOAL_ML) STATUS_DONE else AppTheme.colors.textPrimary,
                    )
                    val (ritmo, nota) = when {
                        tomado >= WaterPlan.GOAL_ML -> "✓" to w.goalMet
                        atras == null -> "–" to " "
                        // En vasos y no en ml (4-oct): "1668 ml" cambiaba cada minuto y no decia
                        // nada; "8 glasses" es lo que hay que tomar para ponerse al dia.
                        atras >= WaterPlan.GLASS_ML -> WaterPlan.glasses(atras).let { "$it" to "${w.glassesWord(it)} ${w.behind}" }
                        atras <= -WaterPlan.GLASS_ML -> WaterPlan.glasses(-atras).let { "$it" to "${w.glassesWord(it)} ${w.ahead}" }
                        else -> "✓" to w.onTrack
                    }
                    StatTile(w.pace, ritmo, Modifier.weight(1f), caption = nota)
                    // "5:23" con "PM" chico al lado, como "0 ml": entero no cabia en un tercio
                    // del ancho y la tarjeta crecia mas que las otras (4-oct).
                    StatTile(
                        w.next, proximo?.format(SOLO_HORA) ?: "–", Modifier.weight(1f),
                        unit = proximo?.format(AM_PM),
                        caption = if (proximo == null) w.none else " ",
                    )
                }
            }
            item(key = "presets") {
                // Las dos medidas que usa -el vaso y la botella de los dias presenciales-, y
                // "Other" para otra bebida, otra cantidad u otra hora (lo que no anoto a tiempo).
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Solo la cantidad: con tres botones "Glass 200" no cabia en una linea y el
                    // boton de 40 dp cortaba el numero (4-oct).
                    AppOutlineButton("${WaterPlan.GLASS_ML} ml", accent, Modifier.weight(1f)) { anotar(WaterPlan.GLASS_ML) }
                    AppOutlineButton("${WaterPlan.BOTTLE_ML} ml", accent, Modifier.weight(1f)) { anotar(WaterPlan.BOTTLE_ML) }
                    AppOutlineButton(w.other, accent, Modifier.weight(1f)) { dialogo = Dialogo(null) }
                }
            }
            if (hoy.start == null) {
                item(key = "hint") { Text(w.notStarted, color = AppTheme.colors.textDim, fontSize = 13.sp) }
            }
            if (hoy.logs.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(w.empty, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(w.emptyHint, color = AppTheme.colors.textDim, fontSize = 13.sp)
                    }
                }
            }
            items(hoy.logs.reversed(), key = { "log-${it.at}" }) { l ->
                LogRow(l, zone, swipe, onEdit = { dialogo = Dialogo(l) }) {
                    WaterAlarm.remove(ctx, l.at)
                    vuelta++
                }
            }
        }
    }
    dialogo?.let { d ->
        DrinkDialog(d.editing, zone, onDismiss = { dialogo = null }) { nueva ->
            WaterAlarm.put(ctx, nueva, replacing = d.editing?.at)
            dialogo = null
            vuelta++
        }
    }
}

/** El dialogo abierto: [editing] null es una toma nueva. */
private data class Dialogo(val editing: WaterLog?)

/**
 * Una toma del día. Borrar es deslizar a la izquierda con confirmación, como una sesión o un
 * pesaje: nunca un toque suelto.
 */
@Composable
private fun LogRow(l: WaterLog, zone: ZoneId, swipe: SwipeRowsController, onEdit: () -> Unit, onDelete: () -> Unit) {
    val t = I18n.EN
    val w = t.more.water
    var confirmar by remember { mutableStateOf(false) }
    val hora = Instant.ofEpochMilli(l.at).atZone(zone).format(HORA)
    SwipeActionsRow(actions = listOf(SwipeAction(Icons.Outlined.Delete, t.delete) { confirmar = true }), controller = swipe) {
        Row(Modifier.listCard()) {
            // Tocarla la corrige (hora, bebida, cantidad); el icono dice de un vistazo que fue.
            Row(
                Modifier.fillMaxWidth().clickable { if (!swipe.consumeTapIfOpen()) onEdit() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DrinkGlyph(l.type)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${drinkName(l.type, w)} · ${l.ml} ml", color = AppTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(hora, color = AppTheme.colors.textDim, fontSize = 13.sp)
                }
            }
        }
    }
    // El mismo dialogo que borrar una sesion (SessionRow).
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            containerColor = AppTheme.colors.surface,
            titleContentColor = AppTheme.colors.textPrimary,
            textContentColor = AppTheme.colors.textDim,
            title = { Text(t.delete) },
            text = { Text(t.deleteSessionConfirm(w.deleteWhat.format(l.ml, hora))) },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmar = false }) {
                    Text(t.delete, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmar = false }) { Text(t.cancel, color = AppTheme.colors.textDim) }
            },
        )
    }
}
