package com.cerca.app.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cerca.app.model.Medication
import com.cerca.app.viewmodel.MedicationViewModel

private val DAY_LABELS = listOf("L", "M", "X", "J", "V", "S", "D")
// Mapeo: índice 0-6 → Calendar.DAY_OF_WEEK: Lun=2, Mar=3, Mié=4, Jue=5, Vie=6, Sáb=7, Dom=1
private val DAY_CALENDAR = listOf(2, 3, 4, 5, 6, 7, 1)

@Composable
fun MedicationsScreen(
    viewModel: MedicationViewModel = viewModel()
) {
    val medications by viewModel.medications.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()
    var showForm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadMedications() }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Cabecera ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "💊 Medicamentos",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { showForm = !showForm }) {
                Text(if (showForm) "✕ Cerrar" else "+ Nuevo")
            }
        }

        if (message != null) {
            Text(
                text = message!!,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
        }

        // ── Formulario expandible ─────────────────────────────────────────────
        if (showForm) {
            AddMedicationForm(
                onAdd = { med ->
                    viewModel.addMedication(med)
                    showForm = false
                },
                onCancel = { showForm = false }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // ── Lista de recordatorios ────────────────────────────────────────────
        if (loading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            }
        } else if (medications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💊", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No tienes recordatorios aún",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Pulsa "+ Nuevo" para agregar uno",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(medications, key = { it.id }) { med ->
                    MedicationCard(
                        medication = med,
                        onToggle = { viewModel.toggleActive(med) },
                        onDelete = { viewModel.deleteMedication(med) }
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

// ─── Tarjeta de medicamento ───────────────────────────────────────────────────

@Composable
private fun MedicationCard(
    medication: Medication,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var showConfirmDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (medication.active)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "💊", fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medication.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (medication.dose.isNotBlank()) {
                        Text(
                            text = medication.dose,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // hora
                Text(
                    text = "%02d:%02d".format(medication.hour, medication.minute),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (medication.active)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            // Días de la semana
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DAY_LABELS.forEachIndexed { idx, label ->
                    val calDay = DAY_CALENDAR[idx]
                    val active = calDay in medication.days
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(width = 30.dp, height = 26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = if (active) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            if (medication.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = medication.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onToggle,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (medication.active) "Pausar" else "Activar")
                }
                OutlinedButton(
                    onClick = { showConfirmDelete = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar")
                }
            }
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Eliminar recordatorio") },
            text = { Text("¿Eliminar "${medication.name}"? Se cancelarán sus notificaciones.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showConfirmDelete = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) { Text("Cancelar") }
            }
        )
    }
}

// ─── Formulario para añadir ────────────────────────────────────────────────────

@Composable
private fun AddMedicationForm(
    onAdd: (Medication) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var hourStr by remember { mutableStateOf("8") }
    var minuteStr by remember { mutableStateOf("00") }
    // días seleccionados: índice 0-6
    var selectedDayIndices by remember { mutableStateOf(setOf(0, 1, 2, 3, 4, 5, 6)) }

    var nameError by remember { mutableStateOf(false) }
    var timeError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Nuevo recordatorio",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; nameError = false },
            label = { Text("Nombre del medicamento *") },
            isError = nameError,
            supportingText = if (nameError) ({ Text("Campo requerido") }) else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = dose,
            onValueChange = { dose = it },
            label = { Text("Dosis (ej. 500 mg)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        // Hora
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = hourStr,
                onValueChange = { if (it.length <= 2) { hourStr = it; timeError = false } },
                label = { Text("Hora (0-23)") },
                isError = timeError,
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = minuteStr,
                onValueChange = { if (it.length <= 2) { minuteStr = it; timeError = false } },
                label = { Text("Minutos (0-59)") },
                isError = timeError,
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        if (timeError) {
            Text("Hora inválida", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))

        // Días de la semana
        Text("Días de la semana", style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DAY_LABELS.forEachIndexed { idx, label ->
                val active = idx in selectedDayIndices
                FilterChip(
                    selected = active,
                    onClick = {
                        selectedDayIndices = if (active)
                            selectedDayIndices - idx
                        else
                            selectedDayIndices + idx
                    },
                    label = { Text(label, fontSize = 12.sp) },
                    modifier = Modifier.height(34.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notas (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancelar")
            }
            Button(
                onClick = {
                    val h = hourStr.toIntOrNull()
                    val m = minuteStr.toIntOrNull()
                    nameError = name.isBlank()
                    timeError = h == null || m == null || h !in 0..23 || m !in 0..59
                    if (nameError || timeError) return@Button

                    val days = if (selectedDayIndices.isEmpty())
                        DAY_CALENDAR
                    else
                        selectedDayIndices.map { DAY_CALENDAR[it] }

                    onAdd(
                        Medication(
                            name = name.trim(),
                            dose = dose.trim(),
                            hour = h!!,
                            minute = m!!,
                            days = days,
                            notes = notes.trim(),
                            active = true
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Guardar")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
