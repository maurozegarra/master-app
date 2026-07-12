package com.athletic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athletic.SettingsViewModel
import com.athletic.i18n.Strings
import com.athletic.model.ACCENT_COLORS
import com.athletic.model.THEME_AUTO
import com.athletic.model.THEME_DARK
import com.athletic.model.THEME_LIGHT
import com.athletic.ui.SwitchRow
import com.athletic.ui.theme.AppTheme
import com.athletic.ui.theme.Dims

/** Pantalla de Ajustes: general y player. */
@Composable
fun SettingsScreen(vm: SettingsViewModel, t: Strings) {
    val cfg = vm.config
    val accent = AppTheme.colors.accent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsCard(t.groupGeneral) {
            Text(t.color, color = AppTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            AccentPicker(cfg.general.accent) { vm.setAccent(it) }
            Spacer(Modifier.height(16.dp))
            Text(t.theme, color = AppTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            SegmentedRow(
                options = listOf(THEME_AUTO to t.themeAuto, THEME_LIGHT to t.themeLight, THEME_DARK to t.themeDark),
                selected = cfg.general.themeMode,
                accent = accent,
                onSelect = { vm.setThemeMode(it) },
            )
        }

        SettingsCard(t.groupPlayer) {
            SwitchRow(
                label = t.padPlayerClock,
                desc = t.padPlayerClockDesc,
                checked = cfg.athlete.padPlayerClock,
                accent = accent,
                onCheckedChange = { vm.setPadPlayerClock(it) },
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.card))
            .background(AppTheme.colors.surface)
            .padding(16.dp),
    ) {
        Text(title, color = AppTheme.colors.textDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(selected: Long, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ACCENT_COLORS.forEach { argb ->
            val c = Color(argb)
            val isSel = argb == selected
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(if (isSel) 3.dp else 0.dp, AppTheme.colors.textPrimary, CircleShape)
                    .clickable { onSelect(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSel) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SegmentedRow(
    options: List<Pair<Int, String>>,
    selected: Int,
    accent: Color,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.button))
            .background(AppTheme.colors.track),
    ) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(Dims.button))
                    .background(if (isSel) accent else Color.Transparent)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (isSel) AppTheme.colors.onAccent else AppTheme.colors.textDim,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
