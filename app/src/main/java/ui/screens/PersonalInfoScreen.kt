package com.cerca.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cerca.app.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth

private val ROLES = listOf(
    "familiar"   to "👤 Familiar",
    "paciente"   to "🏥 Paciente",
    "cuidador"   to "🤝 Cuidador",
    "admin"      to "⭐ Administrador"
)

private val BLOOD_TYPES = listOf("A+","A−","B+","B−","AB+","AB−","O+","O−","No sé")

@Composable
fun PersonalInfoScreen(
    viewModel: AuthViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val email   = FirebaseAuth.getInstance().currentUser?.email ?: ""
    val userName  by viewModel.userName.collectAsState()
    val age       by viewModel.age.collectAsState()
    val phone     by viewModel.phone.collectAsState()
    val role      by viewModel.role.collectAsState()
    val message   by viewModel.familyMessage.collectAsState()

    // Info médica
    val bloodType    by viewModel.bloodType.collectAsState()
    val allergies    by viewModel.allergies.collectAsState()
    val conditions   by viewModel.conditions.collectAsState()
    val emergInfo    by viewModel.emergencyInfo.collectAsState()
    val ecName       by viewModel.emergencyContactName.collectAsState()
    val ecPhone      by viewModel.emergencyContactPhone.collectAsState()

    // Campos editables
    var nameInput  by remember(userName)  { mutableStateOf(userName) }
    var ageInput   by remember(age)       { mutableStateOf(age) }
    var phoneInput by remember(phone)     { mutableStateOf(phone) }
    var roleInput  by remember(role)      { mutableStateOf(role) }
    var bloodInput by remember(bloodType) { mutableStateOf(bloodType) }
    var allerInput by remember(allergies) { mutableStateOf(allergies) }
    var condInput  by remember(conditions){ mutableStateOf(conditions) }
    var emergInput by remember(emergInfo) { mutableStateOf(emergInfo) }
    var ecNameInput  by remember(ecName)  { mutableStateOf(ecName) }
    var ecPhoneInput by remember(ecPhone) { mutableStateOf(ecPhone) }

    // Sección activa
    var activeSection by remember { mutableStateOf(0) } // 0=Personal, 1=Médica, 2=Emergencia

    LaunchedEffect(message) {
        if (message != null) { kotlinx.coroutines.delay(2000); viewModel.clearFamilyMessage() }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        // ── Cabecera ──────────────────────────────────────────────────────────
        TextButton(onClick = onBack) {
            Text("← Volver", style = MaterialTheme.typography.titleMedium)
        }

        Text("Mi perfil", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(message!!, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(16.dp))

        // ── Tabs de sección ───────────────────────────────────────────────────
        TabRow(selectedTabIndex = activeSection) {
            Tab(selected = activeSection == 0, onClick = { activeSection = 0 },
                text = { Text("👤 Personal") })
            Tab(selected = activeSection == 1, onClick = { activeSection = 1 },
                text = { Text("🏥 Médico") })
            Tab(selected = activeSection == 2, onClick = { activeSection = 2 },
                text = { Text("🆘 Emergencia") })
        }

        Spacer(Modifier.height(20.dp))

        when (activeSection) {
            // ── SECCIÓN PERSONAL ─────────────────────────────────────────────
            0 -> {
                // Email (read-only)
                OutlinedTextField(value = email, onValueChange = {}, enabled = false,
                    label = { Text("Correo") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(value = nameInput, onValueChange = { nameInput = it },
                    label = { Text("Nombre") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = ageInput,
                    onValueChange = { ageInput = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Edad") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(value = phoneInput, onValueChange = { phoneInput = it },
                    label = { Text("Teléfono") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))

                // Rol
                Text("Mi rol en la familia", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    ROLES.forEach { (key, label) ->
                        FilterChip(
                            selected = roleInput == key,
                            onClick  = { roleInput = key },
                            label    = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        viewModel.savePersonalInfo(nameInput, ageInput, phoneInput)
                        viewModel.saveRole(roleInput)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Guardar", fontWeight = FontWeight.Bold) }
            }

            // ── SECCIÓN MÉDICA ───────────────────────────────────────────────
            1 -> {
                Text("Esta información se adjunta a tus alertas SOS para que los servicios de emergencias y tu familia la vean.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))

                // Tipo de sangre
                Text("Tipo de sangre", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    BLOOD_TYPES.forEach { bt ->
                        FilterChip(
                            selected = bloodInput == bt,
                            onClick  = { bloodInput = bt },
                            label    = { Text(bt, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = allerInput, onValueChange = { allerInput = it },
                    label = { Text("Alergias") },
                    placeholder = { Text("Penicilina, mariscos, látex…") },
                    minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = condInput, onValueChange = { condInput = it },
                    label = { Text("Padecimientos / enfermedades crónicas") },
                    placeholder = { Text("Diabetes tipo 2, hipertensión, asma…") },
                    minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        viewModel.saveMedicalInfo(
                            bloodInput, allerInput, condInput,
                            emergInput, ecNameInput, ecPhoneInput
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Guardar info médica", fontWeight = FontWeight.Bold) }
            }

            // ── SECCIÓN EMERGENCIA ───────────────────────────────────────────
            2 -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("🆘 Esta información se muestra a los familiares\ncuando envías una alerta de emergencia.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = emergInput, onValueChange = { emergInput = it },
                    label = { Text("Notas de emergencia") },
                    placeholder = { Text("Vivo solo, departamento 3B, llave debajo del tapete…") },
                    minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text("Contacto de emergencia adicional",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = ecNameInput, onValueChange = { ecNameInput = it },
                    label = { Text("Nombre del contacto") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = ecPhoneInput, onValueChange = { ecPhoneInput = it },
                    label = { Text("Teléfono del contacto") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        viewModel.saveMedicalInfo(
                            bloodInput, allerInput, condInput,
                            emergInput, ecNameInput, ecPhoneInput
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Guardar info de emergencia", fontWeight = FontWeight.Bold) }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
