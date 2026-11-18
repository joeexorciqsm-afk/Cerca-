package com.cerca.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.cerca.app.model.Contact
import com.cerca.app.model.PhoneContact
import com.cerca.app.model.User
import com.cerca.app.util.readPhoneContacts
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val _familyContacts = MutableStateFlow<List<User>>(emptyList())
    val familyContacts: StateFlow<List<User>> = _familyContacts

    private val _myContacts = MutableStateFlow<List<Contact>>(emptyList())
    val myContacts: StateFlow<List<Contact>> = _myContacts

    private val _phoneContacts = MutableStateFlow<List<PhoneContact>>(emptyList())
    val phoneContacts: StateFlow<List<PhoneContact>> = _phoneContacts

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private var userListener:   ListenerRegistration? = null
    private var familyListener: ListenerRegistration? = null
    private var listenedUid:    String? = null
    private var listenedCode:   String? = null
    private var hidden:         Set<String> = emptySet()
    private var rawMembers:     List<User> = emptyList()

    fun loadContacts(forceReload: Boolean = false) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (uid != listenedUid) {
            stopListening()
            listenedUid = uid
            _loading.value = true
            listenToFamily(uid)
            loadMyContacts(uid)
        } else if (forceReload) {
            loadMyContacts(uid)
        }
    }

    private fun listenToFamily(uid: String) {
        userListener = firestore.collection("users").document(uid)
            .addSnapshotListener { doc, error ->
                if (error != null) {
                    Log.w("ContactsViewModel", "Error al leer el perfil: ${error.message}")
                    _loading.value = false
                    return@addSnapshotListener
                }
                // Leer familyCodes (array multi-familia) o fallback a familyCode (string legacy)
                @Suppress("UNCHECKED_CAST")
                val codes = (doc?.get("familyCodes") as? List<String>)
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    ?: doc?.getString("familyCode")?.let { if (it.isNotBlank()) listOf(it) else emptyList() }
                    ?: emptyList()

                hidden = (doc?.get("hiddenMembers") as? List<*>)
                    ?.filterIsInstance<String>()?.toSet() ?: emptySet()

                if (codes.isEmpty()) {
                    familyListener?.remove(); familyListener = null
                    listenedCode = null; rawMembers = emptyList()
                    publishFamily(uid); _loading.value = false
                } else {
                    val codeKey = codes.sorted().joinToString(",")
                    if (codeKey != listenedCode) {
                        listenedCode = codeKey
                        listenToMembersMulti(uid, codes)
                    } else {
                        publishFamily(uid)
                    }
                }
            }
    }

    private fun listenToMembersMulti(uid: String, codes: List<String>) {
        familyListener?.remove()
        // Firestore no permite whereIn con más de 10 valores, pero en la práctica
        // nadie tendrá más de 10 familias. Usamos whereArrayContainsAny sobre familyCodes.
        familyListener = firestore.collection("users")
            .whereArrayContainsAny("familyCodes", codes.take(10))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ContactsViewModel", "Error al cargar familiares: ${error.message}")
                    _loading.value = false
                    return@addSnapshotListener
                }
                rawMembers = snapshot?.documents.orEmpty().mapNotNull { d ->
                    d.toObject(User::class.java)?.let { u ->
                        u.copy(id = d.id,
                            name = u.name.ifBlank { u.email.substringBefore("@") })
                    }
                }.distinctBy { it.id }
                publishFamily(uid)
                _loading.value = false
            }
    }

    private fun publishFamily(uid: String) {
        _familyContacts.value = rawMembers
            .filter { it.id != uid && it.id !in hidden }
            .sortedBy { it.name.lowercase() }
    }

    private fun stopListening() {
        userListener?.remove()
        familyListener?.remove()
        userListener = null
        familyListener = null
        listenedUid = null
        listenedCode = null
        rawMembers = emptyList()
        hidden = emptySet()
        _familyContacts.value = emptyList()
        _myContacts.value = emptyList()
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }

    private fun loadMyContacts(uid: String) {
        firestore.collection("users").document(uid).collection("contacts")
            .get()
            .addOnSuccessListener { snapshot ->
                _myContacts.value = snapshot.documents
                    .mapNotNull { d -> d.toObject(Contact::class.java)?.copy(id = d.id) }
                    .sortedBy { it.name.lowercase() }
            }
            .addOnFailureListener { exception ->
                Log.w("ContactsViewModel", "Error al cargar contactos: ${exception.message}")
            }
    }

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

    fun deleteContactByName(name: String): String? {
        val target = name.trim().lowercase()
        fun coincide(n: String) = n.lowercase().let { it == target || it.startsWith(target) || it.contains(target) }

        _myContacts.value.firstOrNull { coincide(it.name) }?.let {
            deleteContact(it.id)
            return it.name
        }
        _familyContacts.value.firstOrNull { coincide(it.name) }?.let {
            hideFamilyMember(it.id)
            return it.name
        }
        return null
    }

    fun hideFamilyMember(memberId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (memberId.isBlank()) return

        firestore.collection("users").document(uid)
            .set(mapOf("hiddenMembers" to FieldValue.arrayUnion(memberId)), SetOptions.merge())
            .addOnSuccessListener {
                _familyContacts.value = _familyContacts.value.filter { it.id != memberId }
                _message.value = "Contacto quitado"
            }
            .addOnFailureListener { exception ->
                _message.value = exception.message ?: "No se pudo quitar el contacto"
            }
    }

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

    // ── Contactos del teléfono ────────────────────────────────────────────────

    /** Carga los contactos de la agenda. Llama esto después de que el usuario otorgue READ_CONTACTS. */
    fun loadPhoneContacts() {
        try {
            _phoneContacts.value = readPhoneContacts(getApplication())
        } catch (e: Exception) {
            Log.w("ContactsVM", "Error leyendo agenda: ${e.message}")
        }
    }

    /** Busca en la agenda por nombre o número (para los comandos de voz) */
    fun findPhoneContact(query: String): PhoneContact? {
        val q = query.trim().lowercase()
        return _phoneContacts.value.firstOrNull {
            val n = it.name.lowercase()
            n == q || n.startsWith(q) || n.contains(q)
        }
    }
}
