package com.athletic.ui.athlete

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athletic.AthleteViewModel
import com.athletic.i18n.Strings
import com.athletic.model.ExerciseRecord
import com.athletic.model.SessionLog
import com.athletic.model.SessionStatus
import com.athletic.ui.theme.AppTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ExerciseHistoryScreen(vm: AthleteViewModel, accent: Color, t: Strings) {
    val exerciseId = vm.exerciseHistoryId ?: return
    val entries = remember(vm.sessions.toList(), exerciseId) {
        vm.sessionsForExercise(exerciseId)
    }

    if (entries.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(t.noExercises, color = AppTheme.colors.textDim, fontSize = 14.sp)
        }
        return
    }

    val zone = remember { ZoneId.systemDefault() }
    val dateFmt = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(t.locale) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val exerciseName = entries.firstOrNull()?.second?.name ?: exerciseId

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") {
            Text(
                exerciseName,
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                "${entries.size} ${t.sessionsCount}",
                color = AppTheme.colors.textDim,
                fontSize = 13.sp,
            )
        }
        items(entries, key = { it.first.id }) { (session, er) ->
            ExerciseSessionCard(session, er, accent, t, zone, dateFmt, timeFmt)
        }
    }
}

@Composable
private fun ExerciseSessionCard(
    session: SessionLog,
    er: ExerciseRecord,
    accent: Color,
    t: Strings,
    zone: ZoneId,
    dateFmt: DateTimeFormatter,
    timeFmt: DateTimeFormatter,
) {
    val zdt = remember(session.completedAt) { Instant.ofEpochMilli(session.completedAt).atZone(zone) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.surface)
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    zdt.format(dateFmt),
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Text(
                    "${session.trainingName}  ·  ${zdt.format(timeFmt)}",
                    color = AppTheme.colors.textDim,
                    fontSize = 12.sp,
                )
            }
            if (session.status == SessionStatus.PARTIAL) {
                Text(t.partial, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        er.sets.forEachIndexed { i, sr ->
            val setLabel = "Set ${i + 1}"
            val detail = if (er.timeBased) {
                if (sr.weightKg > 0) "$setLabel  ·  ${sr.reps} reps  ·  ${fmtKgEx(sr.weightKg)} ${t.kg}  ·  ${sr.durationSec}s"
                else "$setLabel  ·  ${sr.reps} reps  ·  ${sr.durationSec}s"
            } else {
                if (sr.weightKg > 0) "$setLabel  ·  ${sr.reps} ${t.repLabel}  ·  ${fmtKgEx(sr.weightKg)} ${t.kg}"
                else "$setLabel  ·  ${sr.reps} ${t.repLabel}"
            }
            Text(detail, color = AppTheme.colors.textDim, fontSize = 13.sp)
        }
        if (er.feedbackDeltaKg != null && er.feedbackDeltaKg != 0.0) {
            Spacer(Modifier.height(4.dp))
            val arrow = if (er.feedbackDeltaKg > 0) "\u2191" else "\u2193"
            Text(
                "Feedback: $arrow ${fmtKgEx(kotlin.math.abs(er.feedbackDeltaKg))} ${t.kg}",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun fmtKgEx(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
