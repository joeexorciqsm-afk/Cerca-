package com.cerca.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cerca.app.ui.screens.AuthScreen
import com.cerca.app.ui.theme.CercaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si se abrió desde el atajo de voz, arrancamos en la pestaña de Voz.
        val startOnVoice = intent?.getBooleanExtra(EXTRA_START_VOICE, false) ?: false

        setContent {
            CercaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuthScreen(startOnVoice = startOnVoice)
                }
            }
        }
    }

    companion object {
        const val EXTRA_START_VOICE = "start_voice"
    }
}
