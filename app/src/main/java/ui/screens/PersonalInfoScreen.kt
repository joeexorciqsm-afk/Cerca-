package com.cerca.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cerca.app.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth

@Composable
fun PersonalInfoScreen(
    viewModel: AuthViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val email = FirebaseAuth.getInstance().currentUser?.email ?: ""
    val userName by viewModel.userName.collectAsState()
    val age by viewModel.age.collectAsState()
    val phone by viewModel.phone.collectAsState()
    val message by viewModel.familyMessage.collectAsState()

    var nameInput by remember(userName) { mutableStateOf(userName) }
    var ageInput by remember(age) { mutableStateOf(age) }
    var phoneInput by remember(phone) { mutableStateOf(phone) }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearFamilyMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.height(48.dp)) {
                Text("← Volver", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Información personal",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        if (message != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message!!,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("Correo", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = email,
            onValueChange = {},
            enabled = false,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("Nombre", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            placeholder = { Text("Tu nombre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("Edad", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = ageInput,
            onValueChange = { new -> ageInput = new.filter { it.isDigit() }.take(3) },
            placeholder = { Text("Tu edad") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("Teléfono", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = phoneInput,
            onValueChange = { phoneInput = it },
            placeholder = { Text("Número de teléfono") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.savePersonalInfo(nameInput, ageInput, phoneInput) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Guardar cambios")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
