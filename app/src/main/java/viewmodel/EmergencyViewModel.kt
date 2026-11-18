package com.cerca.app.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.location.Location
import android.net.Uri
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

    private val _state        = MutableStateFlow<EmergencyState>(EmergencyState.Idle)
    val state:        StateFlow<EmergencyState>       = _state

    private val _myAlerts     = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val myAlerts:     StateFlow<List<EmergencyAlert>> = _myAlerts

    private val _familyAlerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val familyAlerts: StateFlow<List<EmergencyAlert>> = _familyAlerts

    private val _familyHistory = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val familyHistory: StateFlow<List<EmergencyAlert>> = _familyHistory

    private var myListener:      ListenerRegistration? = null
    private var familyListener:  ListenerRegistration? = null
    private var historyListener: ListenerRegistration? = null

    private fun uid() = auth.currentUser?.uid

    // ── Suscripciones ─────────────────────────────────────────────────────────

    /** Suscribe con un único código (compatibilidad hacia atrás) */
    fun loadAndSubscribe(user: User) {
        val uid = uid() ?: return
        startMyAlertsListener(uid)
        val codes = listOfNotNull(user.familyCode.ifBlank { null })
        if (codes.isNotEmpty()) {
            startFamilyListener(codes, uid)
            startHistoryListener(codes)
        }
    }

    /** Suscribe con múltiples códigos (multi-familia) */
    fun loadAndSubscribeMulti(uid: String, familyCodes: List<String>) {
        startMyAlertsListener(uid)
        if (familyCodes.isNotEmpty()) {
            startFamilyListener(familyCodes, uid)
            startHistoryListener(familyCodes)
        }
    }

    private fun startMyAlertsListener(uid: String) {
        myListener?.remove()
        myListener = firestore.collection("emergencies")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snap, e ->
                if (e != null) return@addSnapshotListener
                _myAlerts.value = snap?.documents
                    ?.mapNotNull { it.toObject(EmergencyAlert::class.java)?.copy(id = it.id) }
                    ?.sortedByDescending { it.timestamp }?.take(20) ?: emptyList()
            }
    }

    private fun startFamilyListener(codes: List<String>, myUid: String) {
        familyListener?.remove()
        val query = if (codes.size == 1)
            firestore.collection("emergencies").whereEqualTo("familyCode", codes.first())
        else
            firestore.collection("emergencies").whereIn("familyCode", codes.take(10))

        familyListener = query.addSnapshotListener { snap, e ->
            if (e != null) { Log.w("EmergVM", e.message ?: ""); return@addSnapshotListener }
            val all = snap?.documents
                ?.mapNotNull { it.toObject(EmergencyAlert::class.java)?.copy(id = it.id) }
                ?: emptyList()
            val active = all.filter { it.userId != myUid && !it.resolved }
                .sortedByDescending { it.timestamp }.take(20)

            // Notificar nuevas alertas
            val prevIds = _familyAlerts.value.map { it.id }.toSet()
            active.forEach { alert ->
                if (alert.id !in prevIds) {
                    try {
                        EmergencyNotificationHelper.showFamilyAlert(
                            context = getApplication(), fromName = alert.userName,
                            message = alert.message, hasLocation = alert.hasLocation,
                            lat = alert.latitude, lng = alert.longitude
                        )
                    } catch (ex: Exception) { Log.w("EmergVM", ex.message ?: "") }
                }
            }
            _familyAlerts.value = active
        }
    }

    private fun startHistoryListener(codes: List<String>) {
        historyListener?.remove()
        val query = if (codes.size == 1)
            firestore.collection("emergencies").whereEqualTo("familyCode", codes.first())
        else
            firestore.collection("emergencies").whereIn("familyCode", codes.take(10))

        historyListener = query.addSnapshotListener { snap, e ->
            if (e != null) return@addSnapshotListener
            _familyHistory.value = snap?.documents
                ?.mapNotNull { it.toObject(EmergencyAlert::class.java)?.copy(id = it.id) }
                ?.sortedByDescending { it.timestamp }?.take(50) ?: emptyList()
        }
    }

    // ── Enviar SOS ────────────────────────────────────────────────────────────

    /**
     * Envía una alerta SOS.
     * @param targetCodes null = todas las familias del usuario.
     *                    listOf("ABC123") = solo esa familia.
     */
    @SuppressLint("MissingPermission")
    fun sendSOS(
        user: User,
        customMessage: String = "¡Necesito ayuda!",
        type: String = "sos",
        targetCodes: List<String>? = null
    ) {
        if (_state.value is EmergencyState.GettingLocation ||
            _state.value is EmergencyState.Sending) return
        _state.value = EmergencyState.GettingLocation

        // Resolver los códigos destino
        val codes = targetCodes
            ?: listOfNotNull(user.familyCode.ifBlank { null })

        if (codes.isEmpty()) {
            _state.value = EmergencyState.Error("No tienes familia vinculada")
            return
        }

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fusedLocation.removeLocationUpdates(this)
                sendToFamilies(user, customMessage, result.lastLocation, type, codes)
            }
        }
        try {
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMaxUpdates(1).setWaitForAccurateLocation(false).build()
            fusedLocation.requestLocationUpdates(req, cb, Looper.getMainLooper())
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                fusedLocation.removeLocationUpdates(cb)
                if (_state.value is EmergencyState.GettingLocation)
                    sendToFamilies(user, customMessage, null, type, codes)
            }, 8_000L)
        } catch (e: Exception) {
            sendToFamilies(user, customMessage, null, type, codes)
        }
    }

    private fun sendToFamilies(
        user: User, message: String, location: Location?,
        type: String, codes: List<String>
    ) {
        _state.value = EmergencyState.Sending
        val uid = uid() ?: run { _state.value = EmergencyState.Error("Sesión no iniciada"); return }

        var sent = 0
        var lastSaved: EmergencyAlert? = null

        codes.forEach { code ->
            val alert = EmergencyAlert(
                userId      = uid,
                userName    = user.name.ifBlank { "Usuario" },
                userPhone   = user.phone,
                familyCode  = code,
                latitude    = location?.latitude  ?: 0.0,
                longitude   = location?.longitude ?: 0.0,
                hasLocation = location != null,
                message     = message.ifBlank { "¡Necesito ayuda!" },
                timestamp   = System.currentTimeMillis(),
                resolved    = false,
                type        = type,
                bloodType   = user.bloodType,
                conditions  = user.conditions,
                allergies   = user.allergies
            )
            val ref = firestore.collection("emergencies").document()
            ref.set(alert.copy(id = ref.id))
                .addOnSuccessListener {
                    sent++
                    lastSaved = alert.copy(id = ref.id)
                    if (sent == codes.size) {
                        _state.value = EmergencyState.Sent(lastSaved!!)
                        try {
                            EmergencyNotificationHelper.showSOSSent(
                                context = getApplication(),
                                hasLocation = location != null,
                                lat = location?.latitude  ?: 0.0,
                                lng = location?.longitude ?: 0.0
                            )
                        } catch (ex: Exception) { Log.w("EmergVM", ex.message ?: "") }
                    }
                }
                .addOnFailureListener {
                    _state.value = EmergencyState.Error(it.message ?: "Error al enviar")
                }
        }
    }

    fun call911() {
        try {
            getApplication<Application>().startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:911")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) { Log.w("EmergVM", "call911: ${e.message}") }
    }

    fun resolveAlert(alertId: String) {
        firestore.collection("emergencies").document(alertId).update("resolved", true)
    }

    fun resetState() { _state.value = EmergencyState.Idle }

    override fun onCleared() {
        super.onCleared()
        myListener?.remove()
        familyListener?.remove()
        historyListener?.remove()
    }
}
