package com.cerca.app.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.cerca.app.model.EmergencyAlert
import com.cerca.app.model.User
import com.cerca.app.ui.notifications.EmergencyNotificationHelper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class EmergencyState {
    object Idle : EmergencyState()
    object GettingLocation : EmergencyState()
    object Sending : EmergencyState()
    data class Sent(val alert: EmergencyAlert) : EmergencyState()
    data class Error(val message: String) : EmergencyState()
}

class EmergencyViewModel(application: Application) : AndroidViewModel(application) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth      = FirebaseAuth.getInstance()
    private val fusedLocation =
        LocationServices.getFusedLocationProviderClient(application)

    private val _state = MutableStateFlow<EmergencyState>(EmergencyState.Idle)
    val state: StateFlow<EmergencyState> = _state

    private val _myAlerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val myAlerts: StateFlow<List<EmergencyAlert>> = _myAlerts

    private val _familyAlerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val familyAlerts: StateFlow<List<EmergencyAlert>> = _familyAlerts

    private var alertsListener: ListenerRegistration? = null
    private var familyAlertsListener: ListenerRegistration? = null

    private fun uid() = auth.currentUser?.uid

    // ── Cargar perfil y suscribir alertas ────────────────────────────────────

    fun loadAndSubscribe(user: User) {
        val uid = uid() ?: return
        subscribeMyAlerts(uid)
        if (user.familyCode.isNotBlank()) subscribeFamilyAlerts(user.familyCode, uid)
    }

    private fun subscribeMyAlerts(uid: String) {
        alertsListener?.remove()
        alertsListener = firestore.collection("emergencies")
            .whereEqualTo("userId", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snap, e ->
                if (e != null) { Log.w("EmergVM", "myAlerts: ${e.message}"); return@addSnapshotListener }
                _myAlerts.value = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(EmergencyAlert::class.java)?.copy(id = doc.id)
                } ?: emptyList()
            }
    }

    private fun subscribeFamilyAlerts(familyCode: String, myUid: String) {
        familyAlertsListener?.remove()
        familyAlertsListener = firestore.collection("emergencies")
            .whereEqualTo("familyCode", familyCode)
            .whereEqualTo("resolved", false)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snap, e ->
                if (e != null) { Log.w("EmergVM", "familyAlerts: ${e.message}"); return@addSnapshotListener }
                val alerts = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(EmergencyAlert::class.java)?.copy(id = doc.id)
                }?.filter { it.userId != myUid } ?: emptyList()

                val previous = _familyAlerts.value.map { it.id }.toSet()
                alerts.forEach { alert ->
                    if (alert.id !in previous) {
                        // Mostrar notificación local para alertas nuevas de familiares
                        EmergencyNotificationHelper.showFamilyAlert(
                            context = getApplication(),
                            fromName = alert.userName,
                            message  = alert.message,
                            hasLocation = alert.hasLocation,
                            lat = alert.latitude,
                            lng = alert.longitude
                        )
                    }
                }
                _familyAlerts.value = alerts
            }
    }

    // ── Enviar alerta SOS ─────────────────────────────────────────────────────

    /**
     * Flujo principal:
     * 1. Intenta obtener ubicación precisa (timeout 8s)
     * 2. Construye EmergencyAlert con o sin ubicación
     * 3. Guarda en Firestore colección "emergencies"
     * 4. Muestra notificación local confirmando el envío
     */
    @SuppressLint("MissingPermission")
    fun sendSOS(user: User, customMessage: String = "¡Necesito ayuda!") {
        if (_state.value is EmergencyState.GettingLocation ||
            _state.value is EmergencyState.Sending) return

        _state.value = EmergencyState.GettingLocation

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMaxUpdates(1)
            .setWaitForAccurateLocation(false)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fusedLocation.removeLocationUpdates(this)
                val loc = result.lastLocation
                doSendAlert(user, customMessage, loc)
            }
        }

        try {
            fusedLocation.requestLocationUpdates(request, cb, Looper.getMainLooper())
            // Timeout: si en 8 segundos no llega ubicación, enviar sin ella
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                fusedLocation.removeLocationUpdates(cb)
                if (_state.value is EmergencyState.GettingLocation) {
                    doSendAlert(user, customMessage, null)
                }
            }, 8_000L)
        } catch (e: Exception) {
            Log.w("EmergVM", "No se pudo obtener ubicación: ${e.message}")
            doSendAlert(user, customMessage, null)
        }
    }

    private fun doSendAlert(user: User, message: String, location: Location?) {
        _state.value = EmergencyState.Sending
        val uid = uid() ?: run { _state.value = EmergencyState.Error("Sesión no iniciada"); return }

        val alert = EmergencyAlert(
            userId      = uid,
            userName    = user.name.ifBlank { "Usuario" },
            userPhone   = user.phone,
            familyCode  = user.familyCode,
            latitude    = location?.latitude  ?: 0.0,
            longitude   = location?.longitude ?: 0.0,
            hasLocation = location != null,
            message     = message.ifBlank { "¡Necesito ayuda!" },
            timestamp   = System.currentTimeMillis(),
            resolved    = false
        )

        val ref = firestore.collection("emergencies").document()
        ref.set(alert.copy(id = ref.id))
            .addOnSuccessListener {
                val saved = alert.copy(id = ref.id)
                _state.value = EmergencyState.Sent(saved)
                Log.d("EmergVM", "SOS enviado: ${ref.id}")

                // Notificación local confirmando el envío
                EmergencyNotificationHelper.showSOSSent(
                    context     = getApplication(),
                    hasLocation = location != null,
                    lat         = location?.latitude  ?: 0.0,
                    lng         = location?.longitude ?: 0.0
                )
            }
            .addOnFailureListener { e ->
                _state.value = EmergencyState.Error(e.message ?: "No se pudo enviar la alerta")
            }
    }

    fun resolveAlert(alertId: String) {
        firestore.collection("emergencies").document(alertId)
            .update("resolved", true)
            .addOnSuccessListener { Log.d("EmergVM", "Alerta resuelta: $alertId") }
    }

    fun resetState() { _state.value = EmergencyState.Idle }

    override fun onCleared() {
        super.onCleared()
        alertsListener?.remove()
        familyAlertsListener?.remove()
    }
}
