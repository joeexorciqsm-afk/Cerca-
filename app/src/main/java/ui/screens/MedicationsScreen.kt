package com.cerca.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cerca.app.model.Medication
import com.cerca.app.viewmodel.AuthViewModel
import com.cerca.app.viewmodel.MedicationViewModel

private val DAY_LABELS   = listOf("L","M","X","J","V","S","D")
private val DAY_CALENDAR = listOf(2, 3, 4, 5, 6, 7, 1)

@Composable
fun MedicationsScreen(
    viewModel: MedicationViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()
) {
    val medications    by viewModel.medications.collectAsState()
    val familyMeds     by viewModel.familyMedications.collectAsState()
    val familyMembers  by authViewModel.familyMembers.collectAsState()
    val familyGroups   by authViewModel.familyGroups.collectAsState()
    val loading        by viewModel.loading.collectAsState()
    val message        by viewModel.message.collectAsState()
    var showForm       by remember { mutableStateOf(false) }
    var selectedTab    by remember { mutableStateOf(0) }

    // Familia seleccionada para el recordatorio: null = todas
    var selectedFamilyCode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadMedications() }
    LaunchedEffect(familyMembers) {
        if (familyMembers.isNotEmpty()) viewModel.loadFamilyMedications(familyMembers)
    }
    LaunchedEffect(message) {
        if (message != null) { kotlinx.coroutines.delay(2500); viewModel.clearMessage() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("💊 Medicamentos", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (selectedTab == 0) {
                FilledTonalButton(onClick = { showForm = !showForm }) {
                    Text(if (showForm) "✕ Cerrar" else "+ Nuevo", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (message != null) {
            Text(message!!, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp))
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                text = { Text("💊 Míos") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                text = { Text("👨‍👩‍👧 Familia") })
        }

        HorizontalDivider()

        if (showForm && selectedTab == 0) {
            // Selector de familia para el nuevo recordatorio
            FamilySelector(
                groups       = familyGroups,
                selectedCode = selectedFamilyCode,
                onSelect     = { selectedFamilyCode = it }
            )
            AddMedicationForm(
                ownerName = authViewModel.userName.collectAsState().value,
                onAdd    = { med ->
                    val ownerName = authViewModel.userName.value
                    // Si hay una familia seleccionada → solo ese código
                    // Si null → código propio (para notificar a todas las familias)
                    val targetCode = selectedFamilyCode ?: authViewModel.myFamilyCode.value
                    viewModel.addMedication(
                        med.copy(familyCode = targetCode, ownerName = ownerName),
                        ownerName
                    )
                    showForm = false
                },
                onCancel = { showForm = false }
            )
            HorizontalDivider()
        }

        when (selectedTab) {
            0 -> MyMedicationsContent(medications, loading, viewModel)
            1 -> FamilyMedicationsContent(familyMeds)
        }
    }
}

@Composable
private fun MyMedicationsContent(
    medications: List<Medication>,
    loading: Boolean,
    viewModel: MedicationViewModel
) {
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        medications.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("💊", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text("Sin recordatorios aún", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Pulsa + Nuevo para agregar uno", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        else -> LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()
        ) {
            items(medications, key = { it.id }) { med ->
                MedicationCard(
                    medication = med,
                    onToggle   = { viewModel.toggleActive(med) },
                    onDelete   = { viewModel.deleteMedication(med) },
                    onTogglePublic = { viewModel.togglePublic(med) }
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun FamilyMedicationsContent(familyMeds: List<Medication>) {
    if (familyMeds.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("👨‍👩‍👧", fontSize = 48.sp)
                Text("Sin medicamentos compartidos",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Los familiares pueden compartir sus medicamentos\nactivando la opción 'Compartir con familia' en cada uno.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()
        ) {
            val grouped = familyMeds.groupBy { it.ownerName.ifBlank { "Familiar" } }
            grouped.forEach { (owner, meds) ->
                item {
                    Text("👤 $owner", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
                items(meds, key = { it.id }) { med ->
                    MedicationCard(medication = med, readOnly = true)
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ── Tarjeta ───────────────────────────────────────────────────────────────────

@Composable
private fun MedicationCard(
    medication: Medication,
    onToggle: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onTogglePublic: (() -> Unit)? = null,
    readOnly: Boolean = false
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = if (medication.active)
                MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // Nombre + hora
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💊", fontSize = 26.sp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(medication.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    if (medication.dose.isNotBlank())
                        Text(medication.dose,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("%02d:%02d".format(medication.hour, medication.minute),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (medication.active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(10.dp))

            // Días
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DAY_LABELS.forEachIndexed { idx, label ->
                    val active = DAY_CALENDAR[idx] in medication.days
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(width = 32.dp, height = 28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(label, fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (medication.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(medication.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(10.dp))

            if (!readOnly) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onToggle != null)
                        OutlinedButton(onClick = { onToggle.invoke() }, modifier = Modifier.weight(1f)) {
                            Text(if (medication.active) "Pausar" else "Activar")
                        }
                    if (onDelete != null)
                        OutlinedButton(onClick = { confirmDelete = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Eliminar") }
                }
                // Toggle compartir con familia
                if (onTogglePublic != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (medication.isPublic) "👨‍👩‍👧 Visible para la familia"
                             else "🔒 Solo visible para mí",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (medication.isPublic) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f))
                        Switch(checked = medication.isPublic, onCheckedChange = { onTogglePublic?.invoke() })
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar?") },
            text  = { Text("Se cancelarán las notificaciones de ${medication.name}.") },
            confirmButton = {
                Button(
                    onClick = { onDelete?.invoke(); confirmDelete = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}

// ── Formulario ────────────────────────────────────────────────────────────────

@Composable
private fun AddMedicationForm(
    onAdd: (Medication) -> Unit,
    onCancel: () -> Unit,
    ownerName: String = ""
) {
    var name     by remember { mutableStateOf("") }
    var dose     by remember { mutableStateOf("") }
    var hourStr  by remember { mutableStateOf("8") }
    var minStr   by remember { mutableStateOf("00") }
    var selDays  by remember { mutableStateOf(setOf(0,1,2,3,4,5,6)) }
    var isPublic by remember { mutableStateOf(true) }   // público por defecto
    var nameErr  by remember { mutableStateOf(false) }
    var timeErr  by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Nuevo recordatorio",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = name, onValueChange = { name = it; nameErr = false },
            label = { Text("Nombre *") }, isError = nameErr, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            supportingText = if (nameErr) ({ Text("Requerido") }) else null
        )

        OutlinedTextField(
            value = dose, onValueChange = { dose = it },
            label = { Text("Dosis (ej. 500 mg)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = hourStr,
                onValueChange = { if (it.length <= 2) { hourStr = it; timeErr = false } },
                label = { Text("Hora") }, isError = timeErr, singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = minStr,
                onValueChange = { if (it.length <= 2) { minStr = it; timeErr = false } },
                label = { Text("Min") }, isError = timeErr, singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        if (timeErr) Text("Hora inválida", color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)

        // Días
        Text("Días", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            DAY_LABELS.forEachIndexed { idx, label ->
                FilterChip(
                    selected = idx in selDays,
                    onClick  = {
                        selDays = if (idx in selDays) selDays - idx else selDays + idx
                    },
                    label    = { Text(label, fontSize = 12.sp) },
                    modifier = Modifier.height(34.dp)
                )
            }
        }

        // Compartir con familia — toggle visible
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isPublic) "👨‍👩‍👧 Visible para tu familia" else "🔒 Solo visible para ti",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isPublic) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (isPublic) "Tu familia recibirá este recordatorio también"
                    else "Solo tú recibirás la notificación",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isPublic, onCheckedChange = { isPublic = it })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
            Button(
                onClick = {
                    val h = hourStr.toIntOrNull()
                    val m = minStr.toIntOrNull()
                    nameErr = name.isBlank()
                    timeErr = h == null || m == null || h !in 0..23 || m !in 0..59
                    if (nameErr || timeErr) return@Button
                    val days = if (selDays.isEmpty()) DAY_CALENDAR
                               else selDays.map { DAY_CALENDAR[it] }
                    onAdd(Medication(
                        name = name.trim(), dose = dose.trim(),
                        hour = h!!, minute = m!!, days = days,
                        active = true, isPublic = isPublic,
                        ownerName = ownerName
                    ))
                },
                modifier = Modifier.weight(1f)
            ) { Text("Guardar") }
        }
        Spacer(Modifier.height(4.dp))
    }
}
