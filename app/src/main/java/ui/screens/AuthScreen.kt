package com.cerca.app.ui.screens

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cerca.app.viewmodel.AuthState
import com.cerca.app.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = viewModel()
) {
    val authState by viewModel.authState.collectAsState()
    var showSignup by remember { mutableStateOf(false) }

    if (authState is AuthState.Authenticated) {
        HomeScreen(onLogout = { viewModel.logout() })
    } else {
        if (showSignup) {
            SignupScreen(
                viewModel = viewModel,
                onLoginClick = { showSignup = false }
            )
        } else {
            LoginScreen(
                viewModel = viewModel,
                onSignupClick = { showSignup = true }
            )
        }
    }
}
