package com.cerca.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cerca.app.viewmodel.ContactsViewModel

@Composable
fun ContactsScreen(viewModel: ContactsViewModel = viewModel()) {
    val familyContacts by viewModel.familyContacts.collectAsState()
    val myContacts     by viewModel.myContacts.collectAsState()
    val loading        by viewModel.loading.collectAsState()
    val message        by viewModel.message.collectAsState()
    val context        = LocalContext.current

    var showAddDialog    by remember { mutableStateOf(false) }
    var contactToDelete  by remember { mutableStateOf<Pair<String, String>?>(null) }
    var contactToEdit    by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var memberToHide     by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) { viewModel.loadContacts() }
    LaunchedEffect(message) {
        if (message != null) { kotlinx.coroutines.delay(2000); viewModel.clearMessage() }
    }

    fun call(phone: String) =
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Cabecera ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Contactos",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            FilledTonalButton(onClick = { showAddDialog = true }) {
                Text("+ Agregar", fontWeight = FontWeight.SemiBold)
            }
        }

        // Mensaje feedback
        if (message != null) {
            Text(message!!,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(4.dp))
        }

        HorizontalDivider()

        // ── Contenido ─────────────────────────────────────────────────────
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            familyContacts.isEmpty() && myContacts.isEmpty() -> EmptyContacts()
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (familyContacts.isNotEmpty()) {
                        item {
                            SectionLabel("👨‍👩‍👧‍👦 Familia")
                        }
                        items(familyContacts) { m ->
                            ContactCard(
                                name     = m.name,
                                phone    = m.phone,
                                isFamily = true,
                                onCall   = { call(m.phone) },
                                onDelete = { memberToHide = m.id to m.name }
                            )
                        }
                    }
                    if (myContacts.isNotEmpty()) {
                        item {
                            if (familyContacts.isNotEmpty()) Spacer(Modifier.height(4.dp))
                            SectionLabel("📋 Mis contactos")
                        }
                        items(myContacts) { c ->
                            ContactCard(
                                name     = c.name,
                                phone    = c.phone,
                                isFamily = false,
                                onCall   = { call(c.phone) },
                                onEdit   = { contactToEdit = Triple(c.id, c.name, c.phone) },
                                onDelete = { contactToDelete = c.id to c.name }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }

    // ── Diálogos ──────────────────────────────────────────────────────────
    if (showAddDialog) {
        ContactDialog("Agregar contacto", "Agregar",
            onDismiss = { showAddDialog = false },
            onConfirm = { n, p -> viewModel.addContact(n, p); showAddDialog = false }
        )
    }
    contactToEdit?.let { (id, n, p) ->
        ContactDialog("Editar contacto", "Guardar", n, p,
            onDismiss = { contactToEdit = null },
            onConfirm = { nn, np -> viewModel.updateContact(id, nn, np); contactToEdit = null }
        )
    }
    memberToHide?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { memberToHide = null },
            title = { Text("Quitar de la lista") },
            text  = { Text("¿Quitar a \"${name.ifBlank { "este contacto" }}\"? Su cuenta no se elimina.") },
            confirmButton = {
                Button(onClick = { viewModel.hideFamilyMember(id); memberToHide = null }) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { memberToHide = null }) { Text("Cancelar") } }
        )
    }
    contactToDelete?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = { Text("Eliminar contacto") },
            text  = { Text("¿Eliminar a \"$name\"?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteContact(id); contactToDelete = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { contactToDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun EmptyContacts() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📋", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text("Sin contactos aún",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("Pulsa + Agregar o vincula tu familia desde Perfil",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ContactCard(
    name: String,
    phone: String,
    isFamily: Boolean,
    onCall: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = if (isFamily)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (isFamily) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.trim().firstOrNull()?.uppercase() ?: "?",
                    color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(14.dp))

            // Nombre + teléfono
            Column(modifier = Modifier.weight(1f)) {
                Text(name.ifBlank { "Sin nombre" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (phone.isNotBlank())
                    Text(phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1)
            }

            // Acciones
            if (phone.isNotBlank()) {
                FilledTonalIconButton(onClick = onCall, modifier = Modifier.size(44.dp)) {
                    Text("📞", fontSize = 20.sp)
                }
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                    Text("✏️", fontSize = 18.sp)
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Text("🗑️", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun ContactDialog(
    title: String,
    confirmText: String,
    initialName: String = "",
    initialPhone: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name  by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Nombre") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it },
                    label = { Text("Teléfono") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, phone) }) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
