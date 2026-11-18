package com.cerca.app.ui.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import java.util.Calendar

/**
 * BroadcastReceiver disparado por AlarmManager al llegar la hora del medicamento.
 *
 * - Adquiere WakeLock para garantizar que el dispositivo se despierte
 * - Muestra la notificación directamente (sin depender de ViewModel)
 * - Reprograma la próxima alarma 7 días adelante
 */
class MedicationReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_MED_NAME  = "extra_med_name"
        const val EXTRA_MED_DOSE  = "extra_med_dose"
        const val EXTRA_NOTIF_ID  = "extra_notif_id"
        const val EXTRA_MED_ID    = "extra_med_id"
        const val EXTRA_CAL_DAY   = "extra_cal_day"
        const val EXTRA_HOUR      = "extra_hour"
        const val EXTRA_MINUTE    = "extra_minute"
        const val EXTRA_DAYS      = "extra_days"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // ── 1. WakeLock: fuerza que el CPU no se duerma mientras procesamos ──
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Cerca:MedicationWakeLock"
        )
        wl.acquire(10_000L) // máximo 10 segundos

        try {
            val name    = intent.getStringExtra(EXTRA_MED_NAME)  ?: "Medicamento"
            val dose    = intent.getStringExtra(EXTRA_MED_DOSE)  ?: ""
            val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
            val medId   = intent.getStringExtra(EXTRA_MED_ID)    ?: ""
            val calDay  = intent.getIntExtra(EXTRA_CAL_DAY, -1)
            val hour    = intent.getIntExtra(EXTRA_HOUR, -1)
            val minute  = intent.getIntExtra(EXTRA_MINUTE, -1)
            val daysRaw = intent.getStringArrayListExtra(EXTRA_DAYS) ?: arrayListOf()
            val days    = daysRaw.mapNotNull { it.toIntOrNull() }

            // ── 2. Mostrar notificación ────────────────────────────────────
            MedicationNotificationHelper.createChannel(context)
            MedicationNotificationHelper.showNotification(context, name, dose, notifId)
            Log.d("MedReceiver", "✅ Notificación enviada: $name %02d:%02d".format(hour, minute))

            // ── 3. Reprogramar próxima alarma (7 días) ─────────────────────
            if (medId.isNotBlank() && calDay != -1 && hour != -1 && minute != -1) {
                scheduleNext(context, medId, name, dose, notifId, calDay, hour, minute, days)
            }
        } catch (e: Exception) {
            Log.e("MedReceiver", "Error en onReceive: ${e.message}")
        } finally {
            if (wl.isHeld) wl.release()
        }
    }

    private fun scheduleNext(
        context: Context,
        medId: String, name: String, dose: String,
        notifId: Int, calDay: Int, hour: Int, minute: Int,
        days: List<Int>
    ) {
        val nextTrigger = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 7)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val nextIntent = Intent(context, MedicationReceiver::class.java).apply {
            action = "ALARM_$notifId"
            putExtra(EXTRA_MED_NAME,  name)
            putExtra(EXTRA_MED_DOSE,  dose)
            putExtra(EXTRA_NOTIF_ID,  notifId)
            putExtra(EXTRA_MED_ID,    medId)
            putExtra(EXTRA_CAL_DAY,   calDay)
            putExtra(EXTRA_HOUR,      hour)
            putExtra(EXTRA_MINUTE,    minute)
            putStringArrayListExtra(EXTRA_DAYS, ArrayList(days.map { it.toString() }))
        }

        val pi = PendingIntent.getBroadcast(
            context, notifId, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            when {
                // Android 12+ Samsung/Xiaomi: setAlarmClock garantiza que
                // la alarma no sea postergada por optimización de batería
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val showIntent = PendingIntent.getActivity(
                        context, notifId + 10000,
                        Intent(context, Class.forName("com.cerca.app.MainActivity")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    am.setAlarmClock(
                        AlarmManager.AlarmClockInfo(nextTrigger, showIntent),
                        pi
                    )
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pi)
                else ->
                    am.setExact(AlarmManager.RTC_WAKEUP, nextTrigger, pi)
            }
            Log.d("MedReceiver", "⏰ Próxima alarma de $name en 7 días")
        } catch (e: Exception) {
            Log.w("MedReceiver", "scheduleNext error: ${e.message}")
        }
    }
}
