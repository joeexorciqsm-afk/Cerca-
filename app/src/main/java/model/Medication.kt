package com.cerca.app.model

data class Medication(
    val id: String = "",
    val name: String = "",
    val dose: String = "",
    val hour: Int = 8,
    val minute: Int = 0,
    val days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // Calendar.DAY_OF_WEEK: 1=Dom, 2=Lun...7=Sáb
    val active: Boolean = true,
    val notes: String = "",
    // Si es true, los miembros de la familia pueden verlo en el dashboard
    val isPublic: Boolean = false,
    // UID del dueño (para medicamentos visibles en la familia)
    val ownerId: String = "",
    val ownerName: String = ""
)
