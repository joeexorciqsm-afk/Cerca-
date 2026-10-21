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
import com.cerca.app.viewmodel.AuthViewModel
import com.cerca.app.viewmodel.ContactsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun VoiceScreen(
    contactsViewModel: ContactsViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current

    val familyContacts by contactsViewModel.familyContacts.collectAsState()
    val myContacts by contactsViewModel.myContacts.collectAsState()

    // Datos del perfil/familia para que la voz pueda leerlos y modificarlos.
    val familyCode by authViewModel.familyCode.collectAsState()
    val familyName by authViewModel.familyName.collectAsState()
    val userName by authViewModel.userName.collectAsState()
    val userPhone by authViewModel.phone.collectAsState()
    val userAge by authViewModel.age.collectAsState()
    val members by authViewModel.familyMembers.collectAsState()

    LaunchedEffect(Unit) {
        contactsViewModel.loadContacts()
    }

    var heard by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    // Text-to-Speech para responder con voz clara.
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
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    fun speak(text: String) {
        answer = text
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "respuesta")
    }

    fun call(phone: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun contactosDisponibles(): List<Pair<String, String>> {
        val delaFamilia = familyContacts.map { it.name to it.phone }
        val manuales = myContacts.map { it.name to it.phone }
        return (delaFamilia + manuales).filter { it.second.isNotBlank() }
    }

    fun extraerNumero(texto: String): String = texto.filter { it.isDigit() }

    // Interpreta el comando y ejecuta la acción en el apartado correspondiente.
    fun procesarComando(frase: String): String {
        val t = frase.lowercase().trim()

        // ---------- CONTACTOS ----------
        // Agregar
        val gAgregar = listOf("agrega a ", "agregar a ", "nuevo contacto ", "agrega ", "agregar ")
            .firstOrNull { t.contains(it) }
        if (gAgregar != null) {
            val resto = t.substringAfter(gAgregar).trim()
            val numero = extraerNumero(resto)
            var nombre = resto.substringBefore(" con").substringBefore(" numero")
                .substringBefore(" número").filter { !it.isDigit() }.trim().trim(',', '.')
            return if (nombre.isBlank() || numero.isBlank())
                "Para agregar di: agrega a un nombre con el número, y el teléfono."
            else {
                contactsViewModel.addContact(nombre, numero)
                "Listo, agregué a $nombre"
            }
        }
        // Eliminar
        val gEliminar = listOf("elimina a ", "eliminar a ", "borra a ", "borrar a ", "elimina ", "borra ")
            .firstOrNull { t.contains(it) }
        if (gEliminar != null) {
            val nombre = t.substringAfter(gEliminar).trim().trim(',', '.')
            if (nombre.isBlank()) return "¿A quién quieres eliminar?"
            val eliminado = contactsViewModel.deleteContactByName(nombre)
            return if (eliminado != null) "Eliminé a $eliminado" else "No encontré a $nombre"
        }
        // Editar número
        val gEditar = listOf("cambia el número de ", "cambia el numero de ", "edita a ", "editar a ")
            .firstOrNull { t.contains(it) }
        if (gEditar != null) {
            val resto = t.substringAfter(gEditar).trim()
            val numero = extraerNumero(resto)
            var nombre = resto.substringBefore(" a ").substringBefore(" al ")
                .substringBefore(" numero").substringBefore(" número")
                .filter { !it.isDigit() }.trim().trim(',', '.')
            return if (nombre.isBlank() || numero.isBlank())
                "Di: cambia el número de un nombre a, y el nuevo teléfono."
            else {
                val ok = contactsViewModel.updateContactByName(nombre, numero)
                if (ok) "Actualicé el número de $nombre" else "No encontré a $nombre"
            }
        }
        // Llamar
        val gLlamar = listOf("llama a ", "llamar a ", "márcale a ", "marca a ", "llama ", "marca ")
            .firstOrNull { t.contains(it) }
        if (gLlamar != null) {
            val nombre = t.substringAfter(gLlamar).trim().trim(',', '.')
            if (nombre.isBlank()) return "¿A quién quieres llamar?"
            val encontrado = contactosDisponibles().firstOrNull {
                val n = it.first.lowercase()
                n == nombre || n.startsWith(nombre) || n.contains(nombre)
            }
            return if (encontrado != null) {
                call(encontrado.second); "Llamando a ${encontrado.first}"
            } else "No encontré a $nombre en tus contactos"
        }
        // Leer contactos
        if (t.contains("mis contactos") || t.contains("qué contactos") || t.contains("que contactos")) {
            val nombres = contactosDisponibles().joinToString(", ") { it.first }
            return if (nombres.isBlank()) "No tienes contactos con teléfono todavía"
            else "Tus contactos son: $nombres"
        }

        // ---------- PERFIL / INFORMACIÓN PERSONAL ----------
        // Cambiar mi nombre
        if (t.contains("cambia mi nombre a ") || t.contains("mi nombre es ")) {
            val nuevo = (if (t.contains("cambia mi nombre a ")) t.substringAfter("cambia mi nombre a ")
            else t.substringAfter("mi nombre es ")).trim().trim(',', '.')
            if (nuevo.isBlank()) return "¿Cómo te quieres llamar?"
            authViewModel.savePersonalInfo(nuevo, userAge, userPhone)
            return "Listo, ahora te llamas $nuevo"
        }
        // Cambiar mi teléfono
        if (t.contains("cambia mi teléfono a ") || t.contains("cambia mi telefono a ") ||
            t.contains("mi teléfono es ") || t.contains("mi telefono es ")) {
            val numero = extraerNumero(t)
            if (numero.isBlank()) return "Dime el número de teléfono"
            authViewModel.savePersonalInfo(userName, userAge, numero)
            return "Guardé tu teléfono"
        }
        // Cambiar mi edad
        if (t.contains("cambia mi edad a ") || t.contains("tengo ") && t.contains("años")) {
            val edad = extraerNumero(t)
            if (edad.isBlank()) return "Dime tu edad"
            authViewModel.savePersonalInfo(userName, edad, userPhone)
            return "Guardé tu edad: $edad años"
        }
        // Consultas de mi info
        if (t.contains("cómo me llamo") || t.contains("como me llamo") || t.contains("mi nombre"))
            return if (userName.isNotBlank()) "Te llamas $userName" else "Aún no has guardado tu nombre"
        if (t.contains("mi teléfono") || t.contains("mi telefono") || t.contains("mi número") || t.contains("mi numero"))
            return if (userPhone.isNotBlank()) "Tu teléfono es $userPhone" else "Aún no has guardado tu teléfono"
        if (t.contains("mi edad") || t.contains("cuántos años") || t.contains("cuantos años"))
            return if (userAge.isNotBlank()) "Tienes $userAge años" else "Aún no has guardado tu edad"

        // ---------- FAMILIA ----------
        // Leer / consultar código
        if (t.contains("mi código") || t.contains("mi codigo") || t.contains("código de familia") ||
            t.contains("codigo de familia")) {
            return if (familyCode.isNotBlank())
                "Tu código de familia es: ${familyCode.toList().joinToString(", ")}"
            else "Aún no tienes código de familia"
        }
        // Unirse a familia
        val gUnir = listOf("únete a la familia ", "unete a la familia ", "unirme a la familia ",
            "vincula con ", "vincular con ").firstOrNull { t.contains(it) }
        if (gUnir != null) {
            val codigo = t.substringAfter(gUnir).filter { it.isLetterOrDigit() }.uppercase()
            if (codigo.isBlank()) return "Dime el código de la familia"
            authViewModel.joinFamily(codigo)
            return "Intentando unirte a la familia $codigo"
        }
        // Salir de familia
        if (t.contains("sal de la familia") || t.contains("salir de la familia") ||
            t.contains("salirme de la familia")) {
            authViewModel.leaveFamily()
            return "Saliste de la familia"
        }
        // Nombre de la familia (consultar / cambiar)
        if (t.contains("cambia el nombre de la familia a ") || t.contains("la familia se llama ")) {
            val nuevo = (if (t.contains("cambia el nombre de la familia a "))
                t.substringAfter("cambia el nombre de la familia a ")
            else t.substringAfter("la familia se llama ")).trim().trim(',', '.')
            if (nuevo.isBlank()) return "¿Cómo quieres llamar a la familia?"
            authViewModel.saveFamilyName(nuevo)
            return "La familia ahora se llama $nuevo"
        }
        if (t.contains("cómo se llama mi familia") || t.contains("como se llama mi familia") ||
            t.contains("nombre de mi familia"))
            return if (familyName.isNotBlank()) "Tu familia se llama $familyName" else "Tu familia aún no tiene nombre"
        // Miembros
        if (t.contains("mis familiares") || t.contains("quiénes son mis familiares") ||
            t.contains("quienes son mis familiares") || t.contains("miembros")) {
            val nombres = members.map { it.name }.filter { it.isNotBlank() }.joinToString(", ")
            return if (nombres.isBlank()) "Aún no hay familiares vinculados"
            else "Tus familiares son: $nombres"
        }

        // ---------- GENERALES ----------
        return when {
            t.contains("hola") || t.contains("buenas") || t.contains("buenos días") ->
                "¡Hola${if (userName.isNotBlank()) ", $userName" else ""}! ¿En qué te ayudo?"
            t.contains("hora") -> "Son las " +
                SimpleDateFormat("hh:mm a", Locale("es")).format(Calendar.getInstance().time)
            t.contains("día") || t.contains("fecha") -> "Hoy es " +
                SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(Calendar.getInstance().time)
            t.contains("gracias") -> "¡De nada! Para eso estoy."
            t.contains("ayuda") || t.contains("qué puedes hacer") || t.contains("que puedes hacer") ->
                "Puedo llamar o gestionar contactos, decirte tu código de familia, " +
                    "unirte o salir de una familia, y cambiar tu información personal. " +
                    "Por ejemplo: llama a mamá."
            t.isBlank() -> "No te entendí, inténtalo de nuevo."
            else -> "No entendí \"$frase\". Di \"ayuda\" para saber qué puedo hacer."
        }
    }

    // Reconocedor de voz.
    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context))
            SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(Unit) { onDispose { recognizer?.destroy() } }

    fun startListening() {
        if (recognizer == null) {
            speak("El reconocimiento de voz no está disponible en este dispositivo")
            return
        }
        heard = ""; answer = ""; listening = true
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""
                heard = text
                speak(procesarComando(text))
            }
            override fun onError(error: Int) {
                listening = false
                speak("No te entendí, inténtalo de nuevo")
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
        }
        recognizer.startListening(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) startListening()
    }

    // ---------------- UI ----------------
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Asistente de voz",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (listening) "Escuchando…" else "Toca el micrófono y habla",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Botón de micrófono circular, grande y central.
        val micColor = if (listening) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(micColor)
                .clickable(enabled = !listening) {
                    if (hasPermission) startListening()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🎤", fontSize = 64.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tarjeta: lo que escuchó.
        if (heard.isNotBlank()) {
            InfoCard(label = "Escuché", text = heard)
            Spacer(modifier = Modifier.height(12.dp))
        }
        // Tarjeta: respuesta del asistente.
        if (answer.isNotBlank()) {
            InfoCard(
                label = "Cerca dice",
                text = answer,
                highlight = true
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Ejemplos de comandos, agrupados por apartado.
        CommandExamples()

        Spacer(modifier = Modifier.height(16.dp))
    }
}

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
            Spacer(modifier = Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun CommandExamples() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "¿Qué puedes decir?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            ExampleGroup(
                emoji = "📞",
                title = "Contactos",
                examples = listOf(
                    "\"Llama a mamá\"",
                    "\"Agrega a Juan con el número 871...\"",
                    "\"Cambia el número de Juan a 871...\"",
                    "\"Elimina a Pedro\"",
                    "\"Mis contactos\""
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            ExampleGroup(
                emoji = "👤",
                title = "Mi información",
                examples = listOf(
                    "\"Cambia mi nombre a Ana\"",
                    "\"Cambia mi teléfono a 871...\"",
                    "\"¿Cuál es mi teléfono?\""
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            ExampleGroup(
                emoji = "👨‍👩‍👧",
                title = "Familia",
                examples = listOf(
                    "\"¿Cuál es mi código?\"",
                    "\"Únete a la familia 4KDDGX\"",
                    "\"¿Quiénes son mis familiares?\"",
                    "\"Sal de la familia\""
                )
            )
        }
    }
}

@Composable
private fun ExampleGroup(emoji: String, title: String, examples: List<String>) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = emoji, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
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
