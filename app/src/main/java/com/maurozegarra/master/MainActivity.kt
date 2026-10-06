package com.maurozegarra.master

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.History
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.i18n.I18n
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.model.THEME_DARK
import com.maurozegarra.master.model.THEME_LIGHT
import com.maurozegarra.master.ui.MasterWordmark
import com.maurozegarra.master.update.UpdateChecker
import com.maurozegarra.master.update.UpdateBar
import com.maurozegarra.master.update.UpdateInfo
import com.maurozegarra.master.ui.master.MasterScreen
import com.maurozegarra.master.ui.settings.PeopleScreen
import com.maurozegarra.master.ui.settings.SettingsScreen
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.MasterTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Punto de entrada del app y shell de navegación de MASTER. La sección principal se
 * navega por estado del [MasterViewModel] (no hay NavHost): [MasterScreen] enruta a
 * la pantalla correcta y aquí se resuelve el "back" cerrando el nivel más profundo.
 */
class MainActivity : ComponentActivity() {

    private val pendingWorkoutId = mutableStateOf<Long?>(null)

    /** El mismo del árbol de Compose: los dos salen del ViewModelStore de esta Activity. */
    private val master: MasterViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        pendingWorkoutId.value = intent?.getLongExtra("workoutId", -1L)?.takeIf { it >= 0 }
        openWeighInIfAsked(intent)
        setContent {
            val settingsVm: SettingsViewModel = koinViewModel()
            val cfg = settingsVm.config
            val dark = when (cfg.general.themeMode) {
                THEME_LIGHT -> false
                THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            MasterTheme(accent = cfg.general.accent, darkTheme = dark) {
                Surface(color = AppTheme.colors.bg) {
                    MasterApp(settingsVm, pendingWorkoutId)
                }
            }
        }
    }

    /**
     * Lo asignado se comprueba también al volver a primer plano, no solo al arrancar en
     * frío: un teléfono que nunca se cierra del todo podía tardar días en enterarse de una
     * asignación nueva. Si de verdad toca ir a la red lo decide el ViewModel; aquí solo se
     * le avisa de que el usuario ha vuelto.
     */
    override fun onStart() {
        super.onStart()
        master.syncAssignments()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingWorkoutId.value = intent.getLongExtra("workoutId", -1L).takeIf { it >= 0 }
        openWeighInIfAsked(intent)
    }

