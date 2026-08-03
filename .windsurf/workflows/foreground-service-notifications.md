---
description: Cómo funciona el foreground service del player sin mostrar notificaciones visibles
---

# Foreground Service sin notificaciones visibles

El `WorkoutPlayerService` es un foreground service que **no muestra notificación visible** (ni en status bar, ni en shade, ni en lock screen). Esto es intencional y resulta de tres factores combinados:

## 1. Permiso `POST_NOTIFICATIONS` no concedido (causa principal)

En Android 13+ (API 33+), el permiso `POST_NOTIFICATIONS` es runtime y debe ser concedido explícitamente. La app no lo solicita, por lo que Android **bloquea todas las notificaciones** del servicio.

- `startForeground()` sigue siendo obligatorio y funciona correctamente
- El servicio corre en background, el timer avanza, los ticks funcionan
- Pero la notificación es invisible: `numBlocked` en `dumpsys notification` confirma que todas las actualizaciones están bloqueadas

Verificado con:
```
adb shell dumpsys notification | grep athletic
# Resultado: numEnqueuedByApp=3432, numPostedByApp=0, numBlocked=3432
```

## 2. `foregroundServiceType="specialUse"` (Android 14+ / API 34+)

En `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<service
    android:name=".notify.WorkoutPlayerService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Workout routine player with step countdown" />
</service>
```

`specialUse` es la categoría para servicios que no encajan en los tipos estándar (media, location, camera, etc.). Samsung One UI trata estos servicios de forma más discreta.

En código (`WorkoutPlayerService.kt`):
```kotlin
private fun startForegroundCompat(notification: Notification) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
        startForeground(NOTIF_ID, notification)
    }
}
```

## 3. Canal `IMPORTANCE_LOW` + `setShowBadge(false)`

Si el permiso se concediera en el futuro, la notificación sería silenciosa:

```kotlin
NotificationChannel(
    CHANNEL_ID,
    "Workout",
    NotificationManager.IMPORTANCE_LOW,
).apply {
    setShowBadge(false)
    setSound(null, null)
    enableVibration(false)
}
```

- `IMPORTANCE_LOW`: sin sonido, sin vibración, sin heads-up
- `setShowBadge(false)`: sin ícono en status bar
- En Samsung One UI, canales `LOW` se agrupan como "silenciosos" y se ocultan por defecto en el shade

## 4. Detalles adicionales de la notificación

```kotlin
Notification.Builder(this, CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_stat_timer)
    .setOngoing(true)                    // no se puede descartar manualmente
    .setOnlyAlertOnce(true)              // no alerta repetidamente al actualizar cada segundo
    .setVisibility(Notification.VISIBILITY_PUBLIC)  // visible en lock screen (si permiso concedido)
    .setShowWhen(false)                  // oculta el timestamp
    .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)  // API 31+
```

`FOREGROUND_SERVICE_IMMEDIATE` (Android 12+): la notificación se publica inmediatamente pero respeta la prioridad del canal (`LOW`), en vez de forzar alta visibilidad.

## Notas

- El servicio actualiza la notificación cada segundo (tick del reloj), generando ~3432 actualizaciones bloqueadas en una sesión típica. Esto no afecta el rendimiento.
- Si en el futuro se quiere mostrar notificaciones, solicitar `POST_NOTIFICATIONS` con `ActivityCompat.requestPermissions()` antes de iniciar el training.
- El servicio sobrevive a que la app se quite de recientes porque es foreground. Al reabrir la app, `restorePlayerState()` reconecta la UI.
