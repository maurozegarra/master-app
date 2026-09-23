package com.maurozegarra.master.morning

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Lo que despierta a la alarma desde fuera del app (TD-151): la hora programada, el botón
 * "It eased", y los momentos en que Android borra las alarmas programadas.
 *
 * Reiniciar el teléfono, actualizar el app o cambiar la hora BORRAN lo programado en
 * AlarmManager. Sin reprogramar en esos tres casos, la alarma que reemplaza a su
 * despertador no sonaría al día siguiente de instalar una versión nueva.
 */
class MorningReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> {
                // Lo primero, sonar. La siguiente se programa al apagarla o posponerla; si
                // nadie la toca, el servicio la programa al rendirse (ver MorningRingService).
                MorningStore(context).snoozedUntil = 0L
                MorningAlarm.startRinging(context)
            }
            ACTION_EASED -> MorningAlarm.eased(context)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> MorningAlarm.reschedule(context)
        }
    }

    companion object {
        const val ACTION_FIRE = "com.maurozegarra.master.morning.FIRE"
        const val ACTION_EASED = "com.maurozegarra.master.morning.EASED"
    }
}
