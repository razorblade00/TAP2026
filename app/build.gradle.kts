import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

// Leer las credenciales de Supabase
val localProperties = Properties()

val localPropertiesFile = rootProject.file("local.properties")

if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { input ->
        localProperties.load(input)
    }
}

// Obtener URL y clave pública
val supabaseUrl = localProperties.getProperty("SUPABASE_URL", "")
val supabaseKey = localProperties.getProperty("SUPABASE_PUBLISHABLE_KEY", "")

android {
    namespace = "com.example.fakesapp"

    // Habilitar BuildConfig
    buildFeatures {
        buildConfig = true
    }

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.fakesapp"

        minSdk = 24
        targetSdk = 37

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Credenciales de Supabase
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")

        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            "\"$supabaseKey\""
        )
    }

    // Conserva aquí tu configuración original de buildTypes

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
dependencies {

    // ===================================
    // FIREBASE
    // ===================================

    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))

    implementation("com.google.firebase:firebase-messaging")


    // ===================================
    // ANDROIDX
    // ===================================

    // Activity: permite utilizar EdgeToEdge
    implementation("androidx.activity:activity:1.13.0")

    // Core: permite utilizar ViewCompat, Insets y WindowInsetsCompat
    implementation("androidx.core:core:1.19.1")


    // ===================================
    // OKHTTP
    // ===================================

    // Comunicación HTTP con Supabase
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    // RECYCLERVIEW
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    // ===================================
    // DEPENDENCIAS ORIGINALES
    // ===================================

    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    testImplementation(libs.junit)

    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

}