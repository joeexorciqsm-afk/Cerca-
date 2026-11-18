package com.cerca.app.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.cerca.app.model.Medication
import com.cerca.app.model.User
import com.cerca.app.ui.notifications.MedicationNotificationHelper
import com.cerca.app.ui.notifications.MedicationReceiver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

class MedicationViewModel(application: Application) : AndroidViewModel(application) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth      = FirebaseAuth.getInstance()

    private val _medications = MutableStateFlow<List<Medication>>(emptyList())
    val medications: StateFlow<List<Medication>> = _medications

    private val _familyMedications = MutableStateFlow<List<Medication>>(emptyList())
    val familyMedications: StateFlow<List<Medication>> = _familyMedications

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    // Listener en tiempo real de medicamentos públicos de la familia
    private val familyListeners = mutableListOf<ListenerRegistration>()

    private fun uid() = auth.currentUser?.uid

    // ── Mis medicamentos ──────────────────────────────────────────────────────

    fun loadMedications() {
        val uid = uid() ?: return
        _loading.value = true
        firestore.collection("users").document(uid).collection("medications").get()
            .addOnSuccessListener { snap ->
                _medications.value = snap.documents.mapNotNull { doc ->
                    doc.toObject(Medication::class.java)?.copy(id = doc.id)
                }.sortedBy { "%02d:%02d".format(it.hour, it.minute) }
                _loading.value = false
            }
            .addOnFailureListener { _loading.value = false; Log.w("MedVM", it.message ?: "") }
    }

    /**
     * Agrega un medicamento. Por defecto isPublic = true para que la familia
     * lo vea y reciba recordatorios de quién lo toma.
     * Guarda ownerName para mostrar el nombre en la notificación familiar.
     */
    fun addMedication(med: Medication, ownerName: String = "") {
        val uid = uid() ?: return
        _loading.value = true
        val ref = firestore.collection("users").document(uid).collection("medications").document()
        // isPublic = true por defecto, ownerName viene del perfil
        val withId = med.copy(id = ref.id, ownerId = uid, ownerName = ownerName,
            isPublic = med.isPublic) // respeta lo que venga del formulario
        ref.set(withId)
            .addOnSuccessListener {
                _message.value = "✅ Recordatorio añadido"
                scheduleAllAlarms(withId)
                loadMedications()
            }
            .addOnFailureListener { _message.value = "Error: ${it.message}"; _loading.value = false }
    }

    fun addMedicationAndSchedule(med: Medication, ownerName: String = "") =
        addMedication(med, ownerName)

    fun toggleActive(med: Medication) {
        val uid = uid() ?: return
        val updated = med.copy(active = !med.active)
        firestore.collection("users").document(uid).collection("medications").document(med.id)
            .set(mapOf("active" to updated.active), SetOptions.merge())
            .addOnSuccessListener {
                if (updated.active) scheduleAllAlarms(updated) else cancelAllAlarms(med)
                loadMedications()
            }
    }

    fun togglePublic(med: Medication) {
        val uid = uid() ?: return
        val updated = med.copy(isPublic = !med.isPublic)
        firestore.collection("users").document(uid).collection("medications").document(med.id)
            .set(mapOf("isPublic" to updated.isPublic), SetOptions.merge())
            .addOnSuccessListener {
                _message.value = if (updated.isPublic)
                    "👨‍👩‍👧 Tu familia verá este recordatorio"
                else
                    "🔒 Solo visible para ti"
                loadMedications()
            }
    }

    fun deleteMedication(med: Medication) {
        val uid = uid() ?: return
        cancelAllAlarms(med)
        firestore.collection("users").document(uid).collection("medications").document(med.id)
            .delete()
            .addOnSuccessListener { _message.value = "🗑️ Recordatorio eliminado"; loadMedications() }
            .addOnFailureListener { _message.value = "Error: ${it.message}" }
    }

    fun deleteMedicationByName(name: String): String? {
        val clean = name.lowercase().trim()
        val found = _medications.value.firstOrNull { m ->
            val mn = m.name.lowercase()
            mn == clean || mn.startsWith(clean) || mn.contains(clean)
        } ?: return null
        deleteMedication(found)
        return found.name
    }

    // ── Medicamentos públicos de la familia (listener en tiempo real) ──────────

    /**
     * Escucha en tiempo real los medicamentos públicos de cada familiar.
     * Cuando el alarm del familiar dispara y actualiza Firestore, esta app
     * recibe la notificación automáticamente.
     */
    fun listenFamilyMedications(familyMembers: List<User>) {
        val myUid = uid() ?: return
        // Limpiar listeners anteriores
        familyListeners.forEach { it.remove() }
        familyListeners.clear()

        val others = familyMembers.filter { it.id != myUid }
        if (others.isEmpty()) { _familyMedications.value = emptyList(); return }

        // Mapa mutable para ir acumulando resultados de cada familiar
        val resultsMap = mutableMapOf<String, List<Medication>>()

        others.forEach { member ->
            val listener = firestore.collection("users").document(member.id)
                .collection("medications")
                .whereEqualTo("isPublic", true)
                .addSnapshotListener { snap, e ->
                    if (e != null) return@addSnapshotListener
                    val meds = snap?.documents?.mapNotNull { doc ->
                        doc.toObject(Medication::class.java)?.copy(
                            id        = doc.id,
                            ownerId   = member.id,
                            ownerName = member.name.ifBlank { member.email.substringBefore("@") }
                        )
                    } ?: emptyList()

                    synchronized(resultsMap) {
                        resultsMap[member.id] = meds
                        _familyMedications.value = resultsMap.values
                            .flatten()
                            .sortedWith(compareBy({ it.ownerName }, { "%02d:%02d".format(it.hour, it.minute) }))
                    }
                }
            familyListeners.add(listener)
        }
    }

    // Alias para compatibilidad con código existente
    fun loadFamilyMedications(familyMembers: List<User>) = listenFamilyMedications(familyMembers)

    fun clearMessage() { _message.value = null }

    override fun onCleared() {
        super.onCleared()
        familyListeners.forEach { it.remove() }
        familyListeners.clear()
    }

    // ── AlarmManager ──────────────────────────────────────────────────────────

    fun scheduleAllAlarms(med: Medication) {
        if (!med.active) return
        med.days.forEach { scheduleNextAlarm(med, it) }
    }

    fun scheduleNextAlarm(med: Medication, calDay: Int) {
        if (!med.active) return
        val ctx = getApplication<Application>()
        val am  = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi  = buildPendingIntent(ctx, med, notifIdFor(med.id, calDay), calDay)
        val at  = nextOccurrence(calDay, med.hour, med.minute)
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val showIntent = PendingIntent.getActivity(
                        ctx,
                        notifIdFor(med.id, calDay) + 10000,
                        Intent(ctx, Class.forName("com.cerca.app.MainActivity")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent), pi)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else ->
                    am.setExact(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: Exception) { Log.w("MedVM", e.message ?: "") }
    }

    fun cancelAllAlarms(med: Medication) {
        val ctx = getApplication<Application>()
        val am  = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        med.days.forEach { calDay ->
            am.cancel(buildPendingIntent(ctx, med, notifIdFor(med.id, calDay), calDay))
        }
    }

    fun rescheduleAll() {
        val uid = uid() ?: return
        firestore.collection("users").document(uid).collection("medications")
            .whereEqualTo("active", true).get()
            .addOnSuccessListener { snap ->
                snap.documents.forEach { doc ->
                    val m = doc.toObject(Medication::class.java)?.copy(id = doc.id) ?: return@forEach
                    scheduleAllAlarms(m)
                }
            }
    }

    private fun buildPendingIntent(ctx: Context, med: Medication, notifId: Int, calDay: Int): PendingIntent {
        val intent = Intent(ctx, MedicationReceiver::class.java).apply {
            action = "ALARM_$notifId"
            putExtra(MedicationReceiver.EXTRA_MED_NAME,   med.name)
            putExtra(MedicationReceiver.EXTRA_MED_DOSE,   med.dose)
            putExtra(MedicationReceiver.EXTRA_NOTIF_ID,   notifId)
            putExtra(MedicationReceiver.EXTRA_MED_ID,     med.id)
            putExtra(MedicationReceiver.EXTRA_CAL_DAY,    calDay)
            putExtra(MedicationReceiver.EXTRA_HOUR,       med.hour)
            putExtra(MedicationReceiver.EXTRA_MINUTE,     med.minute)
            putExtra(MedicationReceiver.EXTRA_OWNER_NAME, med.ownerName)
            putExtra(MedicationReceiver.EXTRA_IS_PUBLIC,  med.isPublic)
            putExtra(MedicationReceiver.EXTRA_FAMILY_CODE, med.familyCode)
            putStringArrayListExtra(MedicationReceiver.EXTRA_DAYS, ArrayList(med.days.map { it.toString() }))
        }
        return PendingIntent.getBroadcast(ctx, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun nextOccurrence(dayOfWeek: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = cal.get(Calendar.DAY_OF_WEEK)
        var ahead = (dayOfWeek - today + 7) % 7
        if (ahead == 0 && cal.timeInMillis <= System.currentTimeMillis()) ahead = 7
        cal.add(Calendar.DAY_OF_YEAR, ahead)
        return cal.timeInMillis
    }

    fun notifIdFor(medId: String, day: Int) = (medId + day.toString()).hashCode()
}
