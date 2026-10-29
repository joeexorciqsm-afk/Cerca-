-keep class com.cerca.app.model.** { *; }

-keepclassmembers class com.cerca.app.model.** {
    <init>();
    <fields>;
}
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes AnnotationDefault

-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlin.Metadata { *; }
