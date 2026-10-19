package com.cerca.app.model

/** Contacto manual que el usuario agrega (nombre + teléfono) para poder llamar. */
data class Contact(
    val id: String = "",
    val name: String = "",
    val phone: String = ""
)
