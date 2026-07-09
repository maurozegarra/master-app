package com.athletic.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athletic.i18n.Strings
import com.athletic.ui.theme.AppTheme
import com.athletic.util.pad2
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlin.math.abs

// ---------- Selector tipo rueda (Hours/Minutes/Seconds) ----------
private val WHEEL_COL_W = 72.dp
private val WHEEL_ITEM_H = 56.dp

@Composable
internal fun WheelTimePicker(
    h: Int,
    m: Int,
    s: Int,
    accent: Color,
    t: Strings,
    onChange: (Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.Center) {
            WheelLabel(t.hours)
            Spacer(Modifier.width(16.dp))
            WheelLabel(t.minutes)
            Spacer(Modifier.width(16.dp))
            WheelLabel(t.seconds)
        }
        Spacer(Modifier.height(4.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(WHEEL_ITEM_H * 3)) {
            // Banda de selección (cápsula) detrás de los números.
            Box(
                modifier = Modifier
                    .width(WHEEL_COL_W * 3 + 32.dp)
                    .height(WHEEL_ITEM_H)
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
            )
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                WheelColumn(range = 100, value = h) { onChange(it, m, s) }
                WheelColon()
                WheelColumn(range = 60, value = m) { onChange(h, it, s) }
                WheelColon()
                WheelColumn(range = 60, value = s) { onChange(h, m, it) }
            }
        }
    }
}

@Composable
private fun WheelLabel(text: String) {
    Box(modifier = Modifier.width(WHEEL_COL_W), contentAlignment = Alignment.Center) {
        Text(text, color = AppTheme.colors.textDim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WheelColon() {
    Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.Center) {
        Text(":", color = AppTheme.colors.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(range: Int, value: Int, onValue: (Int) -> Unit) {
    val view = LocalView.current
    val blocks = 2000
    val start = range * (blocks / 2) + value
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start - 1)
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val center by remember {
        derivedStateOf {
            val li = listState.layoutInfo
            val visible = li.visibleItemsInfo
            if (visible.isEmpty()) start
            else {
                val vc = (li.viewportStartOffset + li.viewportEndOffset) / 2
                visible.minByOrNull { abs((it.offset + it.size / 2) - vc) }!!.index
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { center }
            .drop(1)
            .distinctUntilChanged()
            .collect { idx ->
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onValue(idx % range)
            }
    }
    LaunchedEffect(value) {
        if (!listState.isScrollInProgress && center % range != value) {
            val target = center - (center % range) + value
            listState.scrollToItem((target - 1).coerceAtLeast(0))
        }
    }
    LazyColumn(
        state = listState,
        flingBehavior = fling,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(WHEEL_COL_W).height(WHEEL_ITEM_H * 3),
    ) {
        items(range * blocks) { index ->
            val selected = index == center
            Box(modifier = Modifier.width(WHEEL_COL_W).height(WHEEL_ITEM_H), contentAlignment = Alignment.Center) {
                Text(
                    pad2(index % range),
                    color = if (selected) AppTheme.colors.textPrimary else AppTheme.colors.textFaded,
                    fontSize = if (selected) 40.sp else 24.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Light,
                )
            }
        }
    }
}
