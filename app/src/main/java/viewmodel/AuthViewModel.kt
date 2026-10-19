package com.cerca.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.cerca.app.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AuthViewModel : ViewModel() {
    private val firebaseAuth = FirebaseAuth.getInstance()
    // Firestore se crea de forma perezosa para que no bloquee el arranque ni el auth.
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState

    // Código de familia del usuario actual (para mostrarlo en la Home).
    private val _familyCode = MutableStateFlow("")
    val familyCode: StateFlow<String> = _familyCode

    // Mensaje de la acción de vincular familia (éxito o error), para mostrarlo en la UI.
    private val _familyMessage = MutableStateFlow<String?>(null)
    val familyMessage: StateFlow<String?> = _familyMessage

    // Teléfono del usuario actual (para que la familia pueda llamarle).
    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone

    init {
        // Si ya hay sesion iniciada, entramos directo
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
                // Generamos un código de familia para el nuevo usuario (F01: generar código).
                val code = generateFamilyCode()
                _familyCode.value = code

                // La cuenta ya existe en Authentication: entramos a la app enseguida
                // sin esperar a Firestore (si Firestore falla, no bloquea el registro).
                Log.d("AuthViewModel", "signup: cuenta creada (uid=$uid), entrando a la app")
                _authState.value = AuthState.Authenticated

                // Guardamos el perfil en Firestore en segundo plano, envuelto en try/catch
                // para que nada de Firestore pueda bloquear ni afectar el flujo de auth.
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

    /** Une al usuario actual a la familia que tenga el código indicado (F01: vincular familia). */
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

        // Verificamos que el código exista (que algún usuario lo tenga) antes de unirse.
        firestore.collection("users")
            .whereEqualTo("familyCode", cleanCode)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    _familyMessage.value = "No existe una familia con ese código"
                } else {
                    firestore.collection("users").document(uid)
                        .update("familyCode", cleanCode)
                        .addOnSuccessListener {
                            _familyCode.value = cleanCode
                            _familyMessage.value = "¡Te uniste a la familia!"
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

    /**
     * Carga el código de familia del usuario actual desde Firestore.
     * Si el usuario no tiene código todavía (por ejemplo, cuentas creadas antes
     * de esta funcionalidad), le genera uno y lo guarda.
     */
    private fun loadFamilyCode() {
        val user = firebaseAuth.currentUser ?: return
        val uid = user.uid
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                _phone.value = doc.getString("phone") ?: ""
                val code = doc.getString("familyCode")
                if (!code.isNullOrBlank()) {
                    _familyCode.value = code
                } else {
                    // No tiene código: le creamos uno y lo guardamos (auto-reparación).
                    assignFamilyCodeIfMissing(uid, user.email ?: "", doc.getString("name") ?: "")
                }
            }
            .addOnFailureListener { exception ->
                Log.w("AuthViewModel", "No se pudo cargar el código de familia: ${exception.message}")
            }
    }

    /** Crea un código de familia para un usuario que no lo tiene y lo guarda en Firestore. */
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

    /** Limpia el mensaje de vinculación tras mostrarlo. */
    fun clearFamilyMessage() {
        _familyMessage.value = null
    }

    /** Guarda el teléfono del usuario para que su familia pueda llamarle. */
    fun savePhone(newPhone: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val clean = newPhone.trim()
        _phone.value = clean
        firestore.collection("users").document(uid)
            .update("phone", clean)
            .addOnSuccessListener {
                _familyMessage.value = "Teléfono guardado"
            }
            .addOnFailureListener { exception ->
                _familyMessage.value = exception.message ?: "No se pudo guardar el teléfono"
            }
    }

    fun logout() {
        firebaseAuth.signOut()
        _familyCode.value = ""
        _familyMessage.value = null
        _phone.value = ""
        _authState.value = AuthState.Unauthenticated
    }

    /** Genera un código de familia aleatorio de 6 caracteres (letras y números). */
    private fun generateFamilyCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // sin O/0/I/1 para evitar confusión
        return (1..6).map { chars.random() }.joinToString("")
    }
}

sealed class AuthState {
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
}
