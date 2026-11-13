package com.cerca.app.model

data class PhoneContact(
    val id: String = "",
    val name: String = "",
    val phones: List<String> = emptyList()
) {
    val primaryPhone: String get() = phones.firstOrNull() ?: ""
}
