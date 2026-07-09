package com.athletic.ui.athlete

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athletic.AthleteViewModel
import com.athletic.i18n.Strings
import com.athletic.model.SessionLog
import com.athletic.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Historial de trainings completados. Lee `vm.sessions` (recargado al abrir desde el
 * store, ya que las escribe el servicio del player en otro contexto). Agrupa por día
 * con encabezados relativos (Today/Yesterday) y permite borrar una o todas.
 */
@Composable
fun HistoryScreen(vm: AthleteViewModel, accent: Color, t: Strings) {
    val sessions = vm.sessions

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
    val groups = remember(sessions.toList()) {
        sessions
            .sortedByDescending { it.completedAt }
            .groupBy { Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate() }
            .toList()
            .sortedByDescending { it.first }
    }

    var confirmClear by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
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
                        t = t,
                        onDelete = { vm.deleteSession(s.id) },
                    )
                }
            }
        }

        TextButton(
            onClick = { confirmClear = true },
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        ) {
            Text(t.clearHistory, color = accent, fontWeight = FontWeight.SemiBold)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = AppTheme.colors.surface,
            titleContentColor = AppTheme.colors.textPrimary,
            textContentColor = AppTheme.colors.textDim,
            title = { Text(t.clearHistory) },
            text = { Text(t.clearHistoryConfirm) },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); confirmClear = false }) {
                    Text(t.delete, color = accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(t.cancel, color = AppTheme.colors.textDim)
                }
            },
        )
    }
}

@Composable
private fun SessionRow(
    session: SessionLog,
    time: String,
    t: Strings,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                session.trainingName.ifBlank { t.noName },
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Text(time, color = AppTheme.colors.textDim, fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = AppTheme.colors.textDim)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(t.delete) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** Etiqueta relativa del día: Today/Yesterday o fecha larga localizada. */
private fun dayLabel(date: LocalDate, zone: ZoneId, t: Strings): String {
    val today = LocalDate.now(zone)
    return when (date) {
        today -> t.today
        today.minusDays(1) -> t.yesterday
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(t.locale))
    }
}
