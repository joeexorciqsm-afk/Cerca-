package com.cerca.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cerca.app.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth

// Sub-pantallas del Perfil.
private enum class ProfileTab { MENU, PERSONAL, FAMILY }

@Composable
fun HomeScreen(
    viewModel: AuthViewModel = viewModel(),
    onLogout: () -> Unit = {}
) {
    var screen by remember { mutableStateOf(ProfileTab.MENU) }

    when (screen) {
        ProfileTab.PERSONAL -> PersonalInfoScreen(
            viewModel = viewModel,
            onBack = { screen = ProfileTab.MENU }
        )
        ProfileTab.FAMILY -> FamilyScreen(
            viewModel = viewModel,
            onBack = { screen = ProfileTab.MENU }
        )
        ProfileTab.MENU -> ProfileMenu(
            viewModel = viewModel,
            onOpenPersonal = { screen = ProfileTab.PERSONAL },
            onOpenFamily = { screen = ProfileTab.FAMILY },
            onLogout = onLogout
        )
    }
}

@Composable
private fun ProfileMenu(
    viewModel: AuthViewModel,
    onOpenPersonal: () -> Unit,
    onOpenFamily: () -> Unit,
    onLogout: () -> Unit
) {
    val email = FirebaseAuth.getInstance().currentUser?.email ?: ""
    val userName by viewModel.userName.collectAsState()
    val familyName by viewModel.familyName.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (userName.isNotBlank()) "Hola, $userName" else "Perfil",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        if (email.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Opción: Información personal
        MenuCard(
            emoji = "👤",
            title = "Información personal",
            subtitle = "Nombre, edad y teléfono",
            onClick = onOpenPersonal
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Opción: Mi familia
        MenuCard(
            emoji = "👨‍👩‍👧‍👦",
            title = "Mi familia",
            subtitle = if (familyName.isNotBlank()) familyName else "Código, miembros y vinculación",
            onClick = onOpenFamily
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Cerrar Sesión")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MenuCard(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 36.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(text = "›", fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
