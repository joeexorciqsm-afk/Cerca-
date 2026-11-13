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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
    val familyMembers by authViewModel.familyMembers.collectAsState()

    val state        by emergencyViewModel.state.collectAsState()
    val myAlerts     by emergencyViewModel.myAlerts.collectAsState()
    val familyAlerts by emergencyViewModel.familyAlerts.collectAsState()

    // Construir objeto User para pasarlo al ViewModel
    val currentUser = remember(userName, userPhone, userAge, familyCode) {
        User(name = userName, phone = userPhone, age = userAge, familyCode = familyCode)
    }

    // Suscribir alertas en tiempo real al entrar a la pantalla
    LaunchedEffect(familyCode) {
        emergencyViewModel.loadAndSubscribe(currentUser)
    }

    // Auto-reset del estado Sent después de 5s
    LaunchedEffect(state) {
        if (state is EmergencyState.Sent) {
            kotlinx.coroutines.delay(5_000)
            emergencyViewModel.resetState()
        }
    }

    // Permisos de ubicación
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
        // Si se otorgó permiso, lanzar SOS inmediatamente
        if (locationGranted) emergencyViewModel.sendSOS(currentUser)
    }

    var showCustomDialog by remember { mutableStateOf(false) }
    var customMessage by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Cabecera ──────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()) {
                Text("🆘", fontSize = 32.sp)
                Text(
                    "Emergencias",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "Presiona el botón para alertar a tu familia",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Botón SOS central ─────────────────────────────────────────────
            item {
                SOSButton(
                    state = state,
                    onSOS = {
                        if (locationGranted) {
                            emergencyViewModel.sendSOS(currentUser)
                        } else {
                            permLauncher.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        }
                    },
                    onCustomSOS = { showCustomDialog = true },
                    onReset = { emergencyViewModel.resetState() }
                )
            }

            // ── Info de quién recibe la alerta ────────────────────────────────
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "¿Quién recibe la alerta?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        if (familyMembers.isEmpty()) {
                            Text(
                                "Aún no tienes familiares vinculados. Ve a Perfil → Mi familia.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            familyMembers.forEach { member ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Text("👤", fontSize = 18.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(member.name.ifBlank { "Sin nombre" },
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium)
                                        if (member.phone.isNotBlank())
                                            Text("📞 ${member.phone}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Alertas activas de familiares ─────────────────────────────────
            if (familyAlerts.isNotEmpty()) {
                item {
                    Text(
                        "⚠️ Alertas activas de familiares",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                items(familyAlerts, key = { it.id }) { alert ->
                    FamilyAlertCard(
                        alert = alert,
                        onResolve = { emergencyViewModel.resolveAlert(alert.id) },
                        context = context
                    )
                }
            }

            // ── Mis alertas enviadas ──────────────────────────────────────────
            if (myAlerts.isNotEmpty()) {
                item {
                    Text(
                        "📋 Mis alertas enviadas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(myAlerts, key = { it.id }) { alert ->
                    MyAlertCard(alert = alert, context = context)
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // ── Diálogo mensaje personalizado ────────────────────────────────────────
    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Mensaje de emergencia") },
            text = {
                Column {
                    Text("Escribe qué está pasando (opcional):")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customMessage,
                        onValueChange = { customMessage = it },
                        placeholder = { Text("Ej: Me caí, estoy en casa...") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCustomDialog = false
                        val msg = customMessage.ifBlank { "¡Necesito ayuda!" }
                        if (locationGranted) {
                            emergencyViewModel.sendSOS(currentUser, msg)
                        } else {
                            permLauncher.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("🆘 Enviar alerta") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) { Text("Cancelar") }
            }
        )
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
    val isError = state is EmergencyState.Error

    val btnColor by animateColorAsState(
        targetValue = when {
            isSent  -> Color(0xFF2E7D32)   // verde
            isError -> Color(0xFFE65100)   // naranja
            else    -> Color(0xFFD32F2F)   // rojo
        },
        animationSpec = tween(400), label = "btnColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (isBusy) 0.92f else 1f,
        animationSpec = tween(200), label = "scale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Botón circular grande
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(btnColor),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { if (!isBusy && !isSent) onSOS() },
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                    enabled = !isBusy
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (state is EmergencyState.GettingLocation) "Obteniendo\nubicación…"
                                else "Enviando…",
                                color = Color.White,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(if (isSent) "✅" else "🆘", fontSize = 56.sp)
                            Text(
                                if (isSent) "Enviado" else "SOS",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            when {
                isSent -> {
                    val alert = (state as EmergencyState.Sent).alert
                    Text(
                        "✅ Alerta enviada a tu familia",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (alert.hasLocation) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "📍 Con tu ubicación incluida",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onReset) { Text("Volver al inicio") }
                }
                isError -> {
                    Text(
                        (state as EmergencyState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onReset) { Text("Reintentar") }
                }
                else -> {
                    Text(
                        "Presiona para alertar a tu familia con tu ubicación",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onCustomSOS) {
                        Text("✏️ Enviar con mensaje personalizado")
                    }
                }
            }
        }
    }
}

// ── Tarjeta alerta de familiar ────────────────────────────────────────────────

@Composable
private fun FamilyAlertCard(
    alert: EmergencyAlert,
    onResolve: () -> Unit,
    context: android.content.Context
) {
    val timeStr = remember(alert.timestamp) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(alert.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🆘", fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        alert.userName.ifBlank { "Familiar" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        timeStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                alert.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            if (alert.userPhone.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "📞 ${alert.userPhone}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (alert.hasLocation) {
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW,
                                    Uri.parse("geo:${alert.latitude},${alert.longitude}?q=${alert.latitude},${alert.longitude}(${alert.userName})")
                                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("📍 Ver ubicación") }
                }
                if (alert.userPhone.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_DIAL,
                                    Uri.parse("tel:${alert.userPhone}")
                                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("📞 Llamar") }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onResolve,
                modifier = Modifier.fillMaxWidth()
            ) { Text("✅ Marcar como resuelta") }
        }
    }
}

// ── Tarjeta mis alertas ───────────────────────────────────────────────────────

@Composable
private fun MyAlertCard(alert: EmergencyAlert, context: android.content.Context) {
    val timeStr = remember(alert.timestamp) {
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(alert.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (alert.resolved)
                MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (alert.resolved) "✅" else "🆘", fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    timeStr + if (alert.hasLocation) " · 📍 con ubicación" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (alert.hasLocation) {
                IconButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW,
                            Uri.parse("geo:${alert.latitude},${alert.longitude}?q=${alert.latitude},${alert.longitude}")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    )
                }) { Text("🗺️", fontSize = 20.sp) }
            }
        }
    }
}
