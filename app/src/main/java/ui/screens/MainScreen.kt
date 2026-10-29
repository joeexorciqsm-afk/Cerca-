package com.cerca.app.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cerca.app.viewmodel.AuthViewModel
import com.cerca.app.viewmodel.ContactsViewModel

@Composable
fun MainScreen(
    authViewModel: AuthViewModel = viewModel(),
    onLogout: () -> Unit = {},
    startOnVoice: Boolean = false
) {
    val contactsViewModel: ContactsViewModel = viewModel()

    var selectedTab by remember { mutableStateOf(if (startOnVoice) 1 else 0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    alwaysShowLabel = true,
                    icon = { Text("📞", fontSize = 24.sp) },
                    label = { Text("Contactos", fontSize = 14.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    alwaysShowLabel = true,
                    icon = { Text("🎤", fontSize = 24.sp) },
                    label = { Text("Voz", fontSize = 14.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    alwaysShowLabel = true,
                    icon = { Text("👤", fontSize = 24.sp) },
                    label = { Text("Perfil", fontSize = 14.sp) }
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ContactsScreen(viewModel = contactsViewModel)
                1 -> VoiceScreen(
                    contactsViewModel = contactsViewModel,
                    authViewModel = authViewModel
                )
                else -> HomeScreen(
                    viewModel = authViewModel,
                    onLogout = onLogout
                )
            }
        }
    }
}
