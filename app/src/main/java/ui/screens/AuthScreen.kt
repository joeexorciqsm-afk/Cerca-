package com.cerca.app.ui.screens

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cerca.app.viewmodel.AuthState
import com.cerca.app.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = viewModel(),
    startOnVoice: Boolean = false
) {
    val authState by viewModel.authState.collectAsState()
    var showSignup by remember { mutableStateOf(false) }

    // Muestra la pantalla de permisos una sola vez tras autenticarse
    var permissionsGranted by remember { mutableStateOf(false) }

    if (authState is AuthState.Authenticated) {
        if (!permissionsGranted) {
            PermissionsScreen(onAllGranted = { permissionsGranted = true })
        } else {
            MainScreen(
                authViewModel = viewModel,
                onLogout = {
                    viewModel.logout()
                    permissionsGranted = false   // volver a mostrar permisos en el próximo login
                },
                startOnVoice = startOnVoice
            )
        }
    } else {
        if (showSignup) {
            SignupScreen(
                viewModel    = viewModel,
                onLoginClick = { showSignup = false }
            )
        } else {
            LoginScreen(
                viewModel     = viewModel,
                onSignupClick = { showSignup = true }
            )
        }
    }
}
