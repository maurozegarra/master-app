package com.maurozegarra.master.timer

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.maurozegarra.master.R
import com.maurozegarra.master.i18n.I18n

/**
 * El timer que suena como alarma (TD-195, la alarma desechable del 9-oct). Es el mismo
 * patrón que la alarma de la mañana ([com.maurozegarra.master.morning.MorningRingService]):
 * un servicio en primer plano, para que Android no lo calle a los diez segundos, en el volumen
 * de ALARMA y en bucle, con la pantalla completa sobre el bloqueo. Sin preguntar el dolor:
 * solo Dismiss y Snooze. Si nadie lo toca en [RING_MS], se calla solo.
 */
class TimerRingService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val rendirse = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TimerAlarm.ensureChannels(this)
        val n = notification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(TimerAlarm.NOTIF_RING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(TimerAlarm.NOTIF_RING, n)
        }
        if (player == null) ring()
        handler.removeCallbacks(rendirse)
        handler.postDelayed(rendirse, RING_MS)
        return START_NOT_STICKY
    }

    private fun ring() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@TimerRingService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
    }

    private fun notification(): Notification {
        val abrir = PendingIntent.getActivity(
            this, 4302, Intent(this, TimerRingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val w = I18n.EN.more.timer
        return Notification.Builder(this, TimerAlarm.CHANNEL_RING)
            .setSmallIcon(R.drawable.ic_notif_timer)
            .setContentTitle(ringingName(this).ifBlank { w.timer })
            .setContentText(w.done)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(abrir)
            .setFullScreenIntent(abrir, true)
            // Igual que la de la mañana: sin esto Android retrasa 10 s la pantalla completa.
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE) }
            .build()
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(android.app.NotificationManager::class.java).cancel(TimerAlarm.NOTIF_RING)
        handler.removeCallbacks(rendirse)
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
        sendBroadcast(Intent(ACTION_STOPPED).setPackage(packageName))
        super.onDestroy()
    }

    companion object {
        private const val RING_MS = 10 * 60_000L
        private const val PREFS = "timers"
        private const val KEY_RINGING = "ringing_name"
        /** Para que la pantalla de alarma se cierre si el servicio se calla solo. */
        const val ACTION_STOPPED = "com.maurozegarra.master.timer.RING_STOPPED"

        fun start(context: Context, name: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_RINGING, name).apply()
            val i = Intent(context, TimerRingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerRingService::class.java))
            context.getSystemService(android.app.NotificationManager::class.java).cancel(TimerAlarm.NOTIF_RING)
        }

        /** El nombre del timer que está sonando, para la pantalla de alarma y el Snooze. */
        fun ringingName(context: Context): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RINGING, "") ?: ""
    }
}
