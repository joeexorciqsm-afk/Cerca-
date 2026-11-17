package com.cerca.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cerca.app.model.EmergencyAlert
import com.cerca.app.model.User
import com.cerca.app.viewmodel.AuthViewModel
import com.cerca.app.viewmodel.EmergencyState
import com.cerca.app.viewmodel.EmergencyViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EmergencyScreen(
    authViewModel: AuthViewModel,
    emergencyViewModel: EmergencyViewModel = viewModel()
) {
    val context = LocalContext.current
    val userName    by authViewModel.userName.collectAsState()
    val userPhone   by authViewModel.phone.collectAsState()
    val userAge     by authViewModel.age.collectAsState()
    val familyCode  by authViewModel.familyCode.collectAsState()
    val bloodType   by authViewModel.bloodType.collectAsState()
    val conditions  by authViewModel.conditions.collectAsState()
    val allergies   by authViewModel.allergies.collectAsState()
    val familyMembers by authViewModel.familyMembers.collectAsState()

    val state         by emergencyViewModel.state.collectAsState()
    val myAlerts      by emergencyViewModel.myAlerts.collectAsState()
    val familyAlerts  by emergencyViewModel.familyAlerts.collectAsState()
    val familyHistory by emergencyViewModel.familyHistory.collectAsState()

    val currentUser = remember(userName, userPhone, userAge, familyCode, bloodType, conditions, allergies) {
        User(name = userName, phone = userPhone, age = userAge, familyCode = familyCode,
            bloodType = bloodType, conditions = conditions, allergies = allergies)
    }

    LaunchedEffect(familyCode) { emergencyViewModel.loadAndSubscribe(currentUser) }
    LaunchedEffect(state) {
        if (state is EmergencyState.Sent) {
            kotlinx.coroutines.delay(5_000); emergencyViewModel.resetState()
        }
    }

    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        locationGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted) emergencyViewModel.sendSOS(currentUser)
    }

    var selectedTab      by remember { mutableStateOf(0) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customMessage    by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Cabecera ──────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 20.dp, vertical = 12.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()) {
                Text("🆘", fontSize = 28.sp)
                Text("Emergencias", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        // ── Tabs ──────────────────────────────────────────────────────────────
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                text = { Text("🆘 SOS") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                text = { Text("📋 Historial") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                text = { Text("🚗 Conducción") })
        }

        when (selectedTab) {
            0 -> SOSTab(
                state           = state,
                familyMembers   = familyMembers,
                familyAlerts    = familyAlerts,
                locationGranted = locationGranted,
                onSOS = {
                    if (locationGranted) emergencyViewModel.sendSOS(currentUser)
                    else permLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                onCustomSOS     = { showCustomDialog = true },
                on911           = { emergencyViewModel.call911() },
                onReset         = { emergencyViewModel.resetState() },
                onResolve       = { emergencyViewModel.resolveAlert(it) },
                context         = context
            )
            1 -> HistoryTab(
                myAlerts      = myAlerts,
                familyHistory = familyHistory,
                currentUser   = currentUser,
                onResolve     = { emergencyViewModel.resolveAlert(it) },
                context       = context
            )
            2 -> DrivingModeTab(
                user            = currentUser,
                locationGranted = locationGranted,
                onSOS = {
                    if (locationGranted) emergencyViewModel.sendSOS(currentUser, "Emergencia en carretera", "driving")
                    else permLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                on911 = { emergencyViewModel.call911() },
                context = context
            )
        }
    }

    // ── Diálogo mensaje personalizado ─────────────────────────────────────────
    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Mensaje de emergencia") },
            text  = {
                Column {
                    Text("¿Qué está pasando?")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = customMessage, onValueChange = { customMessage = it },
                        placeholder = { Text("Me caí, estoy en casa…") },
                        maxLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCustomDialog = false
                        val msg = customMessage.ifBlank { "¡Necesito ayuda!" }
                        if (locationGranted) emergencyViewModel.sendSOS(currentUser, msg)
                        else permLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("🆘 Enviar") }
            },
            dismissButton = { TextButton(onClick = { showCustomDialog = false }) { Text("Cancelar") } }
        )
    }
}

// ── Tab SOS ───────────────────────────────────────────────────────────────────

