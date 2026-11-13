package com.cerca.app.viewmodel

import android.annotation.SuppressLint
import android.app.Application
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

    private val firestore     = FirebaseFirestore.getInstance()
    private val auth          = FirebaseAuth.getInstance()
    private val fusedLocation = LocationServices.getFusedLocationProviderClient(application)

    private val _state = MutableStateFlow<EmergencyState>(EmergencyState.Idle)
    val state: StateFlow<EmergencyState> = _state

    private val _myAlerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val myAlerts: StateFlow<List<EmergencyAlert>> = _myAlerts

    private val _familyAlerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val familyAlerts: StateFlow<List<EmergencyAlert>> = _familyAlerts

    private var myListener:     ListenerRegistration? = null
    private var familyListener: ListenerRegistration? = null

    private fun uid() = auth.currentUser?.uid

    // ── Suscripciones ─────────────────────────────────────────────────────────

    fun loadAndSubscribe(user: User) {
        val uid = uid() ?: return
        startMyAlertsListener(uid)
        if (user.familyCode.isNotBlank()) startFamilyAlertsListener(user.familyCode, uid)
    }

    private fun startMyAlertsListener(uid: String) {
        myListener?.remove()
        myListener = firestore.collection("emergencies")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snap, e ->
                if (e != null) { Log.w("EmergVM", e.message ?: ""); return@addSnapshotListener }
                _myAlerts.value = snap?.documents
                    ?.mapNotNull { it.toObject(EmergencyAlert::class.java)?.copy(id = it.id) }
                    ?.sortedByDescending { it.timestamp }
                    ?.take(10)
                    ?: emptyList()
            }
    }

    private fun startFamilyAlertsListener(familyCode: String, myUid: String) {
        familyListener?.remove()
        familyListener = firestore.collection("emergencies")
            .whereEqualTo("familyCode", familyCode)
            .addSnapshotListener { snap, e ->
                if (e != null) { Log.w("EmergVM", e.message ?: ""); return@addSnapshotListener }

                val all = snap?.documents
                    ?.mapNotNull { it.toObject(EmergencyAlert::class.java)?.copy(id = it.id) }
                    ?: emptyList()

                // Filtrar: no propias, no resueltas, ordenar por recientes
                val active = all
                    .filter { it.userId != myUid && !it.resolved }
                    .sortedByDescending { it.timestamp }
                    .take(20)

                // Notificar solo alertas nuevas
                val prevIds = _familyAlerts.value.map { it.id }.toSet()
                active.forEach { alert ->
                    if (alert.id !in prevIds) {
                        try {
                            EmergencyNotificationHelper.showFamilyAlert(
                                context     = getApplication(),
                                fromName    = alert.userName,
                                message     = alert.message,
                                hasLocation = alert.hasLocation,
                                lat         = alert.latitude,
                                lng         = alert.longitude
                            )
                        } catch (ex: Exception) {
                            Log.w("EmergVM", "notif: ${ex.message}")
                        }
                    }
                }
                _familyAlerts.value = active
            }
    }

    // ── Enviar SOS ────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun sendSOS(user: User, customMessage: String = "¡Necesito ayuda!") {
        if (_state.value is EmergencyState.GettingLocation ||
            _state.value is EmergencyState.Sending) return

        _state.value = EmergencyState.GettingLocation

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fusedLocation.removeLocationUpdates(this)
                sendAlert(user, customMessage, result.lastLocation)
            }
        }

        try {
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMaxUpdates(1)
                .setWaitForAccurateLocation(false)
                .build()
            fusedLocation.requestLocationUpdates(req, cb, Looper.getMainLooper())

            // Timeout 8s — enviar sin ubicación si no llega a tiempo
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                fusedLocation.removeLocationUpdates(cb)
                if (_state.value is EmergencyState.GettingLocation) {
                    sendAlert(user, customMessage, null)
                }
            }, 8_000L)
        } catch (e: Exception) {
            Log.w("EmergVM", "GPS error: ${e.message}")
            sendAlert(user, customMessage, null)
        }
    }

    private fun sendAlert(user: User, message: String, location: Location?) {
        _state.value = EmergencyState.Sending
        val uid = uid() ?: run {
            _state.value = EmergencyState.Error("Sesión no iniciada")
            return
        }

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
                _state.value = EmergencyState.Sent(alert.copy(id = ref.id))
                try {
                    EmergencyNotificationHelper.showSOSSent(
                        context     = getApplication(),
                        hasLocation = location != null,
                        lat         = location?.latitude  ?: 0.0,
                        lng         = location?.longitude ?: 0.0
                    )
                } catch (ex: Exception) {
                    Log.w("EmergVM", "notif sent: ${ex.message}")
                }
            }
            .addOnFailureListener { e ->
                _state.value = EmergencyState.Error(e.message ?: "Error al enviar")
            }
    }

    fun resolveAlert(alertId: String) {
        firestore.collection("emergencies").document(alertId)
            .update("resolved", true)
    }

    fun resetState() { _state.value = EmergencyState.Idle }

    override fun onCleared() {
        super.onCleared()
        myListener?.remove()
        familyListener?.remove()
    }
}
