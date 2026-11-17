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
    val resolved: Boolean = false,
    // Tipo de alerta: "sos" | "911" | "medical" | "fall" | "driving"
    val type: String = "sos",
    // Info médica adjunta al momento de la alerta
    val bloodType: String = "",
    val conditions: String = "",
    val allergies: String = ""
)
