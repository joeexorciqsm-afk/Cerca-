package com.cerca.app.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.cerca.app.model.Medication
import com.cerca.app.ui.notifications.MedicationReceiver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

class MedicationViewModel(application: Application) : AndroidViewModel(application) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _medications = MutableStateFlow<List<Medication>>(emptyList())
    val medications: StateFlow<List<Medication>> = _medications

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private fun uid() = auth.currentUser?.uid

    // ─── Colección Firestore: users/{uid}/medications ─────────────────────────

    fun loadMedications() {
        val uid = uid() ?: return
        _loading.value = true
        firestore.collection("users").document(uid)
            .collection("medications")
            .get()
            .addOnSuccessListener { snap ->
                _medications.value = snap.documents.mapNotNull { doc ->
                    val m = doc.toObject(Medication::class.java) ?: return@mapNotNull null
                    m.copy(id = doc.id)
                }.sortedBy { "${it.hour.toString().padStart(2,'0')}:${it.minute.toString().padStart(2,'0')}" }
                _loading.value = false
            }
            .addOnFailureListener { e ->
                Log.w("MedVM", "loadMedications error: ${e.message}")
                _loading.value = false
            }
    }

    fun addMedication(med: Medication) {
        val uid = uid() ?: return
        _loading.value = true
        val ref = firestore.collection("users").document(uid).collection("medications").document()
        val withId = med.copy(id = ref.id)
        ref.set(withId)
            .addOnSuccessListener {
                _message.value = "✅ Recordatorio añadido"
                scheduleAlarms(withId)
                loadMedications()
            }
            .addOnFailureListener { e ->
                _message.value = "Error: ${e.message}"
                _loading.value = false
            }
    }

    fun toggleActive(med: Medication) {
        val uid = uid() ?: return
        val updated = med.copy(active = !med.active)
        firestore.collection("users").document(uid)
            .collection("medications").document(med.id)
            .set(mapOf("active" to updated.active), SetOptions.merge())
            .addOnSuccessListener {
                if (updated.active) scheduleAlarms(updated) else cancelAlarms(med)
                loadMedications()
            }
    }

    fun deleteMedication(med: Medication) {
        val uid = uid() ?: return
        cancelAlarms(med)
        firestore.collection("users").document(uid)
            .collection("medications").document(med.id)
            .delete()
            .addOnSuccessListener {
                _message.value = "🗑️ Recordatorio eliminado"
                loadMedications()
            }
            .addOnFailureListener { e ->
                _message.value = "Error al eliminar: ${e.message}"
            }
    }

    fun clearMessage() { _message.value = null }

    // ─── AlarmManager ─────────────────────────────────────────────────────────

    /**
     * Programa una alarma repetida por cada día seleccionado.
     * notifId único = hash(medId + dayOfWeek) para poder cancelar individualmente.
     */
    private fun scheduleAlarms(med: Medication) {
        if (!med.active) return
        val ctx = getApplication<Application>()
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        med.days.forEach { day ->
            val notifId = notifIdFor(med.id, day)
            val pi = buildPendingIntent(ctx, med, notifId)

            val trigger = nextOccurrence(day, med.hour, med.minute)
            try {
                am.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    trigger,
                    AlarmManager.INTERVAL_DAY * 7,
                    pi
                )
            } catch (e: Exception) {
                Log.w("MedVM", "scheduleAlarms: ${e.message}")
            }
        }
    }

    private fun cancelAlarms(med: Medication) {
        val ctx = getApplication<Application>()
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        med.days.forEach { day ->
            val notifId = notifIdFor(med.id, day)
            val pi = buildPendingIntent(ctx, med, notifId)
            am.cancel(pi)
        }
    }

    private fun buildPendingIntent(ctx: Context, med: Medication, notifId: Int): PendingIntent {
        val intent = Intent(ctx, MedicationReceiver::class.java).apply {
            putExtra(MedicationReceiver.EXTRA_MED_NAME, med.name)
            putExtra(MedicationReceiver.EXTRA_MED_DOSE, med.dose)
            putExtra(MedicationReceiver.EXTRA_NOTIF_ID, notifId)
            putExtra(MedicationReceiver.EXTRA_MED_ID, med.id)
        }
        return PendingIntent.getBroadcast(
            ctx, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Calcula la próxima ocurrencia del [dayOfWeek] (1=Dom..7=Sáb, estilo Calendar) a la hora dada.
     * Si ya pasó hoy, avanza 7 días.
     */
    private fun nextOccurrence(dayOfWeek: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = cal.get(Calendar.DAY_OF_WEEK)
        var daysAhead = (dayOfWeek - today + 7) % 7
        if (daysAhead == 0 && cal.timeInMillis <= System.currentTimeMillis()) daysAhead = 7
        cal.add(Calendar.DAY_OF_YEAR, daysAhead)
        return cal.timeInMillis
    }

    private fun notifIdFor(medId: String, day: Int): Int =
        (medId + day.toString()).hashCode()
}
