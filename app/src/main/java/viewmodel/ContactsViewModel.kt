package com.cerca.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.cerca.app.model.Contact
import com.cerca.app.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ContactsViewModel : ViewModel() {
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    // Familiares vinculados (mismo código de familia).
    private val _familyContacts = MutableStateFlow<List<User>>(emptyList())
    val familyContacts: StateFlow<List<User>> = _familyContacts

    // Contactos agregados manualmente por el usuario (nombre + teléfono).
    private val _myContacts = MutableStateFlow<List<Contact>>(emptyList())
    val myContacts: StateFlow<List<Contact>> = _myContacts

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    // Evita recargar desde Firestore cada vez que se entra a la pestaña
    // (ahorra lecturas, datos y memoria en dispositivos de baja RAM).
    private var alreadyLoaded = false

    /** Carga los contactos. Usa caché en memoria salvo que se fuerce la recarga. */
    fun loadContacts(forceReload: Boolean = false) {
        if (alreadyLoaded && !forceReload) return
        val currentUid = firebaseAuth.currentUser?.uid ?: return
        alreadyLoaded = true
        _loading.value = true

        // 1) Familiares (mismo familyCode).
        firestore.collection("users").document(currentUid)
            .get()
            .addOnSuccessListener { doc ->
                val familyCode = doc.getString("familyCode") ?: ""
                if (familyCode.isBlank()) {
                    _familyContacts.value = emptyList()
                    _loading.value = false
                } else {
                    firestore.collection("users")
                        .whereEqualTo("familyCode", familyCode)
                        .get()
                        .addOnSuccessListener { snapshot ->
                            _familyContacts.value = snapshot.documents
                                .mapNotNull { it.toObject(User::class.java) }
                                .filter { it.id != currentUid } // sin incluirme a mí mismo
                                .sortedBy { it.name.lowercase() }
                            _loading.value = false
                        }
                        .addOnFailureListener { exception ->
                            Log.w("ContactsViewModel", "Error al cargar familiares: ${exception.message}")
                            _loading.value = false
                        }
                }
            }
            .addOnFailureListener { exception ->
                Log.w("ContactsViewModel", "Error al leer el perfil: ${exception.message}")
                _loading.value = false
            }

        // 2) Contactos manuales del usuario.
        loadMyContacts(currentUid)
    }

    private fun loadMyContacts(uid: String) {
        firestore.collection("users").document(uid).collection("contacts")
            .get()
            .addOnSuccessListener { snapshot ->
                _myContacts.value = snapshot.documents
                    .mapNotNull { it.toObject(Contact::class.java) }
                    .sortedBy { it.name.lowercase() }
            }
            .addOnFailureListener { exception ->
                Log.w("ContactsViewModel", "Error al cargar contactos: ${exception.message}")
            }
    }

    /** Agrega un contacto manual (nombre + teléfono) para el usuario actual. */
    fun addContact(name: String, phone: String) {
        val uid = firebaseAuth.currentUser?.uid
        val cleanName = name.trim()
        val cleanPhone = phone.trim()

        if (uid == null) {
            _message.value = "Debes iniciar sesión"
            return
        }
        if (cleanName.isBlank() || cleanPhone.isBlank()) {
            _message.value = "Escribe el nombre y el teléfono"
            return
        }

        val ref = firestore.collection("users").document(uid).collection("contacts").document()
        val contact = Contact(id = ref.id, name = cleanName, phone = cleanPhone)
        ref.set(contact)
            .addOnSuccessListener {
                _message.value = "Contacto agregado"
                loadMyContacts(uid)
            }
            .addOnFailureListener { exception ->
                _message.value = exception.message ?: "No se pudo agregar el contacto"
            }
    }

    /** Edita un contacto manual existente (cambia nombre y/o teléfono). */
    fun updateContact(contactId: String, name: String, phone: String) {
        val uid = firebaseAuth.currentUser?.uid
        val cleanName = name.trim()
        val cleanPhone = phone.trim()

        if (uid == null) {
            _message.value = "Debes iniciar sesión"
            return
        }
        if (contactId.isBlank()) return
        if (cleanName.isBlank() || cleanPhone.isBlank()) {
            _message.value = "Escribe el nombre y el teléfono"
            return
        }

        firestore.collection("users").document(uid).collection("contacts")
            .document(contactId)
            .set(Contact(id = contactId, name = cleanName, phone = cleanPhone))
            .addOnSuccessListener {
                _message.value = "Contacto actualizado"
                loadMyContacts(uid)
            }
            .addOnFailureListener { exception ->
                _message.value = exception.message ?: "No se pudo actualizar el contacto"
            }
    }

    /**
     * Busca un contacto manual por nombre (coincidencia aproximada) y, si existe,
     * actualiza su teléfono. Útil para comandos de voz.
     * Devuelve true si lo encontró y actualizó.
     */
    fun updateContactByName(name: String, newPhone: String): Boolean {
        val uid = firebaseAuth.currentUser?.uid ?: return false
        val target = name.trim().lowercase()
        val match = _myContacts.value.firstOrNull {
            val n = it.name.lowercase()
            n == target || n.startsWith(target) || n.contains(target)
        } ?: return false

        updateContact(match.id, match.name, newPhone)
        return true
    }

    /**
     * Busca un contacto manual por nombre y lo elimina. Útil para comandos de voz.
     * Devuelve el nombre real del contacto eliminado, o null si no lo encontró.
     */
    fun deleteContactByName(name: String): String? {
        val target = name.trim().lowercase()
        val match = _myContacts.value.firstOrNull {
            val n = it.name.lowercase()
            n == target || n.startsWith(target) || n.contains(target)
        } ?: return null

        deleteContact(match.id)
        return match.name
    }

    /** Elimina un contacto manual del usuario actual. */
    fun deleteContact(contactId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (contactId.isBlank()) return

        firestore.collection("users").document(uid).collection("contacts")
            .document(contactId)
            .delete()
            .addOnSuccessListener {
                _message.value = "Contacto eliminado"
                loadMyContacts(uid)
            }
            .addOnFailureListener { exception ->
                _message.value = exception.message ?: "No se pudo eliminar el contacto"
            }
    }

    fun clearMessage() {
        _message.value = null
    }
}
