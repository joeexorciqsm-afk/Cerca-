package com.cerca.app.model

data class User(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val phone: String = "",
    val age: String = "",
    val familyCode: String = "",
    // Rol en la familia: "admin" | "cuidador" | "paciente" | "familiar"
    val role: String = "familiar",
    // Info médica de emergencia
    val bloodType: String = "",           // Tipo de sangre: A+, B-, O+, etc.
    val allergies: String = "",           // Alergias conocidas
    val conditions: String = "",          // Padecimientos/enfermedades crónicas
    val emergencyInfo: String = "",       // Notas de emergencia adicionales
    val emergencyContactName: String = "",// Contacto de emergencia adicional
    val emergencyContactPhone: String = ""// Teléfono del contacto de emergencia
)
