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

    init {
        // Si ya hay sesion iniciada, entramos directo
        if (firebaseAuth.currentUser != null) {
            _authState.value = AuthState.Authenticated
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
                Log.d("AuthViewModel", "signup: cuenta creada (uid=$uid), entrando a la app")

                // La cuenta ya existe en Authentication: entramos a la app enseguida
                // sin esperar a Firestore (si Firestore falla, no bloquea el registro).
                _authState.value = AuthState.Authenticated

                // Guardamos el perfil en Firestore en segundo plano. Lo envolvemos en
                // try/catch para que NADA relacionado con Firestore pueda bloquear
                // ni afectar el flujo de autenticación.
                try {
                    val user = User(id = uid, email = email.trim(), name = name.trim())
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

    fun logout() {
        firebaseAuth.signOut()
        _authState.value = AuthState.Unauthenticated
    }
}

sealed class AuthState {
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
}
