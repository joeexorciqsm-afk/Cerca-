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
 * - WakeLock: fuerza CPU despierto
 * - Notifica al dueño del medicamento
 * - Si isPublic=true, escribe un registro en Firestore para que los familiares
 *   reciban la notificación via su listener en tiempo real
 * - Reprograma la siguiente alarma
 */
class MedicationReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_MED_NAME   = "extra_med_name"
        const val EXTRA_MED_DOSE   = "extra_med_dose"
        const val EXTRA_NOTIF_ID   = "extra_notif_id"
        const val EXTRA_MED_ID     = "extra_med_id"
        const val EXTRA_CAL_DAY    = "extra_cal_day"
        const val EXTRA_HOUR       = "extra_hour"
        const val EXTRA_MINUTE     = "extra_minute"
        const val EXTRA_DAYS       = "extra_days"
        const val EXTRA_OWNER_NAME  = "extra_owner_name"
        const val EXTRA_IS_PUBLIC   = "extra_is_public"
        const val EXTRA_FAMILY_CODE = "extra_family_code"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Cerca:MedWakeLock")
        wl.acquire(10_000L)

        try {
            val name       = intent.getStringExtra(EXTRA_MED_NAME)   ?: "Medicamento"
            val dose       = intent.getStringExtra(EXTRA_MED_DOSE)   ?: ""
            val notifId    = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
            val medId      = intent.getStringExtra(EXTRA_MED_ID)     ?: ""
            val calDay     = intent.getIntExtra(EXTRA_CAL_DAY, -1)
            val hour       = intent.getIntExtra(EXTRA_HOUR, -1)
            val minute     = intent.getIntExtra(EXTRA_MINUTE, -1)
            val ownerName  = intent.getStringExtra(EXTRA_OWNER_NAME) ?: ""
            val isPublic   = intent.getBooleanExtra(EXTRA_IS_PUBLIC, false)
            val familyCode = intent.getStringExtra(EXTRA_FAMILY_CODE) ?: ""
            val daysRaw    = intent.getStringArrayListExtra(EXTRA_DAYS) ?: arrayListOf()
            val days       = daysRaw.mapNotNull { it.toIntOrNull() }

            // ── 1. Notificación propia ─────────────────────────────────────
            MedicationNotificationHelper.createChannel(context)
            MedicationNotificationHelper.showNotification(context, name, dose, notifId)
            Log.d("MedReceiver", "✅ Notificación propia: $name %02d:%02d".format(hour, minute))

            // ── 2. Si es público, escribir evento en Firestore ─────────────
            // Los familiares que tengan la app abierta o en background reciben
            // la notificación via su listener en tiempo real de medicamentos.
            // Para familiares con app cerrada, guardamos en colección
            // "medicationEvents" para que el BootReceiver / próximo arranque la notifique.
            if (isPublic && familyCode.isNotBlank() && ownerName.isNotBlank()) {
                try {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val event = mapOf(
                        "medName"    to name,
                        "dose"       to dose,
                        "ownerName"  to ownerName,
                        "familyCode" to familyCode,
                        "timestamp"  to System.currentTimeMillis(),
                        "hour"       to hour,
                        "minute"     to minute
                    )
                    db.collection("medicationEvents").add(event)
                        .addOnSuccessListener {
                            Log.d("MedReceiver", "📡 Evento familiar guardado en Firestore")
                        }
                } catch (e: Exception) {
                    Log.w("MedReceiver", "Firestore event error: ${e.message}")
                }

                // Notificación local para familiares que tengan la app en background
                MedicationNotificationHelper.showFamilyReminder(
                    context    = context,
                    ownerName  = ownerName,
                    medName    = name,
                    dose       = dose,
                    notifId    = notifId + 50000
                )
            }

            // ── 3. Reprogramar próxima alarma (7 días) ─────────────────────
            if (medId.isNotBlank() && calDay != -1 && hour != -1 && minute != -1) {
                scheduleNext(context, medId, name, dose, notifId, calDay, hour, minute,
                    days, ownerName, isPublic, familyCode)
            }

        } catch (e: Exception) {
            Log.e("MedReceiver", "Error: ${e.message}")
        } finally {
            if (wl.isHeld) wl.release()
        }
    }

    private fun scheduleNext(
        context: Context,
        medId: String, name: String, dose: String,
        notifId: Int, calDay: Int, hour: Int, minute: Int,
        days: List<Int>, ownerName: String, isPublic: Boolean, familyCode: String
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
            putExtra(EXTRA_MED_NAME,    name)
            putExtra(EXTRA_MED_DOSE,    dose)
            putExtra(EXTRA_NOTIF_ID,    notifId)
            putExtra(EXTRA_MED_ID,      medId)
            putExtra(EXTRA_CAL_DAY,     calDay)
            putExtra(EXTRA_HOUR,        hour)
            putExtra(EXTRA_MINUTE,      minute)
            putExtra(EXTRA_OWNER_NAME,  ownerName)
            putExtra(EXTRA_IS_PUBLIC,   isPublic)
            putExtra(EXTRA_FAMILY_CODE, familyCode)
            putStringArrayListExtra(EXTRA_DAYS, ArrayList(days.map { it.toString() }))
        }

        val pi = PendingIntent.getBroadcast(
            context, notifId, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val showIntent = PendingIntent.getActivity(
                        context, notifId + 10000,
                        Intent(context, Class.forName("com.cerca.app.MainActivity")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(nextTrigger, showIntent), pi)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pi)
                else ->
                    am.setExact(AlarmManager.RTC_WAKEUP, nextTrigger, pi)
            }
        } catch (e: Exception) {
            Log.w("MedReceiver", "scheduleNext: ${e.message}")
        }
    }
}
