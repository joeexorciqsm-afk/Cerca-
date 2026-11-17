package com.cerca.app.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cerca.app.model.Medication
import com.cerca.app.model.User
import com.cerca.app.viewmodel.AuthViewModel
import com.cerca.app.viewmodel.MedicationViewModel
import com.google.firebase.auth.FirebaseAuth

@Composable
fun FamilyDashboardScreen(
    viewModel: AuthViewModel,
    medicationViewModel: MedicationViewModel = viewModel()
) {
    val members    by viewModel.familyMembers.collectAsState()
    val familyName by viewModel.familyName.collectAsState()
    val familyCode by viewModel.familyCode.collectAsState()
    val familyMeds by medicationViewModel.familyMedications.collectAsState()
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // Cargar medicamentos públicos de familiares cuando cambian los miembros
    LaunchedEffect(members) {
        if (members.isNotEmpty()) medicationViewModel.loadFamilyMedications(members)
    }

    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Cabecera ──────────────────────────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()) {
                Text("🏠", fontSize = 36.sp)
                Text(familyName.ifBlank { "Mi Familia" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (familyCode.isNotBlank()) {
                    Text("Código: $familyCode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                }
            }
        }

        if (members.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("👨‍👩‍👧‍👦", fontSize = 56.sp)
                    Text("Tu familia está vacía", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Ve a Perfil → Mi familia y comparte el código.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
            return
        }

        // ── Tabs ──────────────────────────────────────────────────────────────
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                text = { Text("👥 Miembros") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                text = { Text("💊 Medicamentos") })
        }

        when (selectedTab) {
            0 -> MembersTab(members, currentUid)
            1 -> FamilyMedsTab(familyMeds)
        }
    }
}

// ── Tab miembros ──────────────────────────────────────────────────────────────

@Composable
private fun MembersTab(members: List<User>, currentUid: String) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryChip("👥", "${members.size} miembro${if (members.size != 1) "s" else ""}",
                    Modifier.weight(1f))
                SummaryChip("✅", "Familia activa", Modifier.weight(1f))
            }
        }
        items(members, key = { it.id }) { member ->
            MemberCard(member = member, isMe = member.id == currentUid)
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun MemberCard(member: User, isMe: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(50.dp).clip(CircleShape)
                    .background(if (isMe) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center) {
                    Text(member.name.trim().firstOrNull()?.uppercase() ?: "?",
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(member.name.ifBlank { "Sin nombre" },
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (isMe) {
                            Spacer(Modifier.width(6.dp))
                            RolChip("Tú", MaterialTheme.colorScheme.primary)
                        }
                        if (member.role.isNotBlank() && member.role != "familiar") {
                            Spacer(Modifier.width(6.dp))
                            val (roleLabel, roleColor) = when (member.role) {
                                "paciente"  -> "🏥 Paciente"  to MaterialTheme.colorScheme.error
                                "cuidador"  -> "🤝 Cuidador"  to MaterialTheme.colorScheme.tertiary
                                "admin"     -> "⭐ Admin"      to MaterialTheme.colorScheme.secondary
                                else        -> member.role     to MaterialTheme.colorScheme.outline
                            }
                            RolChip(roleLabel, roleColor)
                        }
                    }
                    if (member.phone.isNotBlank())
                        Text("📞 ${member.phone}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (member.age.isNotBlank())
                        Text("🎂 ${member.age} años", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Expandir info médica si tiene
                val hasMedical = member.bloodType.isNotBlank() || member.conditions.isNotBlank()
                    || member.allergies.isNotBlank()
                if (hasMedical) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "▲" else "▼", fontSize = 14.sp)
                    }
                }
            }

            // Info médica expandible
            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("🏥 Info médica", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (member.bloodType.isNotBlank())
                        InfoRow("🩸 Tipo de sangre", member.bloodType)
                    if (member.allergies.isNotBlank())
                        InfoRow("⚠️ Alergias", member.allergies)
                    if (member.conditions.isNotBlank())
                        InfoRow("💊 Padecimientos", member.conditions)
                    if (member.emergencyInfo.isNotBlank())
                        InfoRow("📝 Notas", member.emergencyInfo)
                    if (member.emergencyContactName.isNotBlank())
                        InfoRow("👤 Contacto emergencia",
                            "${member.emergencyContactName} · ${member.emergencyContactPhone}")
                }
            }
        }
    }
}

@Composable
private fun RolChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = MaterialTheme.shapes.small, color = color) {
        Text(text, style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Tab medicamentos públicos ─────────────────────────────────────────────────

@Composable
private fun FamilyMedsTab(meds: List<Medication>) {
    if (meds.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("💊", fontSize = 48.sp)
                Text("Sin medicamentos compartidos",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Los familiares pueden marcar sus medicamentos\ncomo públicos en su sección 💊.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Agrupar por ownerName
        val byOwner = meds.groupBy { it.ownerName.ifBlank { "Familiar" } }
        byOwner.forEach { (owner, ownerMeds) ->
            item {
                Text("👤 $owner", style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
            items(ownerMeds, key = { it.id }) { med ->
                FamilyMedCard(med)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun FamilyMedCard(med: Medication) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("💊", fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(med.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (med.dose.isNotBlank())
                    Text(med.dose, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (med.notes.isNotBlank())
                    Text(med.notes, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("%02d:%02d".format(med.hour, med.minute),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (med.active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummaryChip(emoji: String, label: String, modifier: Modifier) {
    Card(modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            Text(emoji, fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
