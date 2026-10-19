package com.cerca.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cerca.app.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth

@Composable
fun HomeScreen(
    viewModel: AuthViewModel = viewModel(),
    onLogout: () -> Unit = {}
) {
    val email = FirebaseAuth.getInstance().currentUser?.email ?: ""
    val familyCode by viewModel.familyCode.collectAsState()
    val familyMessage by viewModel.familyMessage.collectAsState()

    var codeInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "¡Bienvenido a Cerca!",
            style = MaterialTheme.typography.headlineMedium
        )

        if (email.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Código de familia del usuario (para compartir con sus familiares).
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Tu código de familia",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (familyCode.isNotBlank()) familyCode else "—",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Compártelo con tu familia para que se vinculen",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Unirse a una familia con el código de otro miembro.
        Text(
            text = "Unirse a una familia",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = codeInput,
            onValueChange = { codeInput = it },
            label = { Text("Código de familia") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (familyMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = familyMessage!!,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.joinFamily(codeInput) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Vincular")
        }

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar Sesión")
        }
    }
}
