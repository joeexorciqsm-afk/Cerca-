package com.cerca.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.cerca.app.model.FamilyGroup
import com.cerca.app.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AuthViewModel : ViewModel() {
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState

    // ── Código propio (el que el usuario genera para invitar a otros) ─────────
    private val _myFamilyCode = MutableStateFlow("")
    val myFamilyCode: StateFlow<String> = _myFamilyCode

    // Alias de compatibilidad — devuelve el primer código o el propio
    val familyCode: StateFlow<String> get() = _myFamilyCode

    private val _familyMessage = MutableStateFlow<String?>(null)
    val familyMessage: StateFlow<String?> = _familyMessage

    // ── Perfil ────────────────────────────────────────────────────────────────
    private val _phone      = MutableStateFlow("")
    val phone:      StateFlow<String> = _phone

    private val _userName   = MutableStateFlow("")
    val userName:   StateFlow<String> = _userName

    private val _age        = MutableStateFlow("")
    val age:        StateFlow<String> = _age

    private val _role       = MutableStateFlow("familiar")
    val role:       StateFlow<String> = _role

    // ── Info médica ───────────────────────────────────────────────────────────
    private val _bloodType            = MutableStateFlow("")
    val bloodType:            StateFlow<String> = _bloodType
    private val _allergies            = MutableStateFlow("")
    val allergies:            StateFlow<String> = _allergies
    private val _conditions           = MutableStateFlow("")
    val conditions:           StateFlow<String> = _conditions
    private val _emergencyInfo        = MutableStateFlow("")
    val emergencyInfo:        StateFlow<String> = _emergencyInfo
    private val _emergencyContactName = MutableStateFlow("")
    val emergencyContactName: StateFlow<String> = _emergencyContactName
    private val _emergencyContactPhone = MutableStateFlow("")
    val emergencyContactPhone: StateFlow<String> = _emergencyContactPhone

    // ── Multi-familia ─────────────────────────────────────────────────────────
    /** Lista de todas las familias a las que pertenece el usuario */
    private val _familyGroups = MutableStateFlow<List<FamilyGroup>>(emptyList())
    val familyGroups: StateFlow<List<FamilyGroup>> = _familyGroups

    /** Todos los miembros de todas las familias (sin duplicados, sin el propio usuario) */
    private val _familyMembers = MutableStateFlow<List<User>>(emptyList())
    val familyMembers: StateFlow<List<User>> = _familyMembers

    /** Nombre de la familia activa (primera por defecto — compatible con código existente) */
    private val _familyName = MutableStateFlow("")
    val familyName: StateFlow<String> = _familyName

    // Listeners por código de familia
    private val familyListeners = mutableMapOf<String, ListenerRegistration>()

    init {
        if (firebaseAuth.currentUser != null) {
            _authState.value = AuthState.Authenticated
            loadProfile()
        }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Completa el correo y la contraseña"); return
        }
        _authState.value = AuthState.Loading
        firebaseAuth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { _authState.value = AuthState.Authenticated; loadProfile() }
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
                _myFamilyCode.value = code
                _authState.value    = AuthState.Authenticated
                try {
                    val user = User(id = uid, email = email.trim(), name = name.trim(),
                        familyCode = code, role = "familiar")
                    // Guardar usuario con familyCodes como array
                    firestore.collection("users").document(uid).set(
                        mapOf(
                            "id" to uid, "email" to email.trim(), "name" to name.trim(),
                            "familyCode" to code, "familyCodes" to listOf(code), "role" to "familiar"
                        )
                    )
                    // Crear el documento de familia
                    firestore.collection("families").document(code)
                        .set(mapOf("name" to "${name.trim()}'s familia", "createdBy" to uid))
                } catch (e: Exception) { Log.w("AuthVM", e.message ?: "") }
            }
            .addOnFailureListener { _authState.value = AuthState.Error(it.message ?: "Error al registrarse") }
    }

    // ── Perfil ────────────────────────────────────────────────────────────────

    fun savePersonalInfo(name: String, age: String, phone: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        _userName.value = name.trim(); _age.value = age.trim(); _phone.value = phone.trim()
        firestore.collection("users").document(uid)
            .set(mapOf("name" to name.trim(), "age" to age.trim(), "phone" to phone.trim()), SetOptions.merge())
            .addOnSuccessListener { _familyMessage.value = "Información guardada" }
            .addOnFailureListener { _familyMessage.value = it.message }
    }

    fun saveMedicalInfo(
        bloodType: String, allergies: String, conditions: String,
        emergencyInfo: String, emergencyContactName: String, emergencyContactPhone: String
    ) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        _bloodType.value = bloodType.trim(); _allergies.value = allergies.trim()
        _conditions.value = conditions.trim(); _emergencyInfo.value = emergencyInfo.trim()
        _emergencyContactName.value = emergencyContactName.trim()
        _emergencyContactPhone.value = emergencyContactPhone.trim()
        firestore.collection("users").document(uid).set(mapOf(
            "bloodType" to bloodType.trim(), "allergies" to allergies.trim(),
            "conditions" to conditions.trim(), "emergencyInfo" to emergencyInfo.trim(),
            "emergencyContactName" to emergencyContactName.trim(),
            "emergencyContactPhone" to emergencyContactPhone.trim()
        ), SetOptions.merge())
            .addOnSuccessListener { _familyMessage.value = "✅ Info médica guardada" }
            .addOnFailureListener { _familyMessage.value = it.message }
    }

    fun saveRole(newRole: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        _role.value = newRole
        firestore.collection("users").document(uid).set(mapOf("role" to newRole), SetOptions.merge())
    }

    fun savePhone(newPhone: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        _phone.value = newPhone.trim()
        firestore.collection("users").document(uid)
            .set(mapOf("phone" to newPhone.trim()), SetOptions.merge())
            .addOnSuccessListener { _familyMessage.value = "Teléfono guardado" }
    }

    // ── Multi-familia ─────────────────────────────────────────────────────────

    /**
     * Unirse a una familia por código.
     * Agrega el código al array `familyCodes` del usuario (no reemplaza).
     */
    fun joinFamily(code: String) {
        val uid   = firebaseAuth.currentUser?.uid ?: run { _familyMessage.value = "Sesión no iniciada"; return }
        val clean = code.trim().uppercase()
        if (clean.isBlank()) { _familyMessage.value = "Ingresa un código"; return }
        if (_familyGroups.value.any { it.code == clean }) {
            _familyMessage.value = "Ya perteneces a esta familia"; return
        }

        // Verificar que el código existe
        firestore.collection("users").whereEqualTo("familyCode", clean).limit(1).get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) { _familyMessage.value = "Código no encontrado"; return@addOnSuccessListener }

                // Agregar el código al array del usuario
                firestore.collection("users").document(uid)
                    .update("familyCodes", FieldValue.arrayUnion(clean))
                    .addOnSuccessListener {
                        _familyMessage.value = "✅ ¡Te uniste a la familia!"
                        subscribeToFamily(clean)
                    }
                    .addOnFailureListener {
                        // Si el campo no existe aún, créalo
                        firestore.collection("users").document(uid)
                            .set(mapOf("familyCodes" to listOf(clean)), SetOptions.merge())
                            .addOnSuccessListener {
                                _familyMessage.value = "✅ ¡Te uniste a la familia!"
                                subscribeToFamily(clean)
                            }
                    }
            }
            .addOnFailureListener { _familyMessage.value = it.message }
    }

    /**
     * Salir de una familia específica.
     * Elimina ese código del array `familyCodes`.
     */
    fun leaveFamily(code: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (code == _myFamilyCode.value) {
            _familyMessage.value = "No puedes salir de tu propia familia"; return
        }
        firestore.collection("users").document(uid)
            .update("familyCodes", FieldValue.arrayRemove(code))
            .addOnSuccessListener {
                familyListeners[code]?.remove()
                familyListeners.remove(code)
                val updated = _familyGroups.value.filter { it.code != code }
                _familyGroups.value = updated
                rebuildAllMembers()
                _familyMessage.value = "Saliste de la familia"
            }
            .addOnFailureListener { _familyMessage.value = it.message }
    }

    fun saveFamilyName(newName: String, code: String = _myFamilyCode.value) {
        val clean = newName.trim()
        if (code.isBlank())  { _familyMessage.value = "Código no válido"; return }
        if (clean.isBlank()) { _familyMessage.value = "Escribe un nombre"; return }
        // Actualizar nombre local
        val updated = _familyGroups.value.map {
            if (it.code == code) it.copy(name = clean) else it
        }
        _familyGroups.value = updated
        if (code == _myFamilyCode.value) _familyName.value = clean
        firestore.collection("families").document(code)
            .set(mapOf("name" to clean), SetOptions.merge())
            .addOnSuccessListener { _familyMessage.value = "Nombre guardado" }
    }

    fun clearFamilyMessage() { _familyMessage.value = null }

    // ── Carga y listeners ─────────────────────────────────────────────────────

    private fun loadProfile() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                _phone.value        = doc.getString("phone")  ?: ""
                _userName.value     = doc.getString("name")   ?: ""
                _age.value          = doc.getString("age")    ?: ""
                _role.value         = doc.getString("role")   ?: "familiar"
                _bloodType.value    = doc.getString("bloodType")            ?: ""
                _allergies.value    = doc.getString("allergies")            ?: ""
                _conditions.value   = doc.getString("conditions")           ?: ""
                _emergencyInfo.value = doc.getString("emergencyInfo")       ?: ""
                _emergencyContactName.value  = doc.getString("emergencyContactName")  ?: ""
                _emergencyContactPhone.value = doc.getString("emergencyContactPhone") ?: ""

                // Código propio (el que genera al registrarse)
                val ownCode = doc.getString("familyCode") ?: ""
                if (ownCode.isBlank()) {
                    val newCode = generateFamilyCode()
                    _myFamilyCode.value = newCode
                    firestore.collection("users").document(uid).set(
                        mapOf("familyCode" to newCode, "familyCodes" to listOf(newCode)),
                        SetOptions.merge()
                    )
                    subscribeToFamily(newCode)
                } else {
                    _myFamilyCode.value = ownCode

                    // familyCodes = todos los códigos (propio + los que se unió)
                    @Suppress("UNCHECKED_CAST")
                    val allCodes = (doc.get("familyCodes") as? List<String>)
                        ?.filter { it.isNotBlank() }
                        ?.distinct()
                        ?: listOf(ownCode)

                    // Asegurar que el código propio siempre esté en la lista
                    val codes = if (ownCode in allCodes) allCodes else listOf(ownCode) + allCodes

                    // Suscribir a todas las familias
                    codes.forEach { subscribeToFamily(it) }
                }
            }
            .addOnFailureListener { Log.w("AuthVM", it.message ?: "") }
    }

    /**
     * Suscribe a los miembros de una familia en tiempo real.
     * Llama a rebuildAllMembers() cada vez que cambia alguna familia.
     */
    private fun subscribeToFamily(code: String) {
        if (code.isBlank() || familyListeners.containsKey(code)) return

        // Cargar nombre de la familia
        firestore.collection("families").document(code).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: ""
                if (code == _myFamilyCode.value) _familyName.value = name
                updateGroupName(code, name)
            }

        val listener = firestore.collection("users")
            .whereArrayContains("familyCodes", code)
            .addSnapshotListener { snap, e ->
                if (e != null) { Log.w("AuthVM", e.message ?: ""); return@addSnapshotListener }

                // Query nueva (familyCodes array)
                val fromArray = snap?.documents
                    ?.mapNotNull { it.toObject(User::class.java)?.copy(id = it.id) }
                    ?: emptyList()

                // Query legacy (familyCode string) — necesaria para usuarios sin migrar
                firestore.collection("users")
                    .whereEqualTo("familyCode", code)
                    .get()
                    .addOnSuccessListener { legacySnap ->
                        val fromLegacy = legacySnap.documents
                            .mapNotNull { it.toObject(User::class.java)?.copy(id = it.id) }

                        val members = (fromArray + fromLegacy)
                            .distinctBy { it.id }
                            .sortedBy { it.name.lowercase() }

                        val existing = _familyGroups.value.toMutableList()
                        val idx = existing.indexOfFirst { it.code == code }
                        val name = existing.getOrNull(idx)?.name ?: ""
                        val group = FamilyGroup(code = code, name = name, members = members)
                        if (idx >= 0) existing[idx] = group else existing.add(group)
                        _familyGroups.value = existing
                        rebuildAllMembers()

                        // Migrar el usuario actual al nuevo formato si aún no tiene familyCodes
                        val myUid = firebaseAuth.currentUser?.uid ?: return@addOnSuccessListener
                        fromLegacy.forEach { user ->
                            if (user.id == myUid) {
                                firestore.collection("users").document(myUid)
                                    .set(mapOf("familyCodes" to listOf(code)), SetOptions.merge())
                            }
                        }
                    }
                    .addOnFailureListener {
                        // Si falla la query legacy, usar solo los resultados del array
                        val members = fromArray.sortedBy { it.name.lowercase() }
                        val existing = _familyGroups.value.toMutableList()
                        val idx = existing.indexOfFirst { it.code == code }
                        val name = existing.getOrNull(idx)?.name ?: ""
                        val group = FamilyGroup(code = code, name = name, members = members)
                        if (idx >= 0) existing[idx] = group else existing.add(group)
                        _familyGroups.value = existing
                        rebuildAllMembers()
                    }
            }

        familyListeners[code] = listener
    }

    private fun updateGroupName(code: String, name: String) {
        val existing = _familyGroups.value.toMutableList()
        val idx = existing.indexOfFirst { it.code == code }
        if (idx >= 0) existing[idx] = existing[idx].copy(name = name)
        else existing.add(FamilyGroup(code = code, name = name))
        _familyGroups.value = existing
    }

    /** Reconstruye la lista global de miembros (todos menos el propio usuario) */
    private fun rebuildAllMembers() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val all = _familyGroups.value
            .flatMap { it.members }
            .filter { it.id != uid }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
        _familyMembers.value = all
    }

    fun loadFamilyMembers(code: String = _myFamilyCode.value) {
        subscribeToFamily(code)
    }

    fun logout() {
        firebaseAuth.signOut()
        familyListeners.values.forEach { it.remove() }
        familyListeners.clear()
        _myFamilyCode.value = ""
        _familyName.value   = ""
        _familyGroups.value = emptyList()
        _familyMembers.value = emptyList()
        _familyMessage.value = null
        _phone.value = ""; _userName.value = ""; _age.value = ""
        _role.value = "familiar"; _bloodType.value = ""; _allergies.value = ""
        _conditions.value = ""; _emergencyInfo.value = ""
        _emergencyContactName.value = ""; _emergencyContactPhone.value = ""
        _authState.value = AuthState.Unauthenticated
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
