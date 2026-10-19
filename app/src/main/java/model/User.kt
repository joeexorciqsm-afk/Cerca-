package com.cerca.app.model

data class User(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val phone: String = "",
    // Código de la familia a la que pertenece el usuario (vinculación familiar - F01)
    val familyCode: String = ""
)
