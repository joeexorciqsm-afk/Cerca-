package com.cerca.app.ui.notifications

import android.app.AlarmManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.cerca.app.model.Medication
import com.cerca.app.viewmodel.MedicationViewModel
import java.util.Calendar

/**
 * BroadcastReceiver disparado por AlarmManager al llegar la hora del medicamento.
 *
 * Tras mostrar la notificación, programa la próxima ocurrencia (7 días después)
 * para mantener el ciclo sin depender de setRepeating (que no es exacto en Android 6+).
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
        val name    = intent.getStringExtra(EXTRA_MED_NAME)  ?: "Medicamento"
        val dose    = intent.getStringExtra(EXTRA_MED_DOSE)  ?: ""
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        val medId   = intent.getStringExtra(EXTRA_MED_ID)    ?: ""
        val calDay  = intent.getIntExtra(EXTRA_CAL_DAY, -1)
        val hour    = intent.getIntExtra(EXTRA_HOUR, -1)
        val minute  = intent.getIntExtra(EXTRA_MINUTE, -1)
        val daysRaw = intent.getStringArrayListExtra(EXTRA_DAYS) ?: arrayListOf()
        val days    = daysRaw.mapNotNull { it.toIntOrNull() }

        // 1. Mostrar la notificación
        MedicationNotificationHelper.createChannel(context)
        MedicationNotificationHelper.showNotification(context, name, dose, notifId)
        Log.d("MedReceiver", "Notificación enviada: $name a las %02d:%02d".format(hour, minute))

        // 2. Reprogramar la próxima ocurrencia en 7 días (ciclo semanal exacto)
        if (medId.isNotBlank() && calDay != -1 && hour != -1 && minute != -1 && days.isNotEmpty()) {
            try {
                val med = Medication(
                    id = medId, name = name, dose = dose,
                    hour = hour, minute = minute, days = days, active = true
                )
                val vm = MedicationViewModel(context.applicationContext as Application)
                val nextTrigger = Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis()
                    add(Calendar.DAY_OF_YEAR, 7)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
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
                val pi = android.app.PendingIntent.getBroadcast(
                    context, notifId, nextIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, nextTrigger, pi)
                }
                Log.d("MedReceiver", "Próxima alarma de $name en 7 días")
            } catch (e: Exception) {
                Log.w("MedReceiver", "No se pudo reprogramar: ${e.message}")
            }
        }
    }
}