    /**
     * La notificacion del sabado (TD-169) abre el historial en Body, con el pesaje de hoy.
     * Se borra el extra al usarlo: si no, girar la pantalla lo volveria a abrir.
     */
    private fun openWeighInIfAsked(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_WEIGH_IN, false) != true) return
        intent.removeExtra(EXTRA_WEIGH_IN)
        master.openWeighInFromNotification()
    }

    companion object {
        const val EXTRA_WEIGH_IN = "weigh_in"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MasterApp(settingsVm: SettingsViewModel, pendingWorkoutId: androidx.compose.runtime.State<Long?>) {
    val vm: MasterViewModel = koinViewModel()
    val t = I18n.EN
    val accent = AppTheme.colors.accent

    var showSettings by remember { mutableStateOf(false) }
    var showPeople by remember { mutableStateOf(false) }
    var showClearMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current

    // Check de actualizaciones al iniciar (fuera de Play Store).
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isForceUpdate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val info = UpdateChecker.checkForUpdate(context)
        if (info != null) {
            updateInfo = info
            isForceUpdate = UpdateChecker.isForceUpdate(info, context)
        }
    }

    // Deep link desde notificación: abrir el player del training activo.
    LaunchedEffect(pendingWorkoutId.value) {
        val id = pendingWorkoutId.value ?: return@LaunchedEffect
        vm.openPlayer(id)
    }

    // Permisos de notificación (Android 13+) para el foreground service del player.
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> vm.startPlayerRun() }

    fun startTraining() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.startPlayerRun()
        }
    }

    // Ajustes: pantalla propia por encima de la sección principal, y dentro de ella la de
    // personas, que es un nivel más. El atrás cierra de dentro hacia fuera.
    if (showSettings) {
        if (showPeople) {
            BackHandler { showPeople = false }
            SettingsScaffold(title = t.people, onBack = { showPeople = false }) {
                PeopleScreen(vm, t)
            }
        } else {
            BackHandler { showSettings = false }
            SettingsScaffold(title = t.settings, onBack = { showSettings = false }) {
                SettingsScreen(settingsVm, vm, t, onPeople = { showPeople = true })
            }
        }
        return
    }

    val inPlayer = vm.playerTrainingId != null
    val canGoBack = inPlayer ||
        vm.showingHistory ||
        vm.exerciseHistoryId != null ||
        vm.choosingExercise ||
        vm.choosingWorkout ||
        vm.editingExerciseId != null ||
        vm.editingVariantId != null ||
        vm.editingWorkoutId != null ||
        vm.draft != null

    BackHandler(enabled = canGoBack) { goBack(vm) }

    // El player ocupa toda la pantalla (controles propios); el resto usa Scaffold con
    // barra superior y botón atrás contextual.
    if (inPlayer) {
        MasterScreen(vm, accent, t, ::startTraining)
        return
    }

    Scaffold(
        containerColor = AppTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    // En la raíz: wordmark MASTER; en niveles internos: título contextual.
                    if (canGoBack) {
                        Text(
                            titleFor(vm, t),
                            color = AppTheme.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                    } else {
                        MasterWordmark(accent = accent, height = 28.dp)
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
                        // La alarma (TD-158), del mismo ancho que los demás íconos: la hora entera
                        // ("5:00") ocupaba demasiado en la fila (4-oct). Lo que él mira antes de
                        // dormir es si mañana suena y a qué hora, así que queda la hora sola,
                        // como contador estilo Telegram, en el acento y en JetBrains Mono. Sin
                        // alarma mañana, el reloj tachado: que se note sin leer.
                        val hora = com.maurozegarra.master.morning.rememberNextRingHour()
                        IconButton(onClick = { com.maurozegarra.master.morning.MorningHomeActivity.open(context) }) {
                            Box {
                                Icon(
                                    if (hora != null) Icons.Outlined.Alarm else Icons.Outlined.AlarmOff,
                                    contentDescription = t.morning.home,
                                    tint = if (hora != null) AppTheme.colors.textPrimary else AppTheme.colors.textDim,
                                )
                                if (hora != null) {
                                    // El fondo de la barra detrás de la cifra corta el contorno
                                    // del reloj, como el contador de Telegram sobre el avatar.
                                    Text(
                                        "$hora",
                                        color = accent,
                                        fontSize = 11.sp,
                                        lineHeight = 11.sp,
                                        fontFamily = MonoDigits,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 6.dp, y = (-5).dp)
                                            .background(AppTheme.colors.bg, RoundedCornerShape(50))
                                            .padding(horizontal = 2.dp),
                                    )
                                }
                            }
                        }
                        // El agua (TD-190): como el despertador, abre su propia pantalla.
                        IconButton(onClick = { com.maurozegarra.master.water.WaterHomeActivity.open(context) }) {
                            Icon(
                                Icons.Outlined.WaterDrop,
                                contentDescription = t.more.water.title,
                                tint = AppTheme.colors.textPrimary,
                            )
                        }
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
                    // Borrar todo el historial es solo del propio: el de un atleta no se
                    // toca desde aqui (TD-168).
                    // En Body, un + para anotar el pesaje (TD-169), como el + de Morning
                    // (TD-182): en la barra y no con un boton a todo lo ancho. Solo el propio.
                    if (vm.showingHistory && vm.historyAthlete == null && vm.historyTab == MasterViewModel.HistoryTab.BODY) {
                        IconButton(onClick = { vm.openWeighIn() }) {
                            // En el acento, como el + de Morning: es la accion de la pantalla.
                            Icon(Icons.Filled.Add, contentDescription = t.more.body.add, tint = AppTheme.colors.accent)
                        }
                    } else if (vm.showingHistory && vm.historyAthlete == null) {
                        Box {
                            IconButton(onClick = { showClearMenu = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = null,
                                    tint = AppTheme.colors.textPrimary,
                                )
                            }
                            DropdownMenu(expanded = showClearMenu, onDismissRequest = { showClearMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(t.clearHistory) },
                                    onClick = { showClearMenu = false; showClearDialog = true },
                                )
                            }
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = pad.calculateTopPadding()),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (updateInfo == null) {
                            Modifier.padding(bottom = pad.calculateBottomPadding())
                        } else {
                            Modifier
                        },
                    ),
            ) {
                MasterScreen(vm, accent, t, ::startTraining)
            }
            updateInfo?.let { info ->
                UpdateBar(
                    updateInfo = info,
                    isForced = isForceUpdate,
                    onDismiss = { updateInfo = null },
                    bottomInset = pad.calculateBottomPadding(),
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = AppTheme.colors.surface,
            titleContentColor = AppTheme.colors.textPrimary,
            textContentColor = AppTheme.colors.textDim,
            title = { Text(t.clearHistory) },
            text = { Text(t.clearHistoryConfirm) },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); showClearDialog = false }) {
                    Text(t.delete, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(t.cancel, color = AppTheme.colors.textDim)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// Interna y no privada: la usa tambien la pantalla Morning (TD-175), que es otra actividad.
internal fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    /**
     * Una linea chica debajo del titulo, dentro de la barra, como el "last seen" de un chat de
     * Telegram (4-oct). La barra mide 64 dp fijos: un subtitulo fuera de ella dejaba un hueco.
     */
    subtitle: String? = null,
    /** Lo de la derecha de la barra, como el "+" de Morning (TD-182). */
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = AppTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    if (subtitle == null) {
                        Text(title, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    } else {
                        // Con subtitulo, cada linea con un alto ajustado a su letra y sin el
                        // relleno de la fuente: con el alto de linea por defecto las dos no
                        // cabian centradas en los 64 dp de la barra, el titulo subia hasta la
                        // hora del telefono y quedaba aire entre las dos (4-oct).
                        val ajustado = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                            ),
                        )
                        Column {
                            Text(title, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, style = ajustado, maxLines = 1)
                            Text(subtitle, color = AppTheme.colors.textDim, fontSize = 13.sp, lineHeight = 16.sp, style = ajustado, maxLines = 1)
                        }
                    }
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
                actions = actions,
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
private fun titleFor(vm: MasterViewModel, t: Strings): String = when {
    vm.exerciseHistoryId != null -> t.exerciseHistory
    vm.showingHistory -> t.history
    vm.choosingExercise -> t.chooseExercise
    vm.choosingWorkout -> t.chooseWorkout
    vm.editingExerciseId != null -> vm.editingExercise()?.name ?: t.exercise
    vm.editingVariantId != null || vm.editingWorkoutId != null -> vm.editorName().ifBlank { t.workout }
    vm.draft != null -> vm.draft?.name?.ifBlank { t.training } ?: t.training
    else -> "MASTER"
}

/** Cierra el nivel de navegación más profundo (mismo orden que el router de MasterScreen). */
private fun goBack(vm: MasterViewModel) {
    when {
        vm.playerTrainingId != null -> vm.minimizePlayer()
        vm.exerciseHistoryId != null -> vm.closeExerciseHistory()
        vm.showingHistory -> vm.closeHistory()
        vm.choosingExercise -> vm.closeExercisePicker()
        vm.choosingWorkout -> vm.closeWorkoutPicker()
        vm.editingExerciseId != null -> vm.closeExerciseEditor()
        vm.editingVariantId != null -> vm.closeVariantEditor()
        vm.editingWorkoutId != null -> vm.closeWorkoutEditor()
        vm.draft != null -> vm.closeTrainingEditor()
    }
}

/** JetBrains Mono SemiBold, solo cifras y dos puntos: para horas que se leen de un vistazo. */
private val MonoDigits = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.jetbrains_mono_digits))
