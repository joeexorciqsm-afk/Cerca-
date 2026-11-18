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

        // Llamar al 911 directamente
        if (t.contains("llama al 911") || t.contains("llama a 911") ||
            t.contains("marca el 911") || t.trim() == "911" ||
            t.contains("llama al nueve uno uno") || t.contains("servicios de emergencia") ||
            t.contains("llama a emergencias") || t.contains("ambulancia") ||
            t.contains("llama a la ambulancia") || t.contains("llama a la policía") ||
            t.contains("llama a la policia") || t.contains("llama a los bomberos")) {
            emergencyViewModel.call911()
            return "📞 Llamando al 911"
        }

        // Modo conducción / accidente / carretera
        if (t.contains("emergencia en carretera") || t.contains("accidente") ||
            t.contains("choque") || t.contains("choqué") || t.contains("choque de carro") ||
            t.contains("modo conducción") || t.contains("modo conduccion") ||
            t.contains("estoy manejando") || t.contains("estoy conduciendo") ||
            t.contains("volcadura") || t.contains("me chocaron") ||
            t.contains("atropellé") || t.contains("atropellaron") ||
            t.contains("accidente de tráfico") || t.contains("accidente de auto")) {
            val user = User(name = userName, phone = userPhone, age = userAge, familyCode = familyCode)
            if (familyCode.isBlank()) return "No tienes familia vinculada."
            // Capturar el mensaje completo como contexto
            val drivingMsg = when {
                t.contains("choqué") || t.contains("me chocaron") || t.contains("choque") -> "Tuve un choque"
                t.contains("volcadura") -> "Volcadura de vehículo"
                t.contains("atropellé") || t.contains("atropellaron") -> "Atropellamiento"
                else -> "Emergencia en carretera"
            }.let { base ->
                // Si hay más contexto en la frase, agregarlo
                val extras = listOf("accidente ", "choque ", "choqué ", "emergencia en carretera ")
                    .firstOrNull { t.contains(it) }
                    ?.let { t.substringAfter(it).trim().trim(',', '.') }
                if (!extras.isNullOrBlank()) "$base — $extras" else base
            }
            if (hasLocationPerm) {
                emergencyViewModel.sendSOS(user, drivingMsg, "driving")
                return "🚗 Alerta enviada: \"$drivingMsg\""
            } else {
                pendingSOS = drivingMsg
                locationPermLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                return "🚗 Solicitando ubicación y enviando alerta"
            }
        }

        // "envía SOS" / "manda alerta" / "emergencia" / "ayuda"
        val triggerSOS = listOf(
            "envía sos", "envia sos", "manda sos", "enviar sos",
            "manda una alerta", "envía una alerta", "envia una alerta",
            "emergencia", "necesito ayuda", "activa el sos", "activa sos", "sos",
            "socorro", "auxilio", "pide ayuda", "llama a mi familia",
            "avisa a mi familia", "alerta a mi familia", "manda alerta",
            "estoy en peligro", "me caí", "me caí y no me puedo levantar",
            "me siento mal", "me desmayé", "me desmaye", "no me siento bien",
            "tengo una emergencia", "hay una emergencia", "necesito asistencia",
            "vine al suelo", "me lastimé", "me lastime", "me golpeé",
            "me duele el pecho", "creo que me da un infarto", "me falta el aire"
        ).firstOrNull { t.contains(it) }

        if (triggerSOS != null) {
            // Extraer mensaje personalizado: todo lo que viene DESPUÉS de la palabra clave
            // Ej: "SOS me quiere dar un infarto" → "me quiere dar un infarto"
            // Ej: "emergencia me caí en casa" → "me caí en casa"
            val msg = run {
                // Intentar extraer el contexto después del trigger
                val afterTrigger = t.substringAfter(triggerSOS).trim().trim(',', '.')
                // Si hay texto después del trigger, es el mensaje personalizado
                if (afterTrigger.isNotBlank()) {
                    afterTrigger.replaceFirstChar { it.uppercase() }
                } else {
                    // Si el trigger ES el mensaje completo, usar como mensaje
                    // según cuál fue el trigger
                    when (triggerSOS) {
                        "me caí", "me caí y no me puedo levantar" -> "Me caí y necesito ayuda"
                        "me siento mal", "no me siento bien" -> "Me siento mal"
                        "me desmayé", "me desmaye" -> "Me desmayé"
                        "me duele el pecho" -> "Me duele el pecho"
                        "creo que me da un infarto" -> "Creo que me está dando un infarto"
                        "me falta el aire" -> "Me falta el aire, necesito ayuda"
                        "me lastimé", "me lastime" -> "Me lastimé, necesito ayuda"
                        "me golpeé" -> "Me golpeé, necesito ayuda"
                        "vine al suelo" -> "Me caí al suelo"
                        "estoy en peligro" -> "Estoy en peligro"
                        "socorro", "auxilio" -> "¡Necesito ayuda urgente!"
                        else -> "¡Necesito ayuda!"
                    }
                }
            }

            val user = User(name = userName, phone = userPhone, age = userAge, familyCode = familyCode)
            if (familyCode.isBlank()) return "No tienes familia vinculada. Ve a Perfil, Mis familias."

            if (hasLocationPerm) {
                emergencyViewModel.sendSOS(user, msg)
                return "🆘 Enviando: \"$msg\""
            } else {
                pendingSOS = msg
                locationPermLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                return "🆘 Solicitando ubicación y enviando alerta"
            }
        }

        if (t.contains("cancela la alerta") || t.contains("falsa alarma") ||
            t.contains("ya estoy bien") || t.contains("cancela el sos") ||
            t.contains("cancela la emergencia") || t.contains("estoy bien") ||
            t.contains("fue un error") || t.contains("no es nada") ||
            t.contains("todo bien") || t.contains("ya pasó") || t.contains("ya paso")) {
            emergencyViewModel.resetState()
            return "✅ Alerta cancelada"
        }

        if (t.contains("alertas de mi familia") || t.contains("hay emergencias") ||
            t.contains("alguna alerta") || t.contains("pasó algo") || t.contains("paso algo") ||
            t.contains("está bien mi familia") || t.contains("esta bien mi familia") ||
            t.contains("cómo está mi familia") || t.contains("como esta mi familia")) {
            val activas = emergencyViewModel.familyAlerts.value
            return if (activas.isEmpty()) "No hay alertas activas de tu familia, todo bien"
            else "⚠️ ${activas.size} alerta${if (activas.size > 1) "s" else ""} de: ${activas.joinToString(", ") { it.userName }}"
        }

        // ── MEDICAMENTOS ──────────────────────────────────────────────────

        val triggerAgrMed = listOf(
            "agrega el medicamento", "agregar el medicamento",
            "agrega la pastilla", "agregar la pastilla",
            "agrega la medicina", "agregar la medicina",
            "nuevo medicamento", "nueva pastilla", "nuevo recordatorio de",
            "pon un recordatorio de", "ponme un recordatorio",
            "recuérdame tomar", "recuerdame tomar",
            "programa el medicamento", "programa la pastilla",
            "necesito tomar", "quiero recordar tomar",
            "añade el medicamento", "añade la pastilla",
            "registra el medicamento", "registra la pastilla"
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
            "borra la pastilla", "quita el recordatorio de", "elimina el recordatorio de",
            "cancela el medicamento", "cancela la pastilla",
            "ya no tomo", "ya no voy a tomar", "quita la medicina",
            "borra la medicina", "elimina la medicina", "ya no necesito"
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
            t.contains("que medicamentos") || t.contains("mis recordatorios") ||
            t.contains("qué pastillas tomo") || t.contains("que pastillas tomo") ||
            t.contains("qué tengo que tomar") || t.contains("que tengo que tomar") ||
            t.contains("mis medicinas del día") || t.contains("mis pills") ||
            t.contains("cuáles son mis medicamentos") || t.contains("cuales son mis medicamentos") ||
            t.contains("qué medicinas tengo") || t.contains("que medicinas tengo")) {
            if (medications.isEmpty()) return "No tienes recordatorios de medicamentos todavía."
            val lista = medications.joinToString(", ") { "${it.name} a las %02d:%02d".format(it.hour, it.minute) }
            return "Tus medicamentos son: $lista"
        }

        val triggerHoraMed = listOf(
            "a qué hora tomo ", "a que hora tomo ",
            "cuándo tomo ", "cuando tomo ",
            "a qué hora es ", "a que hora es ",
            "qué hora debo tomar ", "que hora debo tomar ",
            "cuándo me toca ", "cuando me toca ",
            "a qué hora toca tomar ", "a que hora toca tomar "
        ).firstOrNull { t.contains(it) }
        if (triggerHoraMed != null) {
            val nombre = t.substringAfter(triggerHoraMed).trim().trim(',', '.')
            val med = medications.firstOrNull { it.name.lowercase().let { mn -> mn == nombre || mn.startsWith(nombre) || mn.contains(nombre) } }
            return if (med != null) "${med.name} lo tomas a las %02d:%02d".format(med.hour, med.minute)
            else "No encontré un medicamento llamado $nombre"
        }

        // ── CONTACTOS ─────────────────────────────────────────────────────

        val gAgregar = listOf(
            "agrega a ","agregar a ","nuevo contacto ","agrega ","agregar ",
            "guarda a ","guardar a ","registra a ","registrar a ",
            "añade a ","añadir a ","anota a ","anotar a "
        ).firstOrNull { t.contains(it) }
        if (gAgregar != null) {
            val resto = t.substringAfter(gAgregar).trim()
            val numero = extraerNumero(resto)
            val nombre = resto.substringBefore(" con").substringBefore(" numero")
                .substringBefore(" número").filter { !it.isDigit() }.trim().trim(',','.')
            return if (nombre.isBlank() || numero.isBlank())
                "Para agregar di: agrega a nombre con el número y el teléfono."
            else { contactsViewModel.addContact(nombre, numero); "Listo, agregué a $nombre" }
        }

        val gEliminar = listOf(
            "elimina a ","eliminar a ","borra a ","borrar a ","elimina ","borra ",
            "quita a ","quitar a ","borra el contacto de ","elimina el contacto de ",
            "ya no quiero a ","borra de mis contactos a "
        ).firstOrNull { t.contains(it) }
        if (gEliminar != null) {
            val nombre = t.substringAfter(gEliminar).trim().trim(',','.')
            if (nombre.isBlank()) return "¿A quién quieres eliminar?"
            val eliminado = contactsViewModel.deleteContactByName(nombre)
            return if (eliminado != null) "Eliminé a $eliminado" else "No encontré a $nombre"
        }

        val gEditar = listOf(
            "cambia el número de ","cambia el numero de ","edita a ","editar a ",
            "actualiza el número de ","actualiza el telefono de ",
            "nuevo número de ","nuevo numero de ",
            "el teléfono de ","el telefono de "
        ).firstOrNull { t.contains(it) }
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

        val gLlamar = listOf(
            "llama a ","llamar a ","márcale a ","marca a ","llama ","marca ",
            "habla con ","hablar con ","comunícame con ","comunícame a ",
            "quiero hablar con ","quiero llamar a ","ponme con ",
            "marcar a ","marcarle a ","telefonea a ","llámale a "
        ).firstOrNull { t.contains(it) }
        if (gLlamar != null) {
            val nombre = t.substringAfter(gLlamar).trim().trim(',','.')
            if (nombre.isBlank()) return "¿A quién quieres llamar?"
            // Busca primero en contactos de la app, luego en la agenda del teléfono
            val encontrado = contactosDisponibles().firstOrNull {
                val n = it.first.lowercase()
                n == nombre || n.startsWith(nombre) || n.contains(nombre)
            }
            if (encontrado != null) { call(encontrado.second); return "Llamando a ${encontrado.first}" }

            val delTelefono = contactsViewModel.findPhoneContact(nombre)
            if (delTelefono != null) { call(delTelefono.primaryPhone); return "Llamando a ${delTelefono.name}" }

            return "No encontré a $nombre en tus contactos"
        }

        if (t.contains("mis contactos") || t.contains("qué contactos") || t.contains("que contactos") ||
            t.contains("quiénes tengo en contactos") || t.contains("quienes tengo en contactos") ||
            t.contains("lista de contactos") || t.contains("mis números") ||
            t.contains("mis numeros") || t.contains("a quién puedo llamar") ||
            t.contains("a quien puedo llamar")) {
            val nombres = contactosDisponibles().joinToString(", ") { it.first }
            return if (nombres.isBlank()) "No tienes contactos con teléfono todavía"
            else "Tus contactos son: $nombres"
        }

        // ── PERFIL ────────────────────────────────────────────────────────

        if (t.contains("cambia mi nombre a ") || t.contains("mi nombre es ") ||
            t.contains("ahora me llamo ") || t.contains("me llamo ") ||
            t.contains("quiero que me llames ") || t.contains("cámbiate mi nombre a ")) {
            val nuevo = listOf("cambia mi nombre a ", "mi nombre es ", "ahora me llamo ",
                "me llamo ", "quiero que me llames ", "cámbiate mi nombre a ")
                .firstOrNull { t.contains(it) }
                ?.let { t.substringAfter(it) }
                ?.trim()?.trim(',','.')
                ?: ""
            if (nuevo.isBlank()) return "¿Cómo te quieres llamar?"
            authViewModel.savePersonalInfo(nuevo, userAge, userPhone)
            return "Listo, ahora te llamas $nuevo"
        }
        if (t.contains("cambia mi teléfono a ") || t.contains("cambia mi telefono a ") ||
            t.contains("mi teléfono es ") || t.contains("mi telefono es ") ||
            t.contains("mi número es ") || t.contains("mi numero es ") ||
            t.contains("actualiza mi teléfono") || t.contains("nuevo teléfono") ||
            t.contains("cambié mi teléfono") || t.contains("cambie mi telefono")) {
            val numero = extraerNumero(t)
            if (numero.isBlank()) return "Dime el número de teléfono"
            authViewModel.savePersonalInfo(userName, userAge, numero); return "Guardé tu teléfono"
        }
        if (t.contains("cambia mi edad a ") || (t.contains("tengo ") && t.contains("años")) ||
            t.contains("mi edad es ") || t.contains("cumplo ") ||
            t.contains("actualiza mi edad") || t.contains("ya tengo ")) {
            val edad = extraerNumero(t)
            if (edad.isBlank()) return "Dime tu edad"
            authViewModel.savePersonalInfo(userName, edad, userPhone); return "Guardé tu edad: $edad años"
        }
        if (t.contains("cómo me llamo") || t.contains("como me llamo") ||
            t.contains("mi nombre") || t.contains("cuál es mi nombre") ||
            t.contains("cual es mi nombre") || t.contains("dime mi nombre") ||
            t.contains("quién soy") || t.contains("quien soy"))
            return if (userName.isNotBlank()) "Te llamas $userName" else "Aún no has guardado tu nombre"
        if (t.contains("mi teléfono") || t.contains("mi telefono") ||
            t.contains("mi número") || t.contains("mi numero") ||
            t.contains("cuál es mi teléfono") || t.contains("cual es mi telefono") ||
            t.contains("cuál es mi número") || t.contains("cual es mi numero") ||
            t.contains("dime mi número") || t.contains("dime mi telefono"))
            return if (userPhone.isNotBlank()) "Tu teléfono es $userPhone" else "Aún no has guardado tu teléfono"
        if (t.contains("mi edad") || t.contains("cuántos años") || t.contains("cuantos años") ||
            t.contains("cuántos años tengo") || t.contains("cuantos años tengo") ||
            t.contains("qué edad tengo") || t.contains("que edad tengo"))
            return if (userAge.isNotBlank()) "Tienes $userAge años" else "Aún no has guardado tu edad"

        // Info médica por voz
        if (t.contains("mi tipo de sangre") || t.contains("mi sangre") ||
            t.contains("cuál es mi tipo de sangre") || t.contains("cual es mi tipo de sangre") ||
            t.contains("qué tipo de sangre tengo") || t.contains("que tipo de sangre tengo")) {
            val bt = authViewModel.bloodType.value
            return if (bt.isNotBlank()) "Tu tipo de sangre es $bt" else "No tienes tipo de sangre guardado"
        }
        if (t.contains("mis alergias") || t.contains("tengo alergias") ||
            t.contains("cuáles son mis alergias") || t.contains("cuales son mis alergias") ||
            t.contains("a qué soy alérgico") || t.contains("a que soy alergico") ||
            t.contains("qué me causa alergia") || t.contains("que me causa alergia")) {
            val al = authViewModel.allergies.value
            return if (al.isNotBlank()) "Tus alergias: $al" else "No tienes alergias guardadas"
        }
        if (t.contains("mis padecimientos") || t.contains("mis enfermedades") ||
            t.contains("mi condición") || t.contains("mi condicion") ||
            t.contains("qué enfermedades tengo") || t.contains("que enfermedades tengo") ||
            t.contains("cuáles son mis enfermedades") || t.contains("cuales son mis enfermedades") ||
            t.contains("mi historial médico") || t.contains("mi historial medico") ||
            t.contains("de qué padezco") || t.contains("de que padezco")) {
            val cond = authViewModel.conditions.value
            return if (cond.isNotBlank()) "Tus padecimientos: $cond" else "No tienes padecimientos guardados"
        }

        // ── FAMILIA ───────────────────────────────────────────────────────

        if (t.contains("mi código") || t.contains("mi codigo") ||
            t.contains("código de familia") || t.contains("codigo de familia") ||
            t.contains("cuál es mi código") || t.contains("cual es mi codigo") ||
            t.contains("dime mi código") || t.contains("mi clave de familia") ||
            t.contains("código para invitar") || t.contains("codigo para invitar"))
            return if (familyCode.isNotBlank())
                "Tu código de familia es: ${familyCode.toList().joinToString(", ")}"
            else "Aún no tienes código de familia"
        if (t.contains("sal de la familia") || t.contains("salir de la familia") ||
            t.contains("salirme de la familia") || t.contains("abandona la familia") ||
            t.contains("déjame salir de la familia") || t.contains("dejame salir de la familia") ||
            t.contains("quiero salir de la familia")) {
            authViewModel.leaveFamily(familyCode); return "Saliste de la familia"
        }
        if (t.contains("cambia el nombre de la familia a ") || t.contains("la familia se llama ")) {
            val nuevo = (if (t.contains("cambia el nombre de la familia a "))
                t.substringAfter("cambia el nombre de la familia a ")
            else t.substringAfter("la familia se llama ")).trim().trim(',','.')
            if (nuevo.isBlank()) return "¿Cómo quieres llamar a la familia?"
            authViewModel.saveFamilyName(nuevo); return "La familia ahora se llama $nuevo"
        }
        if (t.contains("cómo se llama mi familia") || t.contains("como se llama mi familia") ||
            t.contains("nombre de mi familia") || t.contains("cuál es el nombre de mi familia") ||
            t.contains("cual es el nombre de mi familia") || t.contains("cómo se llama la familia") ||
            t.contains("como se llama la familia"))
            return if (familyName.isNotBlank()) "Tu familia se llama $familyName"
            else "Tu familia aún no tiene nombre"
        if (t.contains("mis familiares") || t.contains("quiénes son mis familiares") ||
            t.contains("quienes son mis familiares") || t.contains("miembros") ||
            t.contains("quién está en mi familia") || t.contains("quien esta en mi familia") ||
            t.contains("quiénes están en mi familia") || t.contains("quienes estan en mi familia") ||
            t.contains("mi familia") || t.contains("lista de familiares") ||
            t.contains("quiénes son") || t.contains("quienes son")) {
            val nombres = members.map { it.name }.filter { it.isNotBlank() }.joinToString(", ")
            return if (nombres.isBlank()) "Aún no hay familiares vinculados" else "Tus familiares son: $nombres"
        }

        // ── GENERAL ───────────────────────────────────────────────────────
        return when {
            t.contains("hola") || t.contains("buenas") || t.contains("buenos días") ||
            t.contains("buenas tardes") || t.contains("buenas noches") ||
            t.contains("hey") || t.contains("qué tal") || t.contains("que tal") ||
            t.contains("cómo estás") || t.contains("como estas") ->
                "¡Hola${if (userName.isNotBlank()) ", $userName" else ""}! ¿En qué te ayudo?"
            t.contains("hora") || t.contains("qué hora es") || t.contains("que hora es") ||
            t.contains("dime la hora") ->
                "Son las " + SimpleDateFormat("hh:mm a", Locale("es")).format(Calendar.getInstance().time)
            t.contains("día") || t.contains("fecha") || t.contains("qué día es") ||
            t.contains("que dia es") || t.contains("hoy es") ->
                "Hoy es " + SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(Calendar.getInstance().time)
            t.contains("gracias") || t.contains("muchas gracias") || t.contains("te lo agradezco") ->
                "¡De nada! Para eso estoy."
            t.contains("ayuda") || t.contains("qué puedes hacer") || t.contains("que puedes hacer") ||
            t.contains("qué sabes hacer") || t.contains("que sabes hacer") ||
            t.contains("cuáles son tus funciones") || t.contains("cuales son tus funciones") ||
            t.contains("qué comandos hay") || t.contains("que comandos hay") ->
                "Puedo enviar alertas SOS, llamar al 911, gestionar contactos, programar medicamentos, " +
                "decirte tu información médica y de familia. Di: emergencia, llama a mamá, " +
                "o pastilla Omeprazol a las 8."
            t.isBlank() -> "No te escuché, inténtalo de nuevo."
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
                "\"Llama al 911\"",
                "\"Accidente\"  /  \"Choque\""
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
                "\"Mi tipo de sangre\"",
                "\"Mis padecimientos\""
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
