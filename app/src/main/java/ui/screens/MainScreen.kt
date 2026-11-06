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
import com.cerca.app.viewmodel.MedicationViewModel

@Composable
fun MainScreen(
    authViewModel: AuthViewModel = viewModel(),
    onLogout: () -> Unit = {},
    startOnVoice: Boolean = false
) {
    val contactsViewModel: ContactsViewModel = viewModel()
    val medicationViewModel: MedicationViewModel = viewModel()

    var selectedTab by remember { mutableStateOf(if (startOnVoice) 1 else 0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    alwaysShowLabel = true,
                    icon = { Text("📞", fontSize = 22.sp) },
                    label = { Text("Contactos", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    alwaysShowLabel = true,
                    icon = { Text("🎤", fontSize = 22.sp) },
                    label = { Text("Voz", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    alwaysShowLabel = true,
                    icon = { Text("💊", fontSize = 22.sp) },
                    label = { Text("Meds", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    alwaysShowLabel = true,
                    icon = { Text("🏠", fontSize = 22.sp) },
                    label = { Text("Familia", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    alwaysShowLabel = true,
                    icon = { Text("👤", fontSize = 22.sp) },
                    label = { Text("Perfil", fontSize = 11.sp) }
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
                2 -> MedicationsScreen(viewModel = medicationViewModel)
                3 -> FamilyDashboardScreen(viewModel = authViewModel)
                else -> HomeScreen(
                    viewModel = authViewModel,
                    onLogout = onLogout
                )
            }
        }
    }
}
