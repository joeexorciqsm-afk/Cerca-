# Reglas de R8/ProGuard para la app Cerca.
# Objetivo: reducir el tamaño sin romper Firebase/Firestore ni los modelos de datos.

# --- Modelos de datos (se serializan a/desde Firestore por reflexión) ---
# Mantener los campos y constructores de las clases del paquete model.
-keep class com.cerca.app.model.** { *; }

# --- Firebase Firestore ---
# Firestore usa reflexión para mapear documentos a objetos; conservamos lo necesario.
-keepclassmembers class com.cerca.app.model.** {
    <init>();
    <fields>;
}
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes AnnotationDefault

# Firebase ya incluye sus propias reglas consumidas automáticamente,
# estas son de refuerzo por si el shrinker es agresivo.
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# --- Kotlin / Coroutines ---
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlin.Metadata { *; }
