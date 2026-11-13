package com.cerca.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cerca.app.model.Medication
import com.cerca.app.model.User
import com.cerca.app.viewmodel.AuthViewModel
import com.cerca.app.viewmodel.ContactsViewModel
import com.cerca.app.viewmodel.EmergencyViewModel
import com.cerca.app.viewmodel.MedicationViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Mapeo nombre de día español → Calendar.DAY_OF_WEEK
private val DIA_A_CALENDAR = mapOf(
    "lunes" to 2, "martes" to 3, "miércoles" to 4, "miercoles" to 4,
    "jueves" to 5, "viernes" to 6, "sábado" to 7, "sabado" to 7, "domingo" to 1,
    "todos" to -1, "diario" to -1, "todos los días" to -1, "todos los dias" to -1
)

@Composable
fun VoiceScreen(
    contactsViewModel:  ContactsViewModel  = viewModel(),
    authViewModel:      AuthViewModel      = viewModel(),
    medicationViewModel: MedicationViewModel = viewModel(),
    emergencyViewModel: EmergencyViewModel = viewModel()
) {
    val context = LocalContext.current

    val familyContacts by contactsViewModel.familyContacts.collectAsState()
    val myContacts     by contactsViewModel.myContacts.collectAsState()
    val familyCode     by authViewModel.familyCode.collectAsState()
    val familyName     by authViewModel.familyName.collectAsState()
    val userName       by authViewModel.userName.collectAsState()
    val userPhone      by authViewModel.phone.collectAsState()
    val userAge        by authViewModel.age.collectAsState()
    val members        by authViewModel.familyMembers.collectAsState()
    val medications    by medicationViewModel.medications.collectAsState()

    LaunchedEffect(Unit) {
        contactsViewModel.loadContacts()
        medicationViewModel.loadMedications()
    }

    var heard     by remember { mutableStateOf("") }
    var answer    by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }

    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocationPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCallPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingCallPhone by remember { mutableStateOf("") }

    val callPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCallPerm = granted
        if (pendingCallPhone.isNotBlank()) {
            val action = if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL
            context.startActivity(
                Intent(action, Uri.parse("tel:$pendingCallPhone")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            pendingCallPhone = ""
        }
    }
    val tts = remember {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale("es", "ES")
                engine?.setSpeechRate(0.95f)
            }
        }
        engine
    }
    DisposableEffect(Unit) { onDispose { tts.stop(); tts.shutdown() } }

    fun speak(text: String) { answer = text; tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "resp") }
    fun call(phone: String) {
        if (hasCallPerm) {
            context.startActivity(
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } else {
            pendingCallPhone = phone
            callPermLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    fun contactosDisponibles() =
        (familyContacts.map { it.name to it.phone } + myContacts.map { it.name to it.phone })
            .filter { it.second.isNotBlank() }

    fun extraerNumero(texto: String) = texto.filter { it.isDigit() }

    // ── Parseo de hora ────────────────────────────────────────────────────
    fun parsearHora(texto: String): Pair<Int, Int>? {
        val yCuarto  = Regex("""(\d{1,2})\s*y\s*cuarto""").find(texto)
        val yMedia   = Regex("""(\d{1,2})\s*y\s*media""").find(texto)
        val hm       = Regex("""(\d{1,2})[:\s](\d{2})""").find(texto)
        val soloHora = Regex("""(\d{1,2})\s*(am|pm|de la mañana|de la tarde|de la noche)?""").find(texto)
        return when {
            yCuarto  != null -> yCuarto.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 }?.let { it to 15 }
            yMedia   != null -> yMedia.groupValues[1].toIntOrNull()?.takeIf  { it in 0..23 }?.let { it to 30 }
            hm       != null -> {
                val h = hm.groupValues[1].toIntOrNull() ?: return null
                val m = hm.groupValues[2].toIntOrNull() ?: return null
                if (h in 0..23 && m in 0..59) h to m else null
            }
            soloHora != null -> {
                var h = soloHora.groupValues[1].toIntOrNull() ?: return null
                val suf = soloHora.groupValues[2]
                if ((suf.contains("tarde") || suf.contains("noche") || suf.contains("pm")) && h < 12) h += 12
                if (h in 0..23) h to 0 else null
            }
            else -> null
        }
    }

    // ── Parseo de días ────────────────────────────────────────────────────
    fun parsearDias(texto: String): List<Int> {
        if (texto.contains("todos") || texto.contains("diario") ||
            texto.contains("cada día") || texto.contains("cada dia"))
            return listOf(1, 2, 3, 4, 5, 6, 7)
        val encontrados = DIA_A_CALENDAR.entries
            .filter { (k, _) -> k != "todos" && k != "diario" && !k.startsWith("todos") && texto.contains(k) }
            .map { it.value }
        return encontrados.ifEmpty { listOf(1, 2, 3, 4, 5, 6, 7) }
    }

    // ── Lanzador de permisos de ubicación para SOS ────────────────────────
    var pendingSOS by remember { mutableStateOf("") }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPerm = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val msg = pendingSOS.ifBlank { "¡Necesito ayuda!" }
        val user = User(name = userName, phone = userPhone, age = userAge, familyCode = familyCode)
        emergencyViewModel.sendSOS(user, msg)
        speak("Enviando alerta de emergencia a tu familia")
        pendingSOS = ""
    }

    // ── Procesamiento de comandos ─────────────────────────────────────────
    fun procesarComando(frase: String): String {
        val t = frase.lowercase().trim()

        // ── EMERGENCIA SOS ────────────────────────────────────────────────

        // "envía SOS" / "manda alerta" / "llama al 911" / "emergencia" / "ayuda"
        val triggerSOS = listOf(
            "envía sos", "envia sos", "manda sos", "enviar sos",
            "manda una alerta", "envía una alerta", "envia una alerta",
            "envía alerta de emergencia", "envia alerta de emergencia",
            "emergencia", "necesito ayuda", "llama a emergencias",
            "activa el sos", "activa sos", "sos"
        ).firstOrNull { t.contains(it) }

        if (triggerSOS != null) {
            // Extraer mensaje personalizado si lo hay
            val msg = listOf(
                "emergencia ", "necesito ayuda ", "envía sos ", "envia sos ",
                "manda sos ", "manda una alerta "
            ).firstOrNull { t.contains(it) }
                ?.let { t.substringAfter(it).trim().trim(',', '.') }
                ?.ifBlank { null }
                ?: "¡Necesito ayuda!"

            val user = User(name = userName, phone = userPhone, age = userAge, familyCode = familyCode)

            if (familyCode.isBlank()) {
                return "No tienes familia vinculada. Ve a Perfil, Mi familia, y comparte tu código."
            }

            if (hasLocationPerm) {
                emergencyViewModel.sendSOS(user, msg)
                return "🆘 Enviando alerta de emergencia a tu familia con tu ubicación"
            } else {
                pendingSOS = msg
                locationPermLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
                return "🆘 Solicitando permiso de ubicación y enviando alerta"
            }
        }

        // "cancela la alerta" / "ya estoy bien" / "falsa alarma"
        if (t.contains("cancela la alerta") || t.contains("falsa alarma") ||
            t.contains("ya estoy bien") || t.contains("cancela el sos")) {
            emergencyViewModel.resetState()
            return "✅ Alerta cancelada. Me alegra que estés bien."
        }

        // "¿hay alertas de mi familia?"
        if (t.contains("alertas de mi familia") || t.contains("hay emergencias") ||
            t.contains("alguna alerta")) {
            val activas = emergencyViewModel.familyAlerts.value
            return if (activas.isEmpty()) "No hay alertas activas de tu familia"
            else {
                val nombres = activas.joinToString(", ") { it.userName.ifBlank { "un familiar" } }
                "⚠️ Hay ${activas.size} alerta${if (activas.size > 1) "s" else ""} activa${if (activas.size > 1) "s" else ""} de: $nombres"
            }
        }

        // ── MEDICAMENTOS ──────────────────────────────────────────────────

        val triggerAgrMed = listOf(
            "agrega el medicamento", "agregar el medicamento",
            "agrega la pastilla", "agregar la pastilla",
            "agrega la medicina", "agregar la medicina",
            "nuevo medicamento", "nueva pastilla", "nuevo recordatorio de"
        ).firstOrNull { t.contains(it) }

        if (triggerAgrMed != null) {
            val resto = t.substringAfter(triggerAgrMed).trim()
            val nombreRaw = resto
                .substringBefore(" a las ").substringBefore(" todos")
                .substringBefore(" lunes").substringBefore(" martes")
                .substringBefore(" miercoles").substringBefore(" miércoles")
                .substringBefore(" jueves").substringBefore(" viernes")
                .substringBefore(" sabado").substringBefore(" sábado")
                .substringBefore(" domingo").trim().trim(',', '.')
            val hora = parsearHora(resto)
            val dias = parsearDias(resto)
            return when {
                nombreRaw.isBlank() -> "Dime el nombre del medicamento. Por ejemplo: agrega la pastilla Omeprazol a las 8."
                hora == null -> "¿A qué hora tomas $nombreRaw?"
                else -> {
                    val med = Medication(
                        name = nombreRaw.replaceFirstChar { it.uppercase() },
                        hour = hora.first, minute = hora.second, days = dias, active = true
                    )
                    medicationViewModel.addMedication(med)
                    val diasStr = if (dias.size == 7) "todos los días"
                    else dias.joinToString(", ") { d -> DIA_A_CALENDAR.entries.firstOrNull { it.value == d }?.key ?: "" }
                    "Listo, programé ${med.name} a las %02d:%02d $diasStr".format(hora.first, hora.second)
                }
            }
        }

        val triggerElimMed = listOf(
            "elimina el medicamento", "eliminar el medicamento",
            "borra el medicamento", "borrar el medicamento",
            "elimina la pastilla", "eliminar la pastilla",
            "borra la pastilla", "quita el recordatorio de", "elimina el recordatorio de"
        ).firstOrNull { t.contains(it) }
        if (triggerElimMed != null) {
            val nombre = t.substringAfter(triggerElimMed).trim().trim(',', '.')
            if (nombre.isBlank()) return "¿Cuál medicamento quieres eliminar?"
            val eliminado = medicationViewModel.deleteMedicationByName(nombre)
            return if (eliminado != null) "Eliminé el recordatorio de $eliminado"
            else "No encontré un medicamento llamado $nombre"
        }

        if (t.contains("mis medicamentos") || t.contains("mis pastillas") ||
            t.contains("mis medicinas") || t.contains("qué medicamentos") ||
            t.contains("que medicamentos") || t.contains("mis recordatorios")) {
            if (medications.isEmpty()) return "No tienes recordatorios de medicamentos todavía."
            val lista = medications.joinToString(", ") { "${it.name} a las %02d:%02d".format(it.hour, it.minute) }
            return "Tus medicamentos son: $lista"
        }

        val triggerHoraMed = listOf("a qué hora tomo ", "a que hora tomo ", "cuándo tomo ", "cuando tomo ")
            .firstOrNull { t.contains(it) }
        if (triggerHoraMed != null) {
            val nombre = t.substringAfter(triggerHoraMed).trim().trim(',', '.')
            val med = medications.firstOrNull { it.name.lowercase().let { mn -> mn == nombre || mn.startsWith(nombre) || mn.contains(nombre) } }
            return if (med != null) "${med.name} lo tomas a las %02d:%02d".format(med.hour, med.minute)
            else "No encontré un medicamento llamado $nombre"
        }

        // ── CONTACTOS ─────────────────────────────────────────────────────

        val gAgregar = listOf("agrega a ","agregar a ","nuevo contacto ","agrega ","agregar ")
            .firstOrNull { t.contains(it) }
        if (gAgregar != null) {
            val resto = t.substringAfter(gAgregar).trim()
            val numero = extraerNumero(resto)
            val nombre = resto.substringBefore(" con").substringBefore(" numero")
                .substringBefore(" número").filter { !it.isDigit() }.trim().trim(',','.')
            return if (nombre.isBlank() || numero.isBlank())
                "Para agregar di: agrega a nombre con el número y el teléfono."
            else { contactsViewModel.addContact(nombre, numero); "Listo, agregué a $nombre" }
        }

        val gEliminar = listOf("elimina a ","eliminar a ","borra a ","borrar a ","elimina ","borra ")
            .firstOrNull { t.contains(it) }
        if (gEliminar != null) {
            val nombre = t.substringAfter(gEliminar).trim().trim(',','.')
            if (nombre.isBlank()) return "¿A quién quieres eliminar?"
            val eliminado = contactsViewModel.deleteContactByName(nombre)
            return if (eliminado != null) "Eliminé a $eliminado" else "No encontré a $nombre"
        }

        val gEditar = listOf("cambia el número de ","cambia el numero de ","edita a ","editar a ")
            .firstOrNull { t.contains(it) }
        if (gEditar != null) {
            val resto = t.substringAfter(gEditar).trim()
            val numero = extraerNumero(resto)
            val nombre = resto.substringBefore(" a ").substringBefore(" al ")
                .substringBefore(" numero").substringBefore(" número")
                .filter { !it.isDigit() }.trim().trim(',','.')
            return if (nombre.isBlank() || numero.isBlank())
                "Di: cambia el número de nombre a, y el nuevo teléfono."
            else {
                val ok = contactsViewModel.updateContactByName(nombre, numero)
                if (ok) "Actualicé el número de $nombre" else "No encontré a $nombre"
            }
        }

        val gLlamar = listOf("llama a ","llamar a ","márcale a ","marca a ","llama ","marca ")
            .firstOrNull { t.contains(it) }
        if (gLlamar != null) {
            val nombre = t.substringAfter(gLlamar).trim().trim(',','.')
            if (nombre.isBlank()) return "¿A quién quieres llamar?"
            val encontrado = contactosDisponibles().firstOrNull {
                val n = it.first.lowercase()
                n == nombre || n.startsWith(nombre) || n.contains(nombre)
            }
            return if (encontrado != null) { call(encontrado.second); "Llamando a ${encontrado.first}" }
            else "No encontré a $nombre en tus contactos"
        }

        if (t.contains("mis contactos") || t.contains("qué contactos") || t.contains("que contactos")) {
            val nombres = contactosDisponibles().joinToString(", ") { it.first }
            return if (nombres.isBlank()) "No tienes contactos con teléfono todavía"
            else "Tus contactos son: $nombres"
        }

        // ── PERFIL ────────────────────────────────────────────────────────

        if (t.contains("cambia mi nombre a ") || t.contains("mi nombre es ")) {
            val nuevo = (if (t.contains("cambia mi nombre a ")) t.substringAfter("cambia mi nombre a ")
            else t.substringAfter("mi nombre es ")).trim().trim(',','.')
            if (nuevo.isBlank()) return "¿Cómo te quieres llamar?"
            authViewModel.savePersonalInfo(nuevo, userAge, userPhone)
            return "Listo, ahora te llamas $nuevo"
        }
        if (t.contains("cambia mi teléfono a ") || t.contains("cambia mi telefono a ") ||
            t.contains("mi teléfono es ") || t.contains("mi telefono es ")) {
            val numero = extraerNumero(t)
            if (numero.isBlank()) return "Dime el número de teléfono"
            authViewModel.savePersonalInfo(userName, userAge, numero); return "Guardé tu teléfono"
        }
        if (t.contains("cambia mi edad a ") || (t.contains("tengo ") && t.contains("años"))) {
            val edad = extraerNumero(t)
            if (edad.isBlank()) return "Dime tu edad"
            authViewModel.savePersonalInfo(userName, edad, userPhone); return "Guardé tu edad: $edad años"
        }
        if (t.contains("cómo me llamo") || t.contains("como me llamo") || t.contains("mi nombre"))
            return if (userName.isNotBlank()) "Te llamas $userName" else "Aún no has guardado tu nombre"
        if (t.contains("mi teléfono") || t.contains("mi telefono") || t.contains("mi número") || t.contains("mi numero"))
            return if (userPhone.isNotBlank()) "Tu teléfono es $userPhone" else "Aún no has guardado tu teléfono"
        if (t.contains("mi edad") || t.contains("cuántos años") || t.contains("cuantos años"))
            return if (userAge.isNotBlank()) "Tienes $userAge años" else "Aún no has guardado tu edad"

        // ── FAMILIA ───────────────────────────────────────────────────────

        if (t.contains("mi código") || t.contains("mi codigo") ||
            t.contains("código de familia") || t.contains("codigo de familia"))
            return if (familyCode.isNotBlank())
                "Tu código de familia es: ${familyCode.toList().joinToString(", ")}"
            else "Aún no tienes código de familia"
        if (t.contains("sal de la familia") || t.contains("salir de la familia") ||
            t.contains("salirme de la familia")) {
            authViewModel.leaveFamily(); return "Saliste de la familia"
        }
        if (t.contains("cambia el nombre de la familia a ") || t.contains("la familia se llama ")) {
            val nuevo = (if (t.contains("cambia el nombre de la familia a "))
                t.substringAfter("cambia el nombre de la familia a ")
            else t.substringAfter("la familia se llama ")).trim().trim(',','.')
            if (nuevo.isBlank()) return "¿Cómo quieres llamar a la familia?"
            authViewModel.saveFamilyName(nuevo); return "La familia ahora se llama $nuevo"
        }
        if (t.contains("cómo se llama mi familia") || t.contains("como se llama mi familia") ||
            t.contains("nombre de mi familia"))
            return if (familyName.isNotBlank()) "Tu familia se llama $familyName"
            else "Tu familia aún no tiene nombre"
        if (t.contains("mis familiares") || t.contains("quiénes son mis familiares") ||
            t.contains("quienes son mis familiares") || t.contains("miembros")) {
            val nombres = members.map { it.name }.filter { it.isNotBlank() }.joinToString(", ")
            return if (nombres.isBlank()) "Aún no hay familiares vinculados" else "Tus familiares son: $nombres"
        }

        // ── GENERAL ───────────────────────────────────────────────────────
        return when {
            t.contains("hola") || t.contains("buenas") || t.contains("buenos días") ->
                "¡Hola${if (userName.isNotBlank()) ", $userName" else ""}! ¿En qué te ayudo?"
            t.contains("hora") ->
                "Son las " + SimpleDateFormat("hh:mm a", Locale("es")).format(Calendar.getInstance().time)
            t.contains("día") || t.contains("fecha") ->
                "Hoy es " + SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(Calendar.getInstance().time)
            t.contains("gracias") -> "¡De nada! Para eso estoy."
            t.contains("ayuda") || t.contains("qué puedes hacer") || t.contains("que puedes hacer") ->
                "Puedo enviar alertas SOS, gestionar contactos, programar medicamentos y más. " +
                "Por ejemplo di: emergencia, o envía SOS, o agrega la pastilla Omeprazol a las 8."
            t.isBlank() -> "No te entendí, inténtalo de nuevo."
            else -> "No entendí \"$frase\". Di \"ayuda\" para ver qué puedo hacer."
        }
    }

    // ── Reconocedor de voz ────────────────────────────────────────────────
    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context))
            SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(Unit) { onDispose { recognizer?.destroy() } }

    fun startListening() {
        if (recognizer == null) { speak("El reconocimiento de voz no está disponible"); return }
        heard = ""; answer = ""; listening = true
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""
                heard = text
                speak(procesarComando(text))
            }
            override fun onError(error: Int) { listening = false; speak("No te entendí, inténtalo de nuevo") }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            }
        )
    }

    val audioPremLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPerm = granted; if (granted) startListening() }

    // ── UI ────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Asistente de voz", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (listening) "Escuchando…" else "Toca el micrófono y habla",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                .clickable(enabled = !listening) {
                    if (hasAudioPerm) startListening()
                    else audioPremLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
            contentAlignment = Alignment.Center
        ) { Text("🎤", fontSize = 64.sp) }

        Spacer(Modifier.height(24.dp))

        if (heard.isNotBlank())  { InfoCard(label = "Escuché", text = heard); Spacer(Modifier.height(12.dp)) }
        if (answer.isNotBlank()) { InfoCard(label = "Cerca dice", text = answer, highlight = true); Spacer(Modifier.height(12.dp)) }

        Spacer(Modifier.height(8.dp))
        CommandExamples()
        Spacer(Modifier.height(16.dp))
    }
}

