package com.cerca.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cerca.app.model.User
import com.cerca.app.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth

@Composable
fun FamilyScreen(
    viewModel: AuthViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val familyCode by viewModel.familyCode.collectAsState()
    val familyName by viewModel.familyName.collectAsState()
    val members by viewModel.familyMembers.collectAsState()
    val message by viewModel.familyMessage.collectAsState()

    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var codeInput by remember { mutableStateOf("") }
    var familyNameInput by remember(familyName) { mutableStateOf(familyName) }
    var showLeaveDialog by remember { mutableStateOf(false) }

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.height(48.dp)) {
                Text("← Volver", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Mi familia",
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

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (familyName.isNotBlank()) familyName else "Mi familia",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Código de familia", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (familyCode.isNotBlank()) familyCode else "—",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Compártelo para que tu familia se vincule",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Nombre de la familia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = familyNameInput,
                    onValueChange = { familyNameInput = it },
                    placeholder = { Text("Ej: Familia Ramírez") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.saveFamilyName(familyNameInput) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("Guardar nombre")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Miembros (${members.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (members.isEmpty()) {
                    Text(
                        "Aún no hay miembros. Comparte tu código para que se unan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    members.forEach { member ->
                        MemberRow(member = member, isMe = member.id == currentUid)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        val inFamily = members.any { it.id != currentUid }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (!inFamily) {
                    Text(
                        "Unirse a una familia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it },
                        placeholder = { Text("Código de familia") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.joinFamily(codeInput) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Vincular")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                OutlinedButton(
                    onClick = { showLeaveDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("Salir de la familia")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Salir de la familia") },
            text = {
                Text(
                    "Dejarás de ver a los miembros de esta familia y obtendrás un código " +
                        "nuevo. ¿Continuar?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.leaveFamily()
                        showLeaveDialog = false
                    }
                ) { Text("Salir") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun MemberRow(member: User, isMe: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = member.name.trim().firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.name.ifBlank { "Sin nombre" } + if (isMe) "  (tú)" else "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = member.phone.ifBlank { "Sin teléfono" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
