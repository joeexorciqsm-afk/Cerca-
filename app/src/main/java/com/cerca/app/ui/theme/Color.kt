package com.cerca.app.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta de Cerca: tonos sobrios y con buen contraste para accesibilidad.
// Primario: verde azulado (teal), que combina con el logo de familia.
val CercaPrimary = Color(0xFF2F6F6B)        // teal oscuro
val CercaPrimaryDark = Color(0xFF7FC6C0)    // teal claro (modo oscuro)
val CercaSecondary = Color(0xFF4A6572)      // gris azulado
val CercaSecondaryDark = Color(0xFFB0C4CE)

// Fondos y superficies claros y limpios.
val CercaBackground = Color(0xFFF6F8F8)
val CercaSurface = Color(0xFFFFFFFF)
val CercaOnLight = Color(0xFF1B2524)        // texto oscuro, alto contraste

// Mantener compatibilidad por si algún archivo aún referencia estos nombres.
val Purple80 = CercaPrimaryDark
val PurpleGrey80 = CercaSecondaryDark
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = CercaPrimary
val PurpleGrey40 = CercaSecondary
val Pink40 = Color(0xFF7D5260)
