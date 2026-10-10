package com.maurozegarra.master.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.maurozegarra.master.audio.AlarmPlayer
import com.maurozegarra.master.audio.BeepVoiceAudio
import com.maurozegarra.master.data.SettingsStore

/** Lo que llega de la alarma de cada timer, de su notificación y del sistema (TD-195). */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID)
        when (intent.action) {
            ACTION_FIRE -> id?.let { fired(context, it) }
            ACTION_PAUSE -> id?.let { TimerAlarm.pause(context, it) }
            ACTION_RESUME -> id?.let { TimerAlarm.resume(context, it) }
            ACTION_CANCEL -> id?.let { TimerAlarm.cancel(context, it) }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            -> TimerAlarm.rescheduleAll(context)
        }
    }

    /**
     * Terminó un timer. Si suena como alarma, lo hace su servicio. Si pita, son los pitidos de
     * MASTER (decidido el 7-oct): tres, con la voz y el volumen de los pitidos del player. El
     * receptor se mantiene vivo con goAsync lo que tardan en sonar.
     */
    private fun fired(context: Context, id: String) {
        val run = TimerAlarm.fire(context, id) ?: return
        if (run.alarm) return
        val pendiente = goAsync()
        val app = context.applicationContext
        val player = AlarmPlayer(app)
        val uri = BeepVoiceAudio.uri(app, BeepVoiceAudio.current(app).cue)
        val volumen = SettingsStore(app).loadConfig().masterConfig.beepVolume / 100f
        val handler = Handler(Looper.getMainLooper())
        repeat(BEEPS) { i -> handler.postDelayed({ player.beepTone(uri, volumen) }, i * BEEP_GAP_MS) }
        handler.postDelayed({ pendiente.finish() }, BEEPS * BEEP_GAP_MS + 1_500)
    }

    companion object {
        const val ACTION_FIRE = "com.maurozegarra.master.timer.FIRE"
        const val ACTION_PAUSE = "com.maurozegarra.master.timer.PAUSE"
        const val ACTION_RESUME = "com.maurozegarra.master.timer.RESUME"
        const val ACTION_CANCEL = "com.maurozegarra.master.timer.CANCEL"
        const val EXTRA_ID = "id"
        private const val BEEPS = 3
        private const val BEEP_GAP_MS = 900L
    }
}
