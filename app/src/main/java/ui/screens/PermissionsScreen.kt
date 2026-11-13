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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.cerca.app.R

// ── Modelo de un permiso ──────────────────────────────────────────────────────

data class AppPermission(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val why: String,
    val manifestPermission: String?,   // null = permiso especial (ir a Ajustes directamente)
    val isSpecial: Boolean = false      // USE_EXACT_ALARM, POST_NOTIFICATIONS API < 33
)

// ── Lista de permisos requeridos ──────────────────────────────────────────────

fun buildRequiredPermissions(): List<AppPermission> = listOf(
    AppPermission(
        id = "notifications",
        emoji = "🔔",
        title = "Notificaciones",
        description = "Recibir avisos de medicamentos y alertas de emergencia de tu familia.",
        why = "Sin este permiso no podrás recibir recordatorios de tus pastillas ni enterarte si un familiar necesita ayuda.",
        manifestPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.POST_NOTIFICATIONS else null,
        isSpecial = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    ),
    AppPermission(
        id = "location",
        emoji = "📍",
        title = "Ubicación",
        description = "Enviar tu posición cuando actives una alerta de emergencia.",
        why = "Tu familia podrá ver exactamente dónde estás cuando pulses el botón SOS.",
        manifestPermission = Manifest.permission.ACCESS_FINE_LOCATION
    ),
    AppPermission(
        id = "audio",
        emoji = "🎤",
        title = "Micrófono",
        description = "Usar el asistente de voz para controlar la app sin tocar la pantalla.",
        why = "Necesario para los comandos de voz: llamar, enviar SOS, agregar medicamentos, etc.",
        manifestPermission = Manifest.permission.RECORD_AUDIO
    ),
    AppPermission(
        id = "exact_alarm",
        emoji = "⏰",
        title = "Alarmas exactas",
        description = "Programar recordatorios de medicamentos a la hora exacta.",
        why = "Sin este permiso las notificaciones de pastillas pueden llegar con retraso.",
        manifestPermission = null,
        isSpecial = true
    )
)

// ── Función auxiliar: comprobar si un permiso está concedido ──────────────────

fun isPermissionGranted(context: Context, perm: AppPermission): Boolean {
    return when {
        perm.id == "exact_alarm" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.canScheduleExactAlarms()
            } else true
        }
        perm.id == "notifications" && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
            // En Android < 13 las notificaciones están activas por defecto
            true
        }
        perm.manifestPermission != null -> {
            ContextCompat.checkSelfPermission(context, perm.manifestPermission) ==
                PackageManager.PERMISSION_GRANTED
        }
        else -> false
    }
}

// ── Pantalla principal de permisos ────────────────────────────────────────────

@Composable
fun PermissionsScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current
    val permissions = remember { buildRequiredPermissions() }

    // Estado de cada permiso (re-evaluado en cada recomposición)
    var permStates by remember {
        mutableStateOf(permissions.map { isPermissionGranted(context, it) })
    }

    // Revalida cuando la app vuelve a primer plano (usuario regresa de Ajustes)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permStates = permissions.map { isPermissionGranted(context, it) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val allGranted = permStates.all { it }

    // Avanzar automáticamente cuando todos estén concedidos
    LaunchedEffect(allGranted) {
        if (allGranted) onAllGranted()
    }

    // Permiso activo en el diálogo
    var dialogPerm by remember { mutableStateOf<AppPermission?>(null) }

    // Launcher para permisos normales (runtime)
    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        permStates = permissions.map { isPermissionGranted(context, it) }
    }

    // ── UI ────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        // Logo de la app
        AppLogo()

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Permisos necesarios",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Cerca necesita los siguientes permisos para funcionar correctamente.\nPuedes cambiarlos en cualquier momento desde Ajustes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        // Tarjetas de permisos
        permissions.forEachIndexed { index, perm ->
            val granted = permStates.getOrElse(index) { false }

            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
            ) {
                PermissionCard(
                    permission = granted,
                    perm       = perm,
                    onRequest  = { dialogPerm = perm }
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Botón continuar (solo visible cuando todos están concedidos)
        if (allGranted) {
            Button(
                onClick = onAllGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("¡Todo listo! Continuar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            // Botón para omitir (permisos opcionales)
            val pendingCount = permStates.count { !it }
            OutlinedButton(
                onClick = onAllGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "Omitir por ahora ($pendingCount pendiente${if (pendingCount > 1) "s" else ""})",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // ── Diálogo de detalle por permiso ────────────────────────────────────
    dialogPerm?.let { perm ->
        PermissionDetailDialog(
            perm    = perm,
            context = context,
            onGrant = {
                dialogPerm = null
                when {
                    // Alarmas exactas → Ajustes del sistema
                    perm.id == "exact_alarm" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                    // Notificaciones en Android < 13 → Ajustes de la app
                    perm.isSpecial -> {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                    // Runtime permission
                    perm.manifestPermission != null -> {
                        runtimeLauncher.launch(perm.manifestPermission)
                    }
                }
            },
            onSettings = {
                dialogPerm = null
                // Abrir ajustes de la app directamente
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            },
            onDismiss = { dialogPerm = null }
        )
    }
}

// ── Logo de la app ────────────────────────────────────────────────────────────

@Composable
fun AppLogo(size: Int = 96) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(size.dp / 4))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher),
            contentDescription = "Logo de Cerca",
            modifier = Modifier.size((size * 0.8f).dp)
        )
    }
}

// ── Tarjeta de permiso individual ────────────────────────────────────────────

@Composable
private fun PermissionCard(
    permission: Boolean,
    perm: AppPermission,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (permission)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (permission) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji del permiso
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (permission) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = perm.emoji, fontSize = 24.sp)
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = perm.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = perm.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(10.dp))

            // Estado / botón
            if (permission) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondary
                ) {
                    Text(
                        text = "✓ Activo",
                        color = MaterialTheme.colorScheme.onSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            } else {
                Button(
                    onClick = onRequest,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Activar", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ── Diálogo de detalle ────────────────────────────────────────────────────────

@Composable
private fun PermissionDetailDialog(
    perm: AppPermission,
    context: Context,
    onGrant: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo + emoji
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppLogo(size = 56)
                    Spacer(Modifier.width(14.dp))
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(perm.emoji, fontSize = 28.sp)
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = perm.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = perm.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(12.dp))

                // Tarjeta "¿Por qué?"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Text("💡", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = perm.why,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Botón principal
                val btnLabel = if (perm.isSpecial || perm.manifestPermission == null)
                    "Ir a Ajustes del sistema" else "Conceder permiso"
                val btnIcon  = if (perm.isSpecial || perm.manifestPermission == null) "⚙️" else "✅"

                Button(
                    onClick  = onGrant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("$btnIcon $btnLabel", fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(10.dp))

                // Botón Ajustes de la app
                OutlinedButton(
                    onClick  = onSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("⚙️ Ajustes de la app", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(8.dp))

                TextButton(onClick = onDismiss) {
                    Text("Ahora no", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
