package com.athletic

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.athletic.i18n.I18n
import com.athletic.i18n.Strings
import com.athletic.model.THEME_DARK
import com.athletic.model.THEME_LIGHT
import com.athletic.ui.TimesWordmark
import com.athletic.ui.athlete.AthleteScreen
import com.athletic.ui.settings.SettingsScreen
import com.athletic.ui.theme.AppTheme
import com.athletic.ui.theme.AthleticTheme

/**
 * Punto de entrada del app y shell de navegación de Athletic. La sección Athlete se
 * navega por estado del [AthleteViewModel] (no hay NavHost): [AthleteScreen] enruta a
 * la pantalla correcta y aquí se resuelve el "back" cerrando el nivel más profundo.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val settingsVm: SettingsViewModel = viewModel()
            val cfg = settingsVm.config
            val dark = when (cfg.general.themeMode) {
                THEME_LIGHT -> false
                THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            AthleticTheme(accent = cfg.general.accent, darkTheme = dark) {
                Surface(color = AppTheme.colors.bg) {
                    AthleticApp(settingsVm)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AthleticApp(settingsVm: SettingsViewModel) {
    val vm: AthleteViewModel = viewModel()
    val t = I18n.EN
    val accent = AppTheme.colors.accent

    var showSettings by remember { mutableStateOf(false) }

    // Ajustes: pantalla propia por encima de la sección Athlete.
    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScaffold(title = t.settings, onBack = { showSettings = false }) {
            SettingsScreen(settingsVm, t)
        }
        return
    }

    val inPlayer = vm.playerTrainingId != null
    val canGoBack = inPlayer ||
        vm.showingHistory ||
        vm.choosingExercise ||
        vm.editingExerciseId != null ||
        vm.editingVariantId != null ||
        vm.editingWorkoutId != null ||
        vm.draft != null

    BackHandler(enabled = canGoBack) { goBack(vm) }

    // El player ocupa toda la pantalla (controles propios); el resto usa Scaffold con
    // barra superior y botón atrás contextual.
    if (inPlayer) {
        AthleteScreen(vm, accent, t)
        return
    }

    Scaffold(
        containerColor = AppTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    // En la raíz: wordmark TIMES; en niveles internos: título contextual.
                    if (canGoBack) {
                        Text(
                            titleFor(vm, t),
                            color = AppTheme.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                    } else {
                        TimesWordmark(accent = accent, height = 22.dp)
                    }
                },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = { goBack(vm) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = AppTheme.colors.textPrimary,
                            )
                        }
                    }
                },
                actions = {
                    // Historial y engranaje solo se muestran en la raíz (lista de trainings).
                    if (!canGoBack) {
                        IconButton(onClick = { vm.openHistory() }) {
                            Icon(
                                Icons.Outlined.History,
                                contentDescription = t.history,
                                tint = AppTheme.colors.textPrimary,
                            )
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                painterResource(R.drawable.ic_settings),
                                contentDescription = t.settings,
                                tint = AppTheme.colors.textPrimary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.colors.bg,
                    titleContentColor = AppTheme.colors.textPrimary,
                ),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            AthleteScreen(vm, accent, t)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = AppTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(title, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AppTheme.colors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.colors.bg,
                    titleContentColor = AppTheme.colors.textPrimary,
                ),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            content()
        }
    }
}

/** Título contextual según el nivel de navegación activo. */
private fun titleFor(vm: AthleteViewModel, t: Strings): String = when {
    vm.showingHistory -> t.history
    vm.choosingExercise -> t.chooseExercise
    vm.editingExerciseId != null -> t.exercise
    vm.editingVariantId != null || vm.editingWorkoutId != null -> t.workout
    vm.draft != null -> t.training
    else -> "TIMES"
}

/** Cierra el nivel de navegación más profundo (mismo orden que el router de AthleteScreen). */
private fun goBack(vm: AthleteViewModel) {
    when {
        vm.playerTrainingId != null -> vm.closePlayer()
        vm.showingHistory -> vm.closeHistory()
        vm.choosingExercise -> vm.closeExercisePicker()
        vm.editingExerciseId != null -> vm.closeExerciseEditor()
        vm.editingVariantId != null -> vm.closeVariantEditor()
        vm.editingWorkoutId != null -> vm.closeWorkoutEditor()
        vm.draft != null -> vm.closeTrainingEditor()
    }
}
