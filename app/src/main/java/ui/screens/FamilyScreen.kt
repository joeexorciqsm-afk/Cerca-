package com.cerca.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val clipboard    = LocalClipboardManager.current

    var showLeaveDialog    by remember { mutableStateOf<String?>(null) }
    var showAddFamilyDialog by remember { mutableStateOf(false) }
    var copied             by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) { kotlinx.coroutines.delay(2500); viewModel.clearFamilyMessage() }
    }
    LaunchedEffect(copied) {
        if (copied) { kotlinx.coroutines.delay(2000); copied = false }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Cabecera ──────────────────────────────────────────────────────────
        item {
            TextButton(onClick = onBack) {
                Text("← Volver", style = MaterialTheme.typography.titleMedium)
            }
            Text("Mis familias",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            if (message != null) {
                Spacer(Modifier.height(6.dp))
                Text(message!!, color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }

        // ── Mi código para invitar ────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🔑 Mi código para invitar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = myFamilyCode.ifBlank { "—" },
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 6.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Comparte este código para que te agreguen a su familia",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = { clipboard.setText(AnnotatedString(myFamilyCode)); copied = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (copied) "✅ Copiado" else "📋 Copiar código",
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── Botón agregar familia ─────────────────────────────────────────────
        item {
            Button(
                onClick  = { showAddFamilyDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = MaterialTheme.shapes.large
            ) {
                Text("+ Agregar familia", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }

        // ── Separador ─────────────────────────────────────────────────────────
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Familias (${familyGroups.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                if (familyGroups.isNotEmpty())
                    Text("${familyGroups.sumOf { it.members.size }} miembros en total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
        }

        if (familyGroups.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("👨‍👩‍👧‍👦", fontSize = 40.sp)
                        Text("Aún no estás en ninguna familia",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                        Text("Pulsa \"+ Agregar familia\" para crear una o unirte.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(familyGroups, key = { it.code }) { group ->
                val isOwn = myFamilyCode.isNotBlank() && group.code == myFamilyCode
                FamilyGroupCard(
                    group      = group,
                    isOwn      = isOwn,
                    currentUid = currentUid,
                    onRename   = { newName -> viewModel.saveFamilyName(newName, group.code) },
                    onLeave    = { showLeaveDialog = group.code }
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    // ── Diálogo salida ────────────────────────────────────────────────────────
    showLeaveDialog?.let { code ->
        val group = familyGroups.firstOrNull { it.code == code }
        AlertDialog(
            onDismissRequest = { showLeaveDialog = null },
            title = { Text("Salir de la familia") },
            text  = { Text("¿Salir de \"${group?.name?.ifBlank { code } ?: code}\"? Dejarás de ver a sus miembros.") },
            confirmButton = {
                Button(onClick = { viewModel.leaveFamily(code); showLeaveDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Salir") }
            },
            dismissButton = { TextButton(onClick = { showLeaveDialog = null }) { Text("Cancelar") } }
        )
    }

    // ── Diálogo agregar familia ───────────────────────────────────────────────
    if (showAddFamilyDialog) {
        AddFamilyDialog(
            onDismiss      = { showAddFamilyDialog = false },
            onCreateFamily = { name ->
                viewModel.createFamily(name)
                showAddFamilyDialog = false
            },
            onJoinFamily   = { code ->
                viewModel.joinFamily(code)
                showAddFamilyDialog = false
            }
        )
    }
}

// ── Diálogo crear / unirse ────────────────────────────────────────────────────

@Composable
private fun AddFamilyDialog(
    onDismiss: () -> Unit,
    onCreateFamily: (String) -> Unit,
    onJoinFamily: (String) -> Unit
) {
    var tab          by remember { mutableStateOf(0) } // 0=Crear, 1=Unirse
    var familyName   by remember { mutableStateOf("") }
    var joinCode     by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier  = Modifier.fillMaxWidth(0.92f).wrapContentHeight(),
            shape     = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {

                Text("Agregar familia",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)

                Spacer(Modifier.height(16.dp))

                // Tabs Crear / Unirse
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 },
                        text = { Text("🏠 Crear nueva") })
                    Tab(selected = tab == 1, onClick = { tab = 1 },
                        text = { Text("🔗 Unirse") })
                }

                Spacer(Modifier.height(20.dp))

                when (tab) {
                    // ── Crear familia ─────────────────────────────────────
                    0 -> {
                        Text("Se generará un código único para que tu familia se una.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = familyName,
                            onValueChange = { familyName = it },
                            label = { Text("Nombre de la familia") },
                            placeholder = { Text("Ej: Familia Ramírez") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick  = { onCreateFamily(familyName) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = MaterialTheme.shapes.large
                        ) {
                            Text("🏠 Crear familia", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // ── Unirse a familia ──────────────────────────────────
                    1 -> {
                        Text("Pide a un familiar su código de 6 caracteres.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = joinCode,
                            onValueChange = { joinCode = it.uppercase().take(6) },
                            label = { Text("Código de familia") },
                            placeholder = { Text("Ej: AB23XZ") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick  = { onJoinFamily(joinCode) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = MaterialTheme.shapes.large,
                            enabled  = joinCode.length == 6
                        ) {
                            Text("🔗 Unirme", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
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
    var nameInput by remember(group.name) { mutableStateOf(group.name) }
    var editMode  by remember { mutableStateOf(false) }
    var expanded  by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth().then(
            if (isOwn) Modifier.border(2.dp, MaterialTheme.colorScheme.primary,
                MaterialTheme.shapes.medium)
            else Modifier
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isOwn) MaterialTheme.colorScheme.secondaryContainer
                             else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Fila nombre + badge + botones
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(group.name.ifBlank { group.code },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        if (isOwn) {
                            Surface(shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary) {
                                Text("⭐ Mi familia",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
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
                    Text(if (editMode) "✕" else "✏️", fontSize = 16.sp)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "▲" else "▼", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Renombrar
            if (editMode) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = nameInput, onValueChange = { nameInput = it },
                    label = { Text("Nombre de la familia") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onRename(nameInput); editMode = false },
                        modifier = Modifier.weight(1f)) { Text("Guardar") }
                    OutlinedButton(onClick = { editMode = false },
                        modifier = Modifier.weight(1f)) { Text("Cancelar") }
                }
            }

            // Miembros expandibles
            if (expanded) {
                if (group.members.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Sin miembros aún",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    group.members.forEach { member ->
                        MemberRow(member = member, isMe = member.id == currentUid)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // Salir (solo familias ajenas)
            if (!isOwn) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth(),
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
        Box(modifier = Modifier.size(40.dp).clip(CircleShape)
            .background(if (isMe) MaterialTheme.colorScheme.primary
                         else MaterialTheme.colorScheme.secondary),
            contentAlignment = Alignment.Center) {
            Text(member.name.trim().firstOrNull()?.uppercase() ?: "?",
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(member.name.ifBlank { "Sin nombre" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
                if (isMe) {
                    Surface(shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("tú", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                if (member.role.isNotBlank() && member.role != "familiar") {
                    Surface(shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(when (member.role) {
                                "paciente" -> "🏥"; "cuidador" -> "🤝"; "admin" -> "⭐"
                                else -> member.role
                            }, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            if (member.phone.isNotBlank())
                Text(member.phone, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