@Composable
private fun SOSTab(
    state: EmergencyState,
    familyMembers: List<User>,
    familyAlerts: List<EmergencyAlert>,
    locationGranted: Boolean,
    onSOS: () -> Unit,
    onCustomSOS: () -> Unit,
    on911: () -> Unit,
    onReset: () -> Unit,
    onResolve: (String) -> Unit,
    context: android.content.Context
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Botón SOS
        item { SOSButton(state = state, onSOS = onSOS, onCustomSOS = onCustomSOS, onReset = onReset) }

        // Botón 911
        item {
            Button(
                onClick  = on911,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1))
            ) {
                Text("📞 Llamar al 911", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = Color.White)
            }
        }

        // Quién recibe la alerta
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("¿Quién recibe la alerta?",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (familyMembers.isEmpty()) {
                        Text("Aún no tienes familiares vinculados. Ve a Perfil → Mi familia.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        familyMembers.forEach { m ->
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 3.dp)) {
                                Text("👤", fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(m.name.ifBlank { "Sin nombre" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium)
                                    if (m.phone.isNotBlank())
                                        Text(m.phone, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Alertas activas de familiares
        if (familyAlerts.isNotEmpty()) {
            item {
                Text("⚠️ Alertas activas de familiares",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error)
            }
            items(familyAlerts, key = { it.id }) { alert ->
                FamilyAlertCard(alert = alert, onResolve = { onResolve(alert.id) }, context = context)
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ── Tab historial ─────────────────────────────────────────────────────────────

@Composable
private fun HistoryTab(
    myAlerts: List<EmergencyAlert>,
    familyHistory: List<EmergencyAlert>,
    currentUser: User,
    onResolve: (String) -> Unit,
    context: android.content.Context
) {
    var showMine   by remember { mutableStateOf(true) }
    var showFamily by remember { mutableStateOf(true) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Mis alertas
        item {
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()) {
                Text("📋 Mis alertas (${myAlerts.size})",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { showMine = !showMine }) {
                    Text(if (showMine) "Ocultar" else "Ver")
                }
            }
        }
        if (showMine) {
            if (myAlerts.isEmpty()) {
                item {
                    Text("No has enviado alertas aún.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                items(myAlerts, key = { "my-${it.id}" }) { alert ->
                    HistoryCard(alert = alert, context = context)
                }
            }
        }

        // Historial familia
        item {
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()) {
                Text("👨‍👩‍👧 Historial familiar (${familyHistory.filter { it.userId != currentUser.name }.size})",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { showFamily = !showFamily }) {
                    Text(if (showFamily) "Ocultar" else "Ver")
                }
            }
        }
        if (showFamily) {
            val familyOwn = familyHistory.filter { it.userName != currentUser.name }
            if (familyOwn.isEmpty()) {
                item {
                    Text("Sin alertas de familiares.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                items(familyOwn, key = { "fam-${it.id}" }) { alert ->
                    HistoryCard(alert = alert, context = context, showResolve = !alert.resolved,
                        onResolve = { onResolve(alert.id) })
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun HistoryCard(
    alert: EmergencyAlert,
    context: android.content.Context,
    showResolve: Boolean = false,
    onResolve: (() -> Unit)? = null
) {
    val time = remember(alert.timestamp) {
        SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(alert.timestamp))
    }
    val typeEmoji = when (alert.type) {
        "911"     -> "🚨"
        "medical" -> "🏥"
        "driving" -> "🚗"
        "fall"    -> "⬇️"
        else      -> "🆘"
    }

    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (alert.resolved) MaterialTheme.colorScheme.surfaceVariant
                             else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(typeEmoji, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(alert.userName.ifBlank { "Yo" },
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(time, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (alert.resolved) {
                    Surface(shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondary) {
                        Text("✓ Resuelta", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            Text(alert.message, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp))

            // Info médica adjunta
            if (alert.bloodType.isNotBlank() || alert.conditions.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                if (alert.bloodType.isNotBlank())
                    Text("🩸 ${alert.bloodType}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (alert.conditions.isNotBlank())
                    Text("💊 ${alert.conditions}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (alert.allergies.isNotBlank())
                    Text("⚠️ ${alert.allergies}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (alert.hasLocation || showResolve) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (alert.hasLocation) {
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("geo:${alert.latitude},${alert.longitude}?q=${alert.latitude},${alert.longitude}(${alert.userName})")
                            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                        }, modifier = Modifier.weight(1f)) { Text("📍 Ver mapa") }
                    }
                    if (showResolve && onResolve != null) {
                        TextButton(onClick = onResolve, modifier = Modifier.weight(1f)) {
                            Text("✅ Resolver")
                        }
                    }
                }
            }
        }
    }
}

// ── Tab modo conducción ───────────────────────────────────────────────────────

@Composable
private fun DrivingModeTab(
    user: User,
    locationGranted: Boolean,
    onSOS: () -> Unit,
    on911: () -> Unit,
    context: android.content.Context
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("🚗", fontSize = 56.sp)
        Text("Modo conducción",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
        Text("Botones grandes para usar sin distraerte mientras manejas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)

        Spacer(Modifier.height(8.dp))

        // SOS conducción — botón grande
        Button(
            onClick  = onSOS,
            modifier = Modifier.fillMaxWidth().height(80.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            shape    = MaterialTheme.shapes.large
        ) {
            Text("🆘  SOS — Emergencia en carretera",
                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }

        // 911
        Button(
            onClick  = on911,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1)),
            shape    = MaterialTheme.shapes.large
        ) {
            Text("📞  Llamar al 911",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        // Llamar a familiar más cercano
        if (user.phone.isNotBlank()) {
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_DIAL,
                        Uri.parse("tel:${user.phone}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape    = MaterialTheme.shapes.large
            ) {
                Text("📞  Mi teléfono guardado", fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(modifier = Modifier.padding(14.dp)) {
                Text("💡", fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Text("Al pulsar SOS en modo conducción se envía automáticamente tu ubicación GPS con el mensaje \"Emergencia en carretera\" a toda tu familia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

// ── Botón SOS animado ─────────────────────────────────────────────────────────

@Composable
private fun SOSButton(
    state: EmergencyState,
    onSOS: () -> Unit,
    onCustomSOS: () -> Unit,
    onReset: () -> Unit
) {
    val isBusy = state is EmergencyState.GettingLocation || state is EmergencyState.Sending
    val isSent = state is EmergencyState.Sent
    val isErr  = state is EmergencyState.Error

    val btnColor by animateColorAsState(
        targetValue = when { isSent -> Color(0xFF2E7D32); isErr -> Color(0xFFE65100); else -> Color(0xFFD32F2F) },
        animationSpec = tween(400), label = "btnColor")
    val scale by animateFloatAsState(
        targetValue = if (isBusy) 0.92f else 1f, animationSpec = tween(200), label = "scale")

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {

            Box(modifier = Modifier.size(160.dp).scale(scale).clip(CircleShape)
                .background(btnColor), contentAlignment = Alignment.Center) {
                Button(onClick = { if (!isBusy && !isSent) onSOS() },
                    modifier = Modifier.fillMaxSize(), shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                    enabled = !isBusy) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isBusy) {
                            CircularProgressIndicator(color = Color.White,
                                modifier = Modifier.size(44.dp), strokeWidth = 4.dp)
                            Text(
                                if (state is EmergencyState.GettingLocation) "GPS…" else "Enviando…",
                                color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
                        } else {
                            Text(if (isSent) "✅" else "🆘", fontSize = 48.sp)
                            Text(if (isSent) "Enviado" else "SOS",
                                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                isSent -> {
                    val alert = (state as EmergencyState.Sent).alert
                    Text("✅ Alerta enviada a tu familia", style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (alert.hasLocation)
                        Text("📍 Con ubicación incluida", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onReset) { Text("Volver") }
                }
                isErr  -> {
                    Text((state as EmergencyState.Error).message, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Button(onClick = onReset) { Text("Reintentar") }
                }
                else   -> {
                    Text("Presiona para alertar con tu ubicación",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onCustomSOS) { Text("✏️ Con mensaje personalizado") }
                }
            }
        }
    }
}

// ── Tarjeta alerta activa familiar ────────────────────────────────────────────

@Composable
private fun FamilyAlertCard(
    alert: EmergencyAlert,
    onResolve: () -> Unit,
    context: android.content.Context
) {
    val time = remember(alert.timestamp) {
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(alert.timestamp))
    }
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🆘", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(alert.userName.ifBlank { "Familiar" },
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(time, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f))
                }
            }
            Text(alert.message, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.onErrorContainer)
            if (alert.bloodType.isNotBlank())
                Text("🩸 ${alert.bloodType}  💊 ${alert.conditions}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (alert.hasLocation)
                    Button(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("geo:${alert.latitude},${alert.longitude}?q=${alert.latitude},${alert.longitude}(${alert.userName})")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                    }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("📍 Mapa") }
                if (alert.userPhone.isNotBlank())
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_DIAL,
                            Uri.parse("tel:${alert.userPhone}")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }, modifier = Modifier.weight(1f)) { Text("📞 Llamar") }
            }
            TextButton(onClick = onResolve, modifier = Modifier.fillMaxWidth()) {
                Text("✅ Marcar como resuelta")
            }
        }
    }
}
