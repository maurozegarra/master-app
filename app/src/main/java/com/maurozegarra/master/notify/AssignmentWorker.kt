package com.maurozegarra.master.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.maurozegarra.master.MainActivity
import com.maurozegarra.master.R
import com.maurozegarra.master.data.AssignmentRepository
import com.maurozegarra.master.data.WorkoutStore
import com.maurozegarra.master.i18n.I18n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Mira cada tanto si el entrenador repartió algo nuevo, con el app cerrado, y avisa.
 *
 * **No fusiona nada.** Solo compara lo que hay en el servidor con lo que el dispositivo ya
 * tiene guardado y, si aparece algo que no estaba, lanza una notificación. La fusión sigue
 * ocurriendo en un único sitio —al abrir el app, en `MainActivity.onStart`—, y esa es toda
 * la razón de que esto sea seguro: si escribiera aquí, dos procesos estarían guardando la
 * lista de trainings a la vez y el que llegase segundo pisaría al otro.
 *
 * Lo que se pierde con eso no es nada: al tocar la notificación se abre el app, que
 * sincroniza al arrancar, así que el training está ahí cuando el usuario llega.
 */
class AssignmentWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val store: WorkoutStore by inject()
    private val assignments: AssignmentRepository by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val profileId = assignments.profileId ?: return@withContext Result.success()

        // Null es "no se pudo leer". Se reintenta, y sobre todo no se avisa de nada: no
        // haber podido preguntar no es lo mismo que no tener nada nuevo.
        val incoming = assignments.assignedTrainings(profileId)
            ?: return@withContext Result.retry()

        val known = store.loadTrainings().filter { it.assigned }.mapTo(mutableSetOf()) { it.uid }
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val announced = prefs.getStringSet(KEY_ANNOUNCED, emptySet()).orEmpty()

        val fresh = incoming.filter { it.uid.isNotBlank() && it.uid !in known && it.uid !in announced }

        // La memoria se recorta a lo que sigue asignado hoy. Si al final le quitan un
        // training y se lo vuelven a poner, tiene que volver a avisar.
        prefs.edit()
            .putStringSet(KEY_ANNOUNCED, incoming.mapTo(mutableSetOf()) { it.uid } - known)
            .apply()

        // Se deja constancia aunque no haya nada que avisar: este trabajo es invisible por
        // definicion, y el dia que alguien diga "no me llego nada" esta linea es la unica
        // forma de saber si llego a preguntar y que vio.
        Log.i(TAG, "$profileId: ${incoming.size} asignados, ${known.size} ya en el telefono, ${fresh.size} por avisar")

        if (fresh.isNotEmpty()) notify(fresh.map { it.name })
        Result.success()
    }

    private fun notify(names: List<String>) {
        val t = I18n.EN
        val manager = NotificationManagerCompat.from(applicationContext)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, t.notifChannel, NotificationManager.IMPORTANCE_DEFAULT)
        )
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(if (names.size == 1) t.notifAssignedOne else t.notifAssignedMany)
            .setContentText(names.joinToString(", "))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        // Sin permiso de notificaciones esto no lanza nada y no pasa nada: el training
        // llega igual la proxima vez que se abra el app.
        runCatching { manager.notify(NOTIF_ID, notification) }
            .onFailure { Log.w(TAG, "no se pudo notificar", it) }
    }

    companion object {
        private const val TAG = "AssignmentWorker"
        private const val CHANNEL_ID = "master_assignments"
        private const val NOTIF_ID = 44
        private const val PREFS = "master_assign_notify"
        private const val KEY_ANNOUNCED = "announced_uids"
        private const val WORK_NAME = "assignments_watch"

        /**
         * Deja programado el sondeo. Se llama en cada arranque a propósito: es idempotente
         * —[ExistingPeriodicWorkPolicy.KEEP] respeta el que ya haya— y así se vuelve a
         * poner solo si el sistema lo canceló.
         *
         * Quince minutos es el mínimo que permite Android, y aun así el sistema lo agrupa
         * con otros trabajos: es un techo, no una promesa. En un Samsung con la
         * optimización de batería puesta puede tardar bastante más.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AssignmentWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
