# Cerca

App Android (Kotlin + Jetpack Compose) para mantener la comunicación familiar.

## Estado actual — Sprint 1 (Autenticación)

Funcionalidad F01: **Login + registro de usuarios con Firebase.**

- Registro con nombre, email y contraseña (Firebase Authentication).
- Al registrarse se crea el perfil del usuario en Firestore (colección `users`),
  base para la futura vinculación familiar.
- Inicio de sesión con email y contraseña.
- Validaciones básicas: campos vacíos, mínimo 6 caracteres, confirmación de contraseña.
- Estados de carga y mensajes de error visibles en pantalla.
- Pantalla de inicio simple tras autenticarse, con opción de cerrar sesión.
- Si ya hay sesión activa, la app entra directo (sin volver a pedir login).

## Configuración de Firebase (necesaria para ejecutar)

El proyecto usa Firebase, por lo que necesitas tu propio archivo `google-services.json`:

1. Crea un proyecto en [Firebase Console](https://console.firebase.google.com/).
2. Agrega una app Android con el package `com.cerca.app`.
3. Habilita **Authentication → Email/Password**.
4. Crea una base de datos **Firestore**.
5. Descarga `google-services.json` y colócalo en la carpeta `app/`.

> Sin `google-services.json` el proyecto no compila. Ese archivo no se sube al
> repositorio por seguridad.

## Estructura

- `model/User.kt` — modelo de usuario.
- `viewmodel/AuthViewModel.kt` — lógica de login, registro y sesión.
- `ui/screens/` — pantallas: `LoginScreen`, `SignupScreen`, `HomeScreen`, `AuthScreen`.
