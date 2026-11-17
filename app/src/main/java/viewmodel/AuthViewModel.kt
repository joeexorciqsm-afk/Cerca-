package com.cerca.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.cerca.app.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AuthViewModel : ViewModel() {
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState

    private val _familyCode   = MutableStateFlow("")
    val familyCode:   StateFlow<String> = _familyCode

    private val _familyMessage = MutableStateFlow<String?>(null)
    val familyMessage: StateFlow<String?> = _familyMessage

    private val _phone      = MutableStateFlow("")
    val phone:      StateFlow<String> = _phone

    private val _familyName = MutableStateFlow("")
    val familyName: StateFlow<String> = _familyName

    private val _userName   = MutableStateFlow("")
    val userName:   StateFlow<String> = _userName

    private val _age        = MutableStateFlow("")
    val age:        StateFlow<String> = _age

    private val _role       = MutableStateFlow("familiar")
    val role:       StateFlow<String> = _role

    private val _familyMembers = MutableStateFlow<List<User>>(emptyList())
    val familyMembers: StateFlow<List<User>> = _familyMembers

    // ── Info médica ───────────────────────────────────────────────────────────
    private val _bloodType   = MutableStateFlow("")
    val bloodType:   StateFlow<String> = _bloodType

    private val _allergies   = MutableStateFlow("")
    val allergies:   StateFlow<String> = _allergies

    private val _conditions  = MutableStateFlow("")
    val conditions:  StateFlow<String> = _conditions

    private val _emergencyInfo = MutableStateFlow("")
    val emergencyInfo: StateFlow<String> = _emergencyInfo

    private val _emergencyContactName  = MutableStateFlow("")
    val emergencyContactName:  StateFlow<String> = _emergencyContactName

    private val _emergencyContactPhone = MutableStateFlow("")
    val emergencyContactPhone: StateFlow<String> = _emergencyContactPhone

    init {
        if (firebaseAuth.currentUser != null) {
            _authState.value = AuthState.Authenticated
            loadFamilyCode()
        }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Completa el correo y la contraseña")
            return
        }
        _authState.value = AuthState.Loading
        firebaseAuth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener {
                _authState.value = AuthState.Authenticated
                loadFamilyCode()
            }
            .addOnFailureListener { _authState.value = AuthState.Error(it.message ?: "Error al iniciar sesión") }
    }

    fun signup(email: String, password: String, name: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Completa todos los campos"); return
        }
        if (password.length < 6) {
            _authState.value = AuthState.Error("La contraseña debe tener al menos 6 caracteres"); return
        }
        _authState.value = AuthState.Loading
        firebaseAuth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val uid  = result.user?.uid ?: ""
                val code = generateFamilyCode()
                _familyCode.value = code
                _authState.value  = AuthState.Authenticated
                try {
                    val user = User(id = uid, email = email.trim(), name = name.trim(),
                        familyCode = code, role = "familiar")
                    firestore.collection("users").document(uid).set(user)
                } catch (e: Exception) {
                    Log.w("AuthVM", "signup Firestore: ${e.message}")
                }
            }
            .addOnFailureListener { _authState.value = AuthState.Error(it.message ?: "Error al registrarse") }
    }

    // ── Perfil ────────────────────────────────────────────────────────────────

    fun savePersonalInfo(name: String, age: String, phone: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        _userName.value = name.trim()
        _age.value      = age.trim()
        _phone.value    = phone.trim()
        val datos = mapOf("name" to name.trim(), "age" to age.trim(), "phone" to phone.trim())
        firestore.collection("users").document(uid).set(datos, SetOptions.merge())
            .addOnSuccessListener {
                _familyMessage.value = "Información guardada"
                if (_familyCode.value.isNotBlank()) loadFamilyMembers(_familyCode.value)
            }
            .addOnFailureListener { _familyMessage.value = it.message }
    }

    fun saveMedicalInfo(
        bloodType: String, allergies: String, conditions: String,
        emergencyInfo: String, emergencyContactName: String, emergencyContactPhone: String
    ) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        _bloodType.value            = bloodType.trim()
        _allergies.value            = allergies.trim()
        _conditions.value           = conditions.trim()
        _emergencyInfo.value        = emergencyInfo.trim()
        _emergencyContactName.value = emergencyContactName.trim()
        _emergencyContactPhone.value = emergencyContactPhone.trim()

        val datos = mapOf(
            "bloodType"            to bloodType.trim(),
            "allergies"            to allergies.trim(),
            "conditions"           to conditions.trim(),
            "emergencyInfo"        to emergencyInfo.trim(),
            "emergencyContactName" to emergencyContactName.trim(),
            "emergencyContactPhone" to emergencyContactPhone.trim()
        )
        firestore.collection("users").document(uid).set(datos, SetOptions.merge())
            .addOnSuccessListener { _familyMessage.value = "✅ Info médica guardada" }
            .addOnFailureListener { _familyMessage.value = it.message }
    }

    fun saveRole(newRole: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        _role.value = newRole
        firestore.collection("users").document(uid)
            .set(mapOf("role" to newRole), SetOptions.merge())
    }

    fun savePhone(newPhone: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        _phone.value = newPhone.trim()
        firestore.collection("users").document(uid)
            .set(mapOf("phone" to newPhone.trim()), SetOptions.merge())
            .addOnSuccessListener { _familyMessage.value = "Teléfono guardado" }
    }

    // ── Familia ───────────────────────────────────────────────────────────────

    fun joinFamily(code: String) {
        val uid      = firebaseAuth.currentUser?.uid ?: run { _familyMessage.value = "Debes iniciar sesión"; return }
        val clean    = code.trim().uppercase()
        if (clean.isBlank()) { _familyMessage.value = "Ingresa un código de familia"; return }

        firestore.collection("users").whereEqualTo("familyCode", clean).limit(1).get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) { _familyMessage.value = "No existe una familia con ese código"; return@addOnSuccessListener }
                firestore.collection("users").document(uid)
                    .set(mapOf("familyCode" to clean), SetOptions.merge())
                    .addOnSuccessListener {
                        _familyCode.value = clean
                        _familyMessage.value = "¡Te uniste a la familia!"
                        loadFamilyName(clean)
                        loadFamilyMembers(clean)
                    }
                    .addOnFailureListener { _familyMessage.value = it.message }
            }
            .addOnFailureListener { _familyMessage.value = it.message }
    }

    fun leaveFamily() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val nuevo = generateFamilyCode()
        firestore.collection("users").document(uid)
            .set(mapOf("familyCode" to nuevo), SetOptions.merge())
            .addOnSuccessListener {
                _familyCode.value  = nuevo
                _familyName.value  = ""
                _familyMembers.value = emptyList()
                _familyMessage.value = "Saliste de la familia"
            }
    }

    fun saveFamilyName(newName: String) {
        val code  = _familyCode.value
        val clean = newName.trim()
        if (code.isBlank())  { _familyMessage.value = "Primero necesitas un código de familia"; return }
        if (clean.isBlank()) { _familyMessage.value = "Escribe un nombre"; return }
        _familyName.value = clean
        firestore.collection("families").document(code)
            .set(mapOf("name" to clean), SetOptions.merge())
            .addOnSuccessListener { _familyMessage.value = "Nombre de familia guardado" }
    }

    fun clearFamilyMessage() { _familyMessage.value = null }

    // ── Carga interna ─────────────────────────────────────────────────────────

    private fun loadFamilyCode() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                _phone.value      = doc.getString("phone") ?: ""
                _userName.value   = doc.getString("name")  ?: ""
                _age.value        = doc.getString("age")   ?: ""
                _role.value       = doc.getString("role")  ?: "familiar"
                // Info médica
                _bloodType.value            = doc.getString("bloodType")            ?: ""
                _allergies.value            = doc.getString("allergies")            ?: ""
                _conditions.value           = doc.getString("conditions")           ?: ""
                _emergencyInfo.value        = doc.getString("emergencyInfo")        ?: ""
                _emergencyContactName.value = doc.getString("emergencyContactName") ?: ""
                _emergencyContactPhone.value = doc.getString("emergencyContactPhone") ?: ""

                val code = doc.getString("familyCode")
                if (!code.isNullOrBlank()) {
                    _familyCode.value = code
                    loadFamilyName(code)
                    loadFamilyMembers(code)
                } else {
                    assignFamilyCodeIfMissing(uid, firebaseAuth.currentUser?.email ?: "",
                        doc.getString("name") ?: "")
                }
            }
            .addOnFailureListener { Log.w("AuthVM", it.message ?: "") }
    }

    private fun assignFamilyCodeIfMissing(uid: String, email: String, name: String) {
        val code = generateFamilyCode()
        _familyCode.value = code
        val user = User(id = uid, email = email, name = name, familyCode = code)
        firestore.collection("users").document(uid).set(user)
    }

    fun loadFamilyMembers(code: String = _familyCode.value) {
        if (code.isBlank()) { _familyMembers.value = emptyList(); return }
        firestore.collection("users").whereEqualTo("familyCode", code).get()
            .addOnSuccessListener { snap ->
                _familyMembers.value = snap.documents
                    .mapNotNull { it.toObject(User::class.java)?.copy(id = it.id) }
                    .sortedBy { it.name.lowercase() }
            }
            .addOnFailureListener { Log.w("AuthVM", it.message ?: "") }
    }

    private fun loadFamilyName(code: String) {
        if (code.isBlank()) return
        firestore.collection("families").document(code).get()
            .addOnSuccessListener { _familyName.value = it.getString("name") ?: "" }
    }

    fun logout() {
        firebaseAuth.signOut()
        _familyCode.value  = ""
        _familyName.value  = ""
        _familyMessage.value = null
        _phone.value       = ""
        _userName.value    = ""
        _age.value         = ""
        _role.value        = "familiar"
        _bloodType.value   = ""
        _allergies.value   = ""
        _conditions.value  = ""
        _emergencyInfo.value = ""
        _emergencyContactName.value  = ""
        _emergencyContactPhone.value = ""
        _familyMembers.value = emptyList()
        _authState.value   = AuthState.Unauthenticated
    }

    private fun generateFamilyCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}

sealed class AuthState {
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
}
