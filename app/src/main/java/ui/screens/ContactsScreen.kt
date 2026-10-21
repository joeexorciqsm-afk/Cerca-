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
                    // Contactos agregados manualmente (sí se pueden eliminar).
                    items(myContacts) { c ->
                        ContactCard(
                            name = c.name,
                            phone = c.phone,
                            onCall = { call(it) },
                            onDelete = { contactToDelete = c.id to c.name }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, phone ->
                viewModel.addContact(name, phone)
                showAddDialog = false
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
private fun AddContactDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar contacto") },
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
            Button(onClick = { onAdd(name, phone) }) {
                Text("Agregar")
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
    onDelete: (() -> Unit)? = null
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar grande con la inicial del nombre (foto grande - F02).
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.trim().firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Nombre y teléfono, con texto grande y legible.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name.ifBlank { "Sin nombre" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = phone.ifBlank { "Sin teléfono" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Botones de acción (llamar y, si aplica, eliminar).
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (phone.isNotBlank()) {
                    Button(
                        onClick = { onCall(phone) },
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text(
                            text = "📞 Llamar",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                // Solo los contactos manuales reciben onDelete.
                if (onDelete != null) {
                    if (phone.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(
                            text = "🗑️ Eliminar",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
