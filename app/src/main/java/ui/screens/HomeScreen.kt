package com.cerca.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    val familyName by viewModel.familyName.collectAsState()
    val familyMessage by viewModel.familyMessage.collectAsState()
    val phone by viewModel.phone.collectAsState()

    var codeInput by remember { mutableStateOf("") }
    // Sincronizamos los campos editables con los valores guardados.
    var phoneInput by remember(phone) { mutableStateOf(phone) }
    var familyNameInput by remember(familyName) { mutableStateOf(familyName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

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
                // Nombre personalizado del grupo familiar (si tiene).
                Text(
                    text = if (familyName.isNotBlank()) familyName else "Mi familia",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Código de familia",
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

        // Nombre personalizado del grupo familiar (se puede cambiar).
        Text(
            text = "Nombre de la familia",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = familyNameInput,
            onValueChange = { familyNameInput = it },
            label = { Text("Ej: Familia Ramírez") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.saveFamilyName(familyNameInput) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Guardar nombre")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Teléfono del usuario (para que la familia pueda llamarle).
        Text(
            text = "Mi teléfono",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = phoneInput,
            onValueChange = { phoneInput = it },
            label = { Text("Número de teléfono") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.savePhone(phoneInput) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Guardar teléfono")
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
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.joinFamily(codeInput) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Vincular")
        }

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
