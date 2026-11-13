package com.cerca.app.util

import android.content.Context
import android.provider.ContactsContract
import com.cerca.app.model.PhoneContact

/**
 * Lee todos los contactos de la agenda del teléfono usando ContactsContract.
 * Funciona en todos los Android sin importar la marca o cuenta (Google, Samsung, SIM, etc.)
 */
fun readPhoneContacts(context: Context): List<PhoneContact> {
    val contactMap = mutableMapOf<String, Pair<String, MutableList<String>>>()

    val uri     = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    val proj    = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    val sort    = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

    try {
        context.contentResolver.query(uri, proj, null, null, sort)?.use { cursor ->
            val idIdx    = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx  = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val id    = cursor.getString(idIdx)   ?: continue
                val name  = cursor.getString(nameIdx)  ?: continue
                val phone = cursor.getString(phoneIdx) ?: continue

                // Limpiar número: quitar espacios y guiones para normalizar
                val cleanPhone = phone.replace("\\s".toRegex(), "").replace("-", "")

                val entry = contactMap.getOrPut(id) { name to mutableListOf() }
                if (cleanPhone.isNotBlank() && cleanPhone !in entry.second)
                    entry.second.add(cleanPhone)
            }
        }
    } catch (_: Exception) {}

    return contactMap.map { (id, pair) ->
        PhoneContact(id = id, name = pair.first, phones = pair.second)
    }.filter { it.phones.isNotEmpty() }
     .sortedBy { it.name.lowercase() }
}
