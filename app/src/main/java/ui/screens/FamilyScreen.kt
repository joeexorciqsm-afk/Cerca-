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
import com.cerca.app.model.FamilyGroup
import com.cerca.app.model.User
import com.cerca.app.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth

@Composable
fun FamilyScreen(
    viewModel: AuthViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val myFamilyCode by viewModel.myFamilyCode.collectAsState()
    val familyGroups by viewModel.familyGroups.collectAsState()
    val message      by viewModel.familyMessage.collectAsState()
    val currentUid   = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    var joinCodeInput by remember { mutableStateOf("") }
    var showLeaveDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearFamilyMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── Cabecera ──────────────────────────────────────────────────────────
        TextButton(onClick = onBack) {
            Text("← Volver", style = MaterialTheme.typography.titleMedium)
        }

        Text("Mis familias", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)

        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(message!!, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(16.dp))

        // ── Mi código para invitar ────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Mi código para invitar", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (myFamilyCode.isNotBlank()) myFamilyCode else "—",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    letterSpacing = 4.sp
                )
                Spacer(Modifier.height(4.dp))
                Text("Compártelo para que te agreguen a su familia",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center)
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Unirse a una nueva familia ────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Unirse a otra familia", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = joinCodeInput,
                    onValueChange = { joinCodeInput = it.uppercase().take(6) },
                    placeholder = { Text("Código de 6 caracteres") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.joinFamily(joinCodeInput); joinCodeInput = "" },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = joinCodeInput.length == 6
                ) { Text("Unirme") }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Lista de familias ─────────────────────────────────────────────────
        Text(
            "Familias (${familyGroups.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))

        if (familyGroups.isEmpty()) {
            Text("Aún no perteneces a ninguna familia.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            familyGroups.forEach { group ->
                FamilyGroupCard(
                    group      = group,
                    isOwn      = group.code == myFamilyCode,
                    currentUid = currentUid,
                    onRename   = { newName -> viewModel.saveFamilyName(newName, group.code) },
                    onLeave    = { showLeaveDialog = group.code }
                )
                Spacer(Modifier.height(14.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    // ── Diálogo confirmar salida ──────────────────────────────────────────────
    showLeaveDialog?.let { code ->
        val group = familyGroups.firstOrNull { it.code == code }
        AlertDialog(
            onDismissRequest = { showLeaveDialog = null },
            title = { Text("Salir de la familia") },
            text  = {
                Text("¿Salir de \"${group?.name?.ifBlank { code } ?: code}\"? " +
                    "Dejarás de ver a sus miembros.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.leaveFamily(code); showLeaveDialog = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Salir") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = null }) { Text("Cancelar") }
            }
        )
    }
}

// ── Tarjeta de grupo familiar ─────────────────────────────────────────────────

@Composable
private fun FamilyGroupCard(
    group: FamilyGroup,
    isOwn: Boolean,
    currentUid: String,
    onRename: (String) -> Unit,
    onLeave: () -> Unit
) {
    var nameInput  by remember(group.name) { mutableStateOf(group.name) }
    var editMode   by remember { mutableStateOf(false) }
    var expanded   by remember { mutableStateOf(true) }

    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOwn)
                MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Nombre + badge + expandir
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            group.name.ifBlank { group.code },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isOwn) {
                            Spacer(Modifier.width(6.dp))
                            Surface(shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primary) {
                                Text("Mi familia", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Text("Código: ${group.code}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${group.members.size} miembro${if (group.members.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                IconButton(onClick = { editMode = !editMode }) {
                    Text("✏️", fontSize = 16.sp)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "▲" else "▼", fontSize = 14.sp)
                }
            }

            // Renombrar
            if (editMode) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Nombre de la familia") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onRename(nameInput); editMode = false },
                        modifier = Modifier.weight(1f)) { Text("Guardar") }
                    OutlinedButton(onClick = { editMode = false },
                        modifier = Modifier.weight(1f)) { Text("Cancelar") }
                }
            }

            // Miembros expandibles
            if (expanded && group.members.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                group.members.forEach { member ->
                    MemberRow(member = member, isMe = member.id == currentUid)
                    Spacer(Modifier.height(6.dp))
                }
            }

            // Salir (solo si no es la propia familia)
            if (!isOwn) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onLeave,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Salir de esta familia") }
            }
        }
    }
}

@Composable
private fun MemberRow(member: User, isMe: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(if (isMe) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.secondary),
            contentAlignment = Alignment.Center
        ) {
            Text(member.name.trim().firstOrNull()?.uppercase() ?: "?",
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(member.name.ifBlank { "Sin nombre" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
                if (isMe) {
                    Spacer(Modifier.width(6.dp))
                    Text("(tú)", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (member.phone.isNotBlank())
                Text(member.phone, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
