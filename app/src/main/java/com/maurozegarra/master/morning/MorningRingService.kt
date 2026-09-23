package com.maurozegarra.master.morning

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
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
import androidx.core.app.NotificationCompat
import com.maurozegarra.master.R

/**
 * El sonido de la alarma (TD-151).
 *
 * Un servicio en primer plano y no un sonido suelto: Android puede matar un proceso en
 * segundo plano en cualquier momento, y una alarma que se calla sola a los diez segundos no
 * despierta a nadie. Suena en el volumen de ALARMA -no en el de notificaciones, que de noche
 * suele estar en silencio- y en bucle, hasta que se conteste, se posponga o se rinda.
 *
 * La pantalla completa sobre el bloqueo la abre la notificación, con su fullScreenIntent.
 */
class MorningRingService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Si nadie la toca en [RING_MS], se pospone sola, como un despertador. Hasta
     * [MAX_AUTO_SNOOZES] veces: después se rinde, para no sonar toda la mañana en un
     * teléfono que se quedó en casa.
     */
    private val rendirse = Runnable {
        val prefs = getSharedPreferences("morning", MODE_PRIVATE)
        val veces = prefs.getInt(KEY_AUTO, 0)
        if (veces < MAX_AUTO_SNOOZES) {
            prefs.edit().putInt(KEY_AUTO, veces + 1).apply()
            MorningAlarm.snooze(this)
        } else {
            prefs.edit().putInt(KEY_AUTO, 0).apply()
            MorningAlarm.dismiss(this, pain = null)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MorningAlarm.ensureChannels(this)
        val n = notification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(MorningAlarm.NOTIF_RING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(MorningAlarm.NOTIF_RING, n)
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
                setDataSource(this@MorningRingService, uri)
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
            this, 44, Intent(this, MorningActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, MorningAlarm.CHANNEL_RING)
            .setSmallIcon(R.drawable.ic_notif_play)
            .setContentTitle("Good morning")
            .setContentText("How is your back, before moving?")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(abrir)
            .setFullScreenIntent(abrir, true)
            // Sin esto Android retrasa 10 s la notificacion de un servicio en primer plano,
            // y con ella la pantalla completa: el 22-sep sonaba a las 21:03:00 y la pantalla
            // se encendia a las 21:03:10. Un despertador no espera.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        // Quitar la notificacion AQUI, con el servicio. Cancelarla desde fuera no sirve
        // mientras el servicio sigue en primer plano -Android la ignora-, y detenerlo es
        // asincrono: el 22-sep, tras contestar el dolor, la de "Good morning" se quedaba.
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(android.app.NotificationManager::class.java).cancel(MorningAlarm.NOTIF_RING)
        handler.removeCallbacks(rendirse)
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val RING_MS = 10 * 60_000L
        private const val MAX_AUTO_SNOOZES = 3
        private const val KEY_AUTO = "auto_snoozes"

        /** Contestar o apagar a mano reinicia la cuenta de las pospuestas solas. */
        fun resetAutoSnoozes(context: android.content.Context) {
            context.getSharedPreferences("morning", MODE_PRIVATE).edit().putInt(KEY_AUTO, 0).apply()
        }
    }
}
