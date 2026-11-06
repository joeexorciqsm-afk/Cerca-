package com.cerca.app.model

data class Medication(
    val id: String = "",
    val name: String = "",
    val dose: String = "",
    val hour: Int = 8,
    val minute: Int = 0,
    val days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // 1=Lun … 7=Dom (Calendar.DAY_OF_WEEK style: 2=Lun, 3=Mar, ...1=Dom)
    val active: Boolean = true,
    val notes: String = ""
)
