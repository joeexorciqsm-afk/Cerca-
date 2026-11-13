package com.cerca.app.model

data class EmergencyAlert(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val familyCode: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val hasLocation: Boolean = false,
    val message: String = "¡Necesito ayuda!",
    val timestamp: Long = 0L,
    val resolved: Boolean = false
)
