package com.cerca.app.model

data class FamilyGroup(
    val code: String = "",
    val name: String = "",
    val members: List<User> = emptyList()
)
