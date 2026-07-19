package com.athletic.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athletic.R
import com.athletic.ui.theme.AppTheme

private val WallpoetAthletic = FontFamily(Font(R.font.wallpoet_athletic))

@Composable
fun MasterWordmark(
    accent: Color,
    modifier: Modifier = Modifier,
    height: Dp = 22.dp,
    restColor: Color = AppTheme.colors.textPrimary,
) {
    val fontSize = height.value.sp
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = accent, fontFamily = WallpoetAthletic, fontSize = fontSize)) {
            append("M")
        }
        withStyle(SpanStyle(color = restColor, fontFamily = WallpoetAthletic, fontSize = fontSize)) {
            append("AST")
        }
        withStyle(SpanStyle(color = accent, fontFamily = WallpoetAthletic, fontSize = fontSize)) {
            append("E")
        }
        withStyle(SpanStyle(color = restColor, fontFamily = WallpoetAthletic, fontSize = fontSize)) {
            append("R")
        }
    }
    Text(text = text, modifier = modifier)
}
