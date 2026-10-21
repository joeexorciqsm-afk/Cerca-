plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.cerca.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.cerca.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Reduce y ofusca el código (R8) y elimina recursos no usados:
            // genera un APK más pequeño y con menor huella de memoria.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        // Desactivamos features de build que no usamos (menos trabajo y tamaño).
        buildConfig = false
        resValues = false
        shaders = false
    }

    // Empaquetado: excluye metadatos innecesarios para aligerar el APK.
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Firebase: el BOM va primero y las librerías SIN versión (las gestiona el BOM).
    // Esto evita conflictos de versiones y mantiene solo lo que usamos (auth + firestore),
    // reduciendo el tamaño del APK y la huella de memoria.
    implementation(platform("com.google.firebase:firebase-bom:35.0.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}