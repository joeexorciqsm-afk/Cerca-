package com.cerca.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.cerca.app.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ContactsViewModel : ViewModel() {
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val _contacts = MutableStateFlow<List<User>>(emptyList())
    val contacts: StateFlow<List<User>> = _contacts

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    /**
     * Carga los contactos: los miembros de la misma familia que el usuario actual
     * (los que comparten su mismo código de familia), excluyéndose a sí mismo.
     */
    fun loadContacts() {
        val currentUid = firebaseAuth.currentUser?.uid ?: return
        _loading.value = true

        // Primero obtenemos el código de familia del usuario actual.
        firestore.collection("users").document(currentUid)
            .get()
            .addOnSuccessListener { doc ->
                val familyCode = doc.getString("familyCode") ?: ""
                if (familyCode.isBlank()) {
                    _contacts.value = emptyList()
                    _loading.value = false
                    return@addOnSuccessListener
                }

                // Luego traemos a todos los de esa misma familia.
                firestore.collection("users")
                    .whereEqualTo("familyCode", familyCode)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        _contacts.value = snapshot.documents
                            .mapNotNull { it.toObject(User::class.java) }
                            .filter { it.id != currentUid } // sin incluirme a mí mismo
                            .sortedBy { it.name.lowercase() }
                        _loading.value = false
                    }
                    .addOnFailureListener { exception ->
                        Log.w("ContactsViewModel", "Error al cargar contactos: ${exception.message}")
                        _loading.value = false
                    }
            }
            .addOnFailureListener { exception ->
                Log.w("ContactsViewModel", "Error al leer el perfil: ${exception.message}")
                _loading.value = false
            }
    }
}
