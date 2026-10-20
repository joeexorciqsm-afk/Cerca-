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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cerca.app.viewmodel.ContactsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun VoiceScreen(
    contactsViewModel: ContactsViewModel = viewModel()
) {
    val context = LocalContext.current

    val familyContacts by contactsViewModel.familyContacts.collectAsState()
    val myContacts by contactsViewModel.myContacts.collectAsState()

    // Cargamos los contactos para poder llamarlos por voz.
    LaunchedEffect(Unit) {
        contactsViewModel.loadContacts()
    }

    var heard by remember { mutableStateOf("") }      // lo que reconoció
    var answer by remember { mutableStateOf("") }     // la respuesta de la app
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

    // Realiza la llamada abriendo el marcador con el número.
    fun call(phone: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // Lista unificada de contactos (nombre -> teléfono) para buscar por voz.
    fun contactosDisponibles(): List<Pair<String, String>> {
        val delaFamilia = familyContacts.map { it.name to it.phone }
        val manuales = myContacts.map { it.name to it.phone }
        return (delaFamilia + manuales).filter { it.second.isNotBlank() }
    }

    // Interpreta el comando. Devuelve la respuesta hablada y, si aplica, hace la llamada.
    fun procesarComando(frase: String): String {
        val texto = frase.lowercase().trim()

        // Comando de llamada: "llama a X", "llamar a X", "marca a X".
        val gatillos = listOf("llama a ", "llamar a ", "llámale a ", "marca a ", "llamar ", "llama ")
        val gatillo = gatillos.firstOrNull { texto.contains(it) }
        if (gatillo != null) {
            val nombreBuscado = texto.substringAfter(gatillo).trim()
            if (nombreBuscado.isBlank()) {
                return "¿A quién quieres llamar?"
            }
            val contactos = contactosDisponibles()
            // Busca por coincidencia: el nombre del contacto empieza o contiene lo dicho.
            val encontrado = contactos.firstOrNull {
                val n = it.first.lowercase()
                n == nombreBuscado || n.startsWith(nombreBuscado) || n.contains(nombreBuscado)
            }
            return if (encontrado != null) {
                call(encontrado.second)
                "Llamando a ${encontrado.first}"
            } else {
                "No encontré a $nombreBuscado en tus contactos"
            }
        }

        return when {
            texto.contains("hola") || texto.contains("buenos días") || texto.contains("buenas") ->
                "¡Hola! Qué gusto escucharte."

            texto.contains("hora") -> {
                val hora = SimpleDateFormat("hh:mm a", Locale("es")).format(Calendar.getInstance().time)
                "Son las $hora"
            }

            texto.contains("día") || texto.contains("fecha") -> {
                val fecha = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es"))
                    .format(Calendar.getInstance().time)
                "Hoy es $fecha"
            }

            texto.contains("contacto") -> {
                val nombres = contactosDisponibles().joinToString(", ") { it.first }
                if (nombres.isBlank()) "No tienes contactos con teléfono todavía"
                else "Tus contactos son: $nombres"
            }

            texto.contains("gracias") -> "¡De nada! Para eso estoy."

            texto.contains("ayuda") ->
                "Puedes decir: llama a un nombre, qué hora es, qué día es, o mis contactos."

            texto.isBlank() -> "No te entendí, inténtalo de nuevo."

            else -> "Dijiste: $frase. Prueba diciendo: llama a un nombre."
        }
    }

    // Reconocedor de voz.
    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }
    DisposableEffect(Unit) {
        onDispose { recognizer?.destroy() }
    }

    fun startListening() {
        if (recognizer == null) {
            speak("El reconocimiento de voz no está disponible en este dispositivo")
            return
        }
        heard = ""
        answer = ""
        listening = true

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                listening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
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
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
        }
        recognizer.startListening(intent)
    }

    // Pide permiso de micrófono si no lo tiene.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) startListening()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Comandos de voz",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (listening) "Escuchando…" else "Pulsa el botón y habla",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (hasPermission) {
                    startListening()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            enabled = !listening,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Text(
                text = "🎤 Hablar",
                fontSize = 22.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (heard.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Escuché:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(heard, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (answer.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Respuesta:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(answer, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Prueba diciendo:\n" +
                "\"llama a [nombre]\", \"mis contactos\",\n" +
                "\"qué hora es\", \"qué día es\" o \"ayuda\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