// ── Componentes UI ────────────────────────────────────────────────────────────

@Composable
private fun InfoCard(label: String, text: String, highlight: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (highlight) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun CommandExamples() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Ejemplos de comandos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))

            ExampleGroup("🆘", "Emergencia", listOf(
                "\"Emergencia\"",
                "\"SOS\"",
                "\"Necesito ayuda\""
            ))
            Spacer(Modifier.height(12.dp))
            ExampleGroup("💊", "Medicamentos", listOf(
                "\"Pastilla Omeprazol a las 8\"",
                "\"Elimina Omeprazol\"",
                "\"Mis medicamentos\""
            ))
            Spacer(Modifier.height(12.dp))
            ExampleGroup("📞", "Contactos", listOf(
                "\"Llama a mamá\"",
                "\"Agrega a Juan 8711234567\"",
                "\"Elimina a Pedro\""
            ))
            Spacer(Modifier.height(12.dp))
            ExampleGroup("👤", "Mi perfil", listOf(
                "\"Mi nombre es Ana\"",
                "\"Mi teléfono\"",
                "\"Mi código\""
            ))
        }
    }
}

@Composable
private fun ExampleGroup(emoji: String, title: String, examples: List<String>) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = emoji, fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        examples.forEach { ex ->
            Text(
                text = "•  $ex",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, top = 2.dp)
            )
        }
    }
}
