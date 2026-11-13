package com.cerca.app.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cerca.app.R

// ─────────────────────────────────────────────────────────────────────────────
// Modelo
// ─────────────────────────────────────────────────────────────────────────────

data class AppPermission(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val why: String,
    val manifestPermission: String?,
    val isSpecial: Boolean = false
)

fun buildRequiredPermissions(): List<AppPermission> = buildList {
    // Notificaciones — solo runtime en Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(AppPermission(
            id = "notifications", emoji = "🔔",
            title = "Notificaciones",
            description = "Avisos de medicamentos y alertas de emergencia de tu familia.",
            why = "Sin este permiso no recibirás recordatorios de pastillas ni sabrás si un familiar necesita ayuda.",
            manifestPermission = Manifest.permission.POST_NOTIFICATIONS
        ))
    }
    // Ubicación
    add(AppPermission(
        id = "location", emoji = "📍",
        title = "Ubicación",
        description = "Enviar tu posición exacta cuando actives una alerta SOS.",
        why = "Tu familia podrá ver dónde estás al instante cuando pulses el botón SOS.",
        manifestPermission = Manifest.permission.ACCESS_FINE_LOCATION
    ))
    // Micrófono
    add(AppPermission(
        id = "audio", emoji = "🎤",
        title = "Micrófono",
        description = "Usar el asistente de voz para controlar la app sin tocar la pantalla.",
        why = "Necesario para los comandos de voz: llamar, enviar SOS, gestionar medicamentos, etc.",
        manifestPermission = Manifest.permission.RECORD_AUDIO
    ))
    // Alarmas exactas — solo Android 12+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(AppPermission(
            id = "exact_alarm", emoji = "⏰",
            title = "Alarmas exactas",
            description = "Recordatorios de medicamentos a la hora exacta.",
            why = "Sin este permiso las notificaciones de pastillas pueden llegar con retraso.",
            manifestPermission = null,
            isSpecial = true
        ))
    }
}

fun isPermissionGranted(context: Context, perm: AppPermission): Boolean = when {
    perm.id == "exact_alarm" -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        else true
    }
    perm.manifestPermission != null ->
        ContextCompat.checkSelfPermission(context, perm.manifestPermission) == PackageManager.PERMISSION_GRANTED
    else -> false
}

// ─────────────────────────────────────────────────────────────────────────────
// Pantalla principal
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PermissionsScreen(onAllGranted: () -> Unit) {
    val context    = LocalContext.current
    val lifecycle  = LocalLifecycleOwner.current.lifecycle
    val permissions = remember { buildRequiredPermissions() }

    fun checkStates() = permissions.map { isPermissionGranted(context, it) }

    var permStates by remember { mutableStateOf(checkStates()) }

    // Re-evalúa permisos cuando la app regresa a primer plano
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permStates = checkStates()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val allGranted = permStates.all { it }

    LaunchedEffect(allGranted) {
        if (allGranted) onAllGranted()
    }

    var dialogPerm by remember { mutableStateOf<AppPermission?>(null) }

    // Launcher para permisos runtime (un permiso a la vez)
    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permStates = checkStates() }

    // ── UI ────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        AppLogo(size = 100)

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Permisos necesarios",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Cerca necesita estos permisos para funcionar.\nPuedes cambiarlos en cualquier momento desde Ajustes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        permissions.forEachIndexed { index, perm ->
            val granted = permStates.getOrElse(index) { false }
            PermissionCard(
                granted   = granted,
                perm      = perm,
                onRequest = { dialogPerm = perm }
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(16.dp))

        if (allGranted) {
            Button(
                onClick  = onAllGranted,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(16.dp)
            ) {
                Text("¡Todo listo! Continuar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            val pending = permStates.count { !it }
            OutlinedButton(
                onClick  = onAllGranted,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "Omitir por ahora ($pending pendiente${if (pending > 1) "s" else ""})",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    // Diálogo de detalle
    dialogPerm?.let { perm ->
        PermissionDetailDialog(
            perm       = perm,
            onGrant    = {
                dialogPerm = null
                when {
                    perm.id == "exact_alarm" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.fromParts("package", context.packageName, null)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    perm.manifestPermission != null ->
                        runtimeLauncher.launch(perm.manifestPermission)
                    else ->
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                }
            },
            onSettings = {
                dialogPerm = null
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            },
            onDismiss  = { dialogPerm = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Logo de la app
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppLogo(size: Int = 96) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter            = painterResource(id = R.mipmap.ic_launcher),
            contentDescription = "Logo de Cerca",
            modifier           = Modifier.size((size * 0.82f).dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tarjeta de permiso
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionCard(
    granted:   Boolean,
    perm:      AppPermission,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (granted) MaterialTheme.colorScheme.secondaryContainer
                             else         MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(if (granted) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (granted) MaterialTheme.colorScheme.secondary
                        else         MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) { Text(perm.emoji, fontSize = 24.sp) }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(perm.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(perm.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.width(10.dp))

            if (granted) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondary) {
                    Text("✓ Activo", color = MaterialTheme.colorScheme.onSecondary,
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            } else {
                Button(
                    onClick = onRequest,
                    shape   = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("Activar", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Diálogo de detalle por permiso
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionDetailDialog(
    perm:      AppPermission,
    onGrant:   () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier  = Modifier.fillMaxWidth(0.92f).wrapContentHeight(),
            shape     = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo + emoji lado a lado
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppLogo(size = 60)
                    Spacer(Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) { Text(perm.emoji, fontSize = 30.sp) }
                }

                Spacer(Modifier.height(20.dp))

                Text(perm.title, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

                Spacer(Modifier.height(10.dp))

                Text(perm.description, style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center)

                Spacer(Modifier.height(14.dp))

                // Tarjeta ¿Por qué?
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Text("💡", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(perm.why, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                Spacer(Modifier.height(22.dp))

                val isSpecialPerm = perm.isSpecial || perm.manifestPermission == null
                Button(
                    onClick  = onGrant,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        if (isSpecialPerm) "⚙️ Ir a Ajustes del sistema"
                        else               "✅ Conceder permiso",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick  = onSettings,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape    = RoundedCornerShape(14.dp)
                ) {
                    Text("⚙️ Ajustes de la app",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(6.dp))

                TextButton(onClick = onDismiss) {
                    Text("Ahora no", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
