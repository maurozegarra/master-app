package com.maurozegarra.master.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import kotlinx.coroutines.launch
import com.maurozegarra.master.ui.theme.AppTheme

/**
 * El sheet de todo el app (9-oct), con el minimalismo de los de Telegram, medidos en su sheet
 * de adjuntar:
 * - arriba apenas una rayita de 36 x 4 dp, a 10 dp del borde y 10 dp antes del contenido,
 *   apenas más clara que el fondo; nada del margen de la de Material;
 * - abre a lo más al [HEIGHT] de la pantalla, todo incluido, como el de Telegram (el de
 *   Material abría a la mitad y no deja elegir), y si el contenido es más corto, a su medida.
 *
 * [tall] es para cuando el contenido crece a pedido -el teclado del timer, por ejemplo- y
 * necesita casi toda la pantalla. Todo sheet nuevo va por aquí, no por ModalBottomSheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSheet(
    onDismiss: () -> Unit,
    containerColor: Color = AppTheme.colors.surface,
    tall: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // El alto es el del sheet ENTERO, como se ve: el contenido más la rayita y la barra de
    // navegación, que el sheet suma por dentro. La primera versión (9-oct) aplicaba el 60 % al
    // contenido solo y quedó en 69 %, más alto que el de Telegram.
    val alto = LocalConfiguration.current.screenHeightDp.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val barra = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val maximo = alto * (if (tall) TALL else HEIGHT) - HANDLE - barra
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val cerrar by rememberUpdatedState(onDismiss)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = estado,
        containerColor = containerColor,
        // El "atrás" de Android 13 en adelante lo pone la hoja misma (ver más abajo): el de
        // Material se registra con PRIORITY_OVERLAY y le gana al teclado.
        properties = ModalBottomSheetProperties(
            securePolicy = SecureFlagPolicy.Inherit,
            isFocusable = true,
            shouldDismissOnBackPress = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU,
        ),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppTheme.colors.textFaded.copy(alpha = 0.6f)),
            )
        },
    ) {
        // TD-204 (27-oct): con el teclado abierto, la flechita de abajo del S26 cerraba la hoja
        // entera, también desde el campo Name, que es un campo normal. El "atrás" de la hoja de
        // Material 1.2.1 va con PRIORITY_OVERLAY y le gana al del teclado. Este va con la
        // prioridad normal: el teclado, que se registra al abrirse, lo atiende primero y solo se
        // baja; ya cerrado, el "atrás" llega aquí y cierra la hoja con su animación. Antes de
        // Android 13 el teclado se lleva el "atrás" por su cuenta y queda el de Material.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val view = LocalView.current
            DisposableEffect(view) {
                val dispatcher = view.findOnBackInvokedDispatcher()
                val callback = OnBackInvokedCallback {
                    scope.launch { estado.hide() }.invokeOnCompletion { cerrar() }
                }
                dispatcher?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
                onDispose { dispatcher?.unregisterOnBackInvokedCallback(callback) }
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(max = maximo), content = content)
    }
}

/**
 * Medido en el sheet de adjuntar de Telegram (9-oct). Ese sheet no abre siempre igual: a las
 * 22:20 empezó en y = 943 y a las 22:53 en y = 982 (de 2340 px). Él comparó contra el de 982:
 * 1358 de 2340.
 */
private const val HEIGHT = 0.5803f
private const val TALL = 0.92f
/** La rayita con sus márgenes: 10 + 4 + 10 dp. */
private val HANDLE = 24.dp
