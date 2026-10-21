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
fun ContactsScreen(
    viewModel: ContactsViewModel = viewModel()
) {
    val familyContacts by viewModel.familyContacts.collectAsState()
    val myContacts by viewModel.myContacts.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    // Contacto manual pendiente de confirmar eliminación (id y nombre).
    var contactToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Contacto manual que se está editando (id, nombre, teléfono); null = no editando.
    var contactToEdit by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    // Cargamos los contactos al abrir la pantalla.
    LaunchedEffect(Unit) {
        viewModel.loadContacts()
    }

    fun call(phone: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        context.startActivity(intent)
    }

    val isEmpty = familyContacts.isEmpty() && myContacts.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Contactos",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.height(52.dp)
            ) {
                Text("➕ Agregar")
            }
        }

        // Mensaje de confirmación/error al agregar.
        if (message != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message!!,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            isEmpty -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Aún no tienes contactos.\n" +
                            "Pulsa \"Agregar\" para añadir un número, o vincula a tu familia desde Perfil.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Familiares vinculados (no se pueden eliminar: vienen de la familia).
                    items(familyContacts) { family ->
                        ContactCard(
                            name = family.name,
                            phone = family.phone,
                            onCall = { call(it) }
                        )
                    }
                    // Contactos agregados manualmente (se pueden editar y eliminar).
                    items(myContacts) { c ->
                        ContactCard(
                            name = c.name,
                            phone = c.phone,
                            onCall = { call(it) },
                            onEdit = { contactToEdit = Triple(c.id, c.name, c.phone) },
                            onDelete = { contactToDelete = c.id to c.name }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ContactDialog(
            title = "Agregar contacto",
            confirmText = "Agregar",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, phone ->
                viewModel.addContact(name, phone)
                showAddDialog = false
            }
        )
    }

    // Diálogo para editar un contacto manual (precargado con sus datos).
    contactToEdit?.let { (id, name, phone) ->
        ContactDialog(
            title = "Editar contacto",
            confirmText = "Guardar",
            initialName = name,
            initialPhone = phone,
            onDismiss = { contactToEdit = null },
            onConfirm = { newName, newPhone ->
                viewModel.updateContact(id, newName, newPhone)
                contactToEdit = null
            }
        )
    }

    // Diálogo de confirmación para eliminar un contacto (evita borrados accidentales).
    contactToDelete?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = { Text("Eliminar contacto") },
            text = { Text("¿Seguro que quieres eliminar a \"$name\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteContact(id)
                        contactToDelete = null
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
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
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Número de teléfono") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, phone) }) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun ContactCard(
    name: String,
    phone: String,
    onCall: (String) -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Fila superior: avatar + nombre y teléfono (con espacio suficiente).
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar grande con la inicial del nombre (foto grande - F02).
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.trim().firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name.ifBlank { "Sin nombre" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = phone.ifBlank { "Sin teléfono" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Fila inferior: botones de acción, bien separados del texto.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (phone.isNotBlank()) {
                    Button(
                        onClick = { onCall(phone) },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            text = "📞 Llamar",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                // Empuja Editar/Eliminar hacia la derecha.
                Spacer(modifier = Modifier.weight(1f))

                if (onEdit != null) {
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("✏️ Editar")
                    }
                }

                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            text = "🗑️",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
