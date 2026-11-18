package com.cerca.app.model

data class Medication(
    val id: String = "",
    val name: String = "",
    val dose: String = "",
    val hour: Int = 8,
    val minute: Int = 0,
    val days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val active: Boolean = true,
    val notes: String = "",
    val isPublic: Boolean = true,   // público por defecto
    val ownerId: String = "",
    val ownerName: String = "",
    val familyCode: String = ""     // para notificar a toda la familia
)
