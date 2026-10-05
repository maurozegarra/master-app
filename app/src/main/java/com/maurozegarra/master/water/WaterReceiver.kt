package com.maurozegarra.master.water

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Lo que despierta al agua desde fuera del app (TD-190): la hora del recordatorio, los botones
 * +200 y +600 de la notificación, y los momentos en que Android borra lo programado
 * (reiniciar, actualizar el app, cambiar la hora).
 */
class WaterReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REMIND -> WaterAlarm.remind(context)
            ACTION_ADD -> intent.getIntExtra(EXTRA_ML, 0).takeIf { it > 0 }?.let { WaterAlarm.add(context, it) }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> WaterAlarm.reschedule(context)
        }
    }

    companion object {
        const val ACTION_REMIND = "com.maurozegarra.master.water.REMIND"
        const val ACTION_ADD = "com.maurozegarra.master.water.ADD"
        const val EXTRA_ML = "ml"
    }
}
