package com.cerca.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cerca.app.model.PhoneContact
import com.cerca.app.viewmodel.ContactsViewModel

@Composable
fun ContactsScreen(viewModel: ContactsViewModel = viewModel()) {
    val familyContacts by viewModel.familyContacts.collectAsState()
    val myContacts     by viewModel.myContacts.collectAsState()
    val phoneContacts  by viewModel.phoneContacts.collectAsState()
    val loading        by viewModel.loading.collectAsState()
    val message        by viewModel.message.collectAsState()
    val context        = LocalContext.current

    var showAddDialog   by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }
    var contactToEdit   by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var memberToHide    by remember { mutableStateOf<Pair<String, String>?>(null) }
    var searchQuery     by remember { mutableStateOf("") }

    // Pestaña activa: 0 = App, 1 = Teléfono
    var activeTab by remember { mutableStateOf(0) }

    // Permisos
    var pendingCall  by remember { mutableStateOf("") }
    var hasCallPerm  by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED)
    }
    var hasReadPerm  by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED)
    }

    // Launcher CALL_PHONE
    val callPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCallPerm = granted
        if (pendingCall.isNotBlank()) {
            val action = if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL
            context.startActivity(Intent(action, Uri.parse("tel:$pendingCall")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            pendingCall = ""
        }
    }

    // Launcher READ_CONTACTS
    val readPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasReadPerm = granted
        if (granted) viewModel.loadPhoneContacts()
    }

    fun call(phone: String) {
        if (hasCallPerm) {
            context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } else {
            pendingCall = phone
            callPermLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadContacts()
        if (hasReadPerm) viewModel.loadPhoneContacts()
    }
    LaunchedEffect(message) {
        if (message != null) { kotlinx.coroutines.delay(2000); viewModel.clearMessage() }
    }

    // Filtrar agenda por búsqueda
    val filteredPhone = remember(phoneContacts, searchQuery) {
        if (searchQuery.isBlank()) phoneContacts
        else phoneContacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.phones.any { p -> p.contains(searchQuery) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Cabecera ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Contactos",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            if (activeTab == 0) {
                FilledTonalButton(onClick = { showAddDialog = true }) {
                    Text("+ Agregar", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ── Tabs ──────────────────────────────────────────────────────────
        TabRow(selectedTabIndex = activeTab) {
            Tab(selected = activeTab == 0, onClick = { activeTab = 0 },
                text = { Text("📋 App") })
            Tab(selected = activeTab == 1, onClick = {
                activeTab = 1
                if (!hasReadPerm) readPermLauncher.launch(Manifest.permission.READ_CONTACTS)
                else if (phoneContacts.isEmpty()) viewModel.loadPhoneContacts()
            }, text = { Text("📱 Teléfono") })
        }

        if (message != null) {
            Text(message!!, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        // ── Contenido por tab ─────────────────────────────────────────────
        when (activeTab) {
            0 -> AppContactsTab(
                familyContacts = familyContacts,
                myContacts     = myContacts.map { it.id to (it.name to it.phone) },
                loading        = loading,
                onCall         = { call(it) },
                onEditContact  = { id, n, p -> contactToEdit = Triple(id, n, p) },
                onDeleteContact = { id, n -> contactToDelete = id to n },
                onHideMember   = { id, n -> memberToHide = id to n }
            )
            1 -> PhoneContactsTab(
                contacts      = filteredPhone,
                hasPermission = hasReadPerm,
                searchQuery   = searchQuery,
                onSearchChange = { searchQuery = it },
                onCall        = { call(it) },
                onRequestPerm = { readPermLauncher.launch(Manifest.permission.READ_CONTACTS) },
                onRefresh     = { viewModel.loadPhoneContacts() }
            )
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
                Button(onClick = { viewModel.deleteContact(id); contactToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { contactToDelete = null }) { Text("Cancelar") } }
        )
    }
}

// ── Tab 0: contactos de la app ────────────────────────────────────────────────

@Composable
private fun AppContactsTab(
    familyContacts:  List<com.cerca.app.model.User>,
    myContacts:      List<Pair<String, Pair<String, String>>>,
    loading:         Boolean,
    onCall:          (String) -> Unit,
    onEditContact:   (String, String, String) -> Unit,
    onDeleteContact: (String, String) -> Unit,
    onHideMember:    (String, String) -> Unit
) {
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        familyContacts.isEmpty() && myContacts.isEmpty() -> EmptyState(
            emoji = "📋",
            title = "Sin contactos aún",
            subtitle = "Pulsa + Agregar o vincula tu familia desde Perfil"
        )
        else -> LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (familyContacts.isNotEmpty()) {
                item { SectionLabel("👨‍👩‍👧‍👦 Familia") }
                items(familyContacts) { m ->
                    ContactCard(name = m.name, phone = m.phone, isFamily = true,
                        onCall = { onCall(m.phone) },
                        onDelete = { onHideMember(m.id, m.name) })
                }
            }
            if (myContacts.isNotEmpty()) {
                item {
                    if (familyContacts.isNotEmpty()) Spacer(Modifier.height(4.dp))
                    SectionLabel("📋 Mis contactos")
                }
                items(myContacts) { (id, np) ->
                    val (name, phone) = np
                    ContactCard(name = name, phone = phone, isFamily = false,
                        onCall = { onCall(phone) },
                        onEdit = { onEditContact(id, name, phone) },
                        onDelete = { onDeleteContact(id, name) })
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ── Tab 1: contactos del teléfono ─────────────────────────────────────────────

@Composable
private fun PhoneContactsTab(
    contacts:       List<PhoneContact>,
    hasPermission:  Boolean,
    searchQuery:    String,
    onSearchChange: (String) -> Unit,
    onCall:         (String) -> Unit,
    onRequestPerm:  () -> Unit,
    onRefresh:      () -> Unit
) {
    if (!hasPermission) {
        // Pedir permiso
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("📱", fontSize = 56.sp)
                Text("Acceso a la agenda",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center)
                Text("Para ver los contactos del teléfono necesitamos tu permiso. Funciona con cualquier marca.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
                Button(onClick = onRequestPerm,
                    modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Permitir acceso a contactos", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Buscador
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Buscar por nombre o número…") },
            leadingIcon  = { Text("🔍", fontSize = 18.sp) },
            trailingIcon = {
                if (searchQuery.isNotBlank())
                    IconButton(onClick = { onSearchChange("") }) { Text("✕", fontSize = 16.sp) }
            },
            singleLine = true,
            modifier   = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        )

        if (contacts.isEmpty() && searchQuery.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (contacts.isEmpty()) {
            EmptyState("🔍", "Sin resultados", "Intenta con otro nombre o número")
        } else {
            // Contador + botón refrescar
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("${contacts.size} contacto${if (contacts.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRefresh) { Text("↺ Actualizar") }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(contacts, key = { it.id }) { contact ->
                    PhoneContactCard(contact = contact, onCall = onCall)
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

// ── Tarjeta contacto del teléfono ─────────────────────────────────────────────

@Composable
private fun PhoneContactCard(contact: PhoneContact, onCall: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(contact.name.trim().firstOrNull()?.uppercase() ?: "?",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Mostrar todos los números si tiene más de uno
                contact.phones.take(2).forEach { phone ->
                    Text(phone, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (contact.phones.size > 2)
                    Text("+${contact.phones.size - 2} más",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Si tiene varios números, mostrar el principal; si tiene uno, llamar directo
            if (contact.phones.size == 1) {
                FilledTonalIconButton(onClick = { onCall(contact.primaryPhone) },
                    modifier = Modifier.size(44.dp)) {
                    Text("📞", fontSize = 20.sp)
                }
            } else {
                // Mostrar menú para elegir número
                var expanded by remember { mutableStateOf(false) }
                Box {
                    FilledTonalIconButton(onClick = { expanded = true },
                        modifier = Modifier.size(44.dp)) {
                        Text("📞", fontSize = 20.sp)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        contact.phones.forEach { phone ->
                            DropdownMenuItem(
                                text = { Text(phone) },
                                onClick = { expanded = false; onCall(phone) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Componentes comunes ───────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun EmptyState(emoji: String, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(emoji, fontSize = 48.sp)
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ContactCard(
    name: String, phone: String, isFamily: Boolean,
    onCall: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isFamily)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape)
                .background(if (isFamily) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center) {
                Text(name.trim().firstOrNull()?.uppercase() ?: "?",
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name.ifBlank { "Sin nombre" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (phone.isNotBlank())
                    Text(phone, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (phone.isNotBlank())
                FilledTonalIconButton(onClick = onCall, modifier = Modifier.size(44.dp)) {
                    Text("📞", fontSize = 20.sp)
                }
            if (onEdit != null)
                IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                    Text("✏️", fontSize = 18.sp)
                }
            if (onDelete != null)
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Text("🗑️", fontSize = 18.sp)
                }
        }
    }
}

@Composable
private fun ContactDialog(
    title: String, confirmText: String,
    initialName: String = "", initialPhone: String = "",
    onDismiss: () -> Unit, onConfirm: (String, String) -> Unit
) {
    var name  by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it },
                    label = { Text("Teléfono") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onConfirm(name, phone) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
