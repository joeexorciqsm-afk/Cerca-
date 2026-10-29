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

    private val _familyCode = MutableStateFlow("")
    val familyCode: StateFlow<String> = _familyCode

    private val _familyMessage = MutableStateFlow<String?>(null)
    val familyMessage: StateFlow<String?> = _familyMessage

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone

    private val _familyName = MutableStateFlow("")
    val familyName: StateFlow<String> = _familyName

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName

    private val _age = MutableStateFlow("")
    val age: StateFlow<String> = _age

    private val _familyMembers = MutableStateFlow<List<User>>(emptyList())
    val familyMembers: StateFlow<List<User>> = _familyMembers

    init {
        if (firebaseAuth.currentUser != null) {
            _authState.value = AuthState.Authenticated
            loadFamilyCode()
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Completa el correo y la contraseña")
            return
        }

        _authState.value = AuthState.Loading
        Log.d("AuthViewModel", "login: iniciando sesión para $email")
        firebaseAuth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener {
                Log.d("AuthViewModel", "login: éxito")
                _authState.value = AuthState.Authenticated
                loadFamilyCode()
            }
            .addOnFailureListener { exception ->
                Log.w("AuthViewModel", "login: error - ${exception.message}")
                _authState.value = AuthState.Error(exception.message ?: "Error al iniciar sesión")
            }
    }

    fun signup(email: String, password: String, name: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Completa todos los campos")
            return
        }
        if (password.length < 6) {
            _authState.value = AuthState.Error("La contraseña debe tener al menos 6 caracteres")
            return
        }

        _authState.value = AuthState.Loading
        Log.d("AuthViewModel", "signup: creando cuenta para $email")
        firebaseAuth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: ""
                val code = generateFamilyCode()
                _familyCode.value = code

                Log.d("AuthViewModel", "signup: cuenta creada (uid=$uid), entrando a la app")
                _authState.value = AuthState.Authenticated

                try {
                    val user = User(
                        id = uid,
                        email = email.trim(),
                        name = name.trim(),
                        familyCode = code
                    )
                    firestore.collection("users").document(uid)
                        .set(user)
                        .addOnSuccessListener {
                            Log.d("AuthViewModel", "signup: perfil guardado en Firestore")
                        }
                        .addOnFailureListener { exception ->
                            Log.w("AuthViewModel", "signup: no se pudo guardar el perfil - ${exception.message}")
                        }
                } catch (e: Exception) {
                    Log.w("AuthViewModel", "signup: error al acceder a Firestore - ${e.message}")
                }
            }
            .addOnFailureListener { exception ->
                Log.w("AuthViewModel", "signup: error - ${exception.message}")
                _authState.value = AuthState.Error(exception.message ?: "Error al registrarse")
            }
    }

    fun joinFamily(code: String) {
        val uid = firebaseAuth.currentUser?.uid
        val cleanCode = code.trim().uppercase()

        if (uid == null) {
            _familyMessage.value = "Debes iniciar sesión primero"
            return
        }
        if (cleanCode.isBlank()) {
            _familyMessage.value = "Ingresa un código de familia"
            return
        }

        firestore.collection("users")
            .whereEqualTo("familyCode", cleanCode)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    _familyMessage.value = "No existe una familia con ese código"
                } else {
                    firestore.collection("users").document(uid)
                        .set(mapOf("familyCode" to cleanCode), SetOptions.merge())
                        .addOnSuccessListener {
                            _familyCode.value = cleanCode
                            _familyMessage.value = "¡Te uniste a la familia!"
                            loadFamilyName(cleanCode)
                            loadFamilyMembers(cleanCode)
                        }
                        .addOnFailureListener { exception ->
                            _familyMessage.value =
                                exception.message ?: "No se pudo unir a la familia"
                        }
                }
            }
            .addOnFailureListener { exception ->
                _familyMessage.value = exception.message ?: "Error al buscar la familia"
            }
    }

    private fun loadFamilyCode() {
        val user = firebaseAuth.currentUser ?: return
        val uid = user.uid
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                _phone.value = doc.getString("phone") ?: ""
                _userName.value = doc.getString("name") ?: ""
                _age.value = doc.getString("age") ?: ""
                val code = doc.getString("familyCode")
                if (!code.isNullOrBlank()) {
                    _familyCode.value = code
                    loadFamilyName(code)
                    loadFamilyMembers(code)
                } else {
                    assignFamilyCodeIfMissing(uid, user.email ?: "", doc.getString("name") ?: "")
                }
            }
            .addOnFailureListener { exception ->
                Log.w("AuthViewModel", "No se pudo cargar el código de familia: ${exception.message}")
            }
    }

    private fun assignFamilyCodeIfMissing(uid: String, email: String, name: String) {
        val code = generateFamilyCode()
        _familyCode.value = code
        val user = User(id = uid, email = email, name = name, familyCode = code)
        firestore.collection("users").document(uid)
            .set(user)
            .addOnSuccessListener {
                Log.d("AuthViewModel", "Código de familia creado para cuenta sin código")
            }
            .addOnFailureListener { exception ->
                Log.w("AuthViewModel", "No se pudo asignar el código de familia: ${exception.message}")
            }
    }

    fun leaveFamily() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val nuevoCodigo = generateFamilyCode()
        firestore.collection("users").document(uid)
            .set(mapOf("familyCode" to nuevoCodigo), SetOptions.merge())
            .addOnSuccessListener {
                _familyCode.value = nuevoCodigo
                _familyName.value = ""
                _familyMessage.value = "Saliste de la familia"
                loadFamilyMembers(nuevoCodigo)
            }
            .addOnFailureListener { exception ->
                _familyMessage.value = exception.message ?: "No se pudo salir de la familia"
            }
    }

    fun clearFamilyMessage() {
        _familyMessage.value = null
    }

    fun savePersonalInfo(name: String, age: String, phone: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val datos = mapOf(
            "name" to name.trim(),
            "age" to age.trim(),
            "phone" to phone.trim()
        )
        _userName.value = name.trim()
        _age.value = age.trim()
        _phone.value = phone.trim()
        firestore.collection("users").document(uid)
            .set(datos, SetOptions.merge())
            .addOnSuccessListener {
                _familyMessage.value = "Información guardada"
                if (_familyCode.value.isNotBlank()) loadFamilyMembers(_familyCode.value)
            }
            .addOnFailureListener { exception ->
                _familyMessage.value = exception.message ?: "No se pudo guardar la información"
            }
    }

    private fun loadFamilyMembers(code: String) {
        if (code.isBlank()) {
            _familyMembers.value = emptyList()
            return
        }
        firestore.collection("users")
            .whereEqualTo("familyCode", code)
            .get()
            .addOnSuccessListener { snapshot ->
                _familyMembers.value = snapshot.documents
                    .mapNotNull { d -> d.toObject(User::class.java)?.copy(id = d.id) }
                    .sortedBy { it.name.lowercase() }
            }
            .addOnFailureListener { exception ->
                Log.w("AuthViewModel", "No se pudieron cargar los miembros: ${exception.message}")
            }
    }

    fun savePhone(newPhone: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val clean = newPhone.trim()
        _phone.value = clean
        firestore.collection("users").document(uid)
            .set(mapOf("phone" to clean), SetOptions.merge())
            .addOnSuccessListener {
                _familyMessage.value = "Teléfono guardado"
            }
            .addOnFailureListener { exception ->
                _familyMessage.value = exception.message ?: "No se pudo guardar el teléfono"
            }
    }

    fun saveFamilyName(newName: String) {
        val code = _familyCode.value
        val clean = newName.trim()
        if (code.isBlank()) {
            _familyMessage.value = "Primero necesitas un código de familia"
            return
        }
        if (clean.isBlank()) {
            _familyMessage.value = "Escribe un nombre para la familia"
            return
        }
        _familyName.value = clean
        firestore.collection("families").document(code)
            .set(mapOf("name" to clean), SetOptions.merge())
            .addOnSuccessListener {
                _familyMessage.value = "Nombre de familia guardado"
            }
            .addOnFailureListener { exception ->
                _familyMessage.value = exception.message ?: "No se pudo guardar el nombre"
            }
    }

    private fun loadFamilyName(code: String) {
        if (code.isBlank()) return
        firestore.collection("families").document(code)
            .get()
            .addOnSuccessListener { doc ->
                _familyName.value = doc.getString("name") ?: ""
            }
            .addOnFailureListener { exception ->
                Log.w("AuthViewModel", "No se pudo cargar el nombre de familia: ${exception.message}")
            }
    }

    fun logout() {
        firebaseAuth.signOut()
        _familyCode.value = ""
        _familyName.value = ""
        _familyMessage.value = null
        _phone.value = ""
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
