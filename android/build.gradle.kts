// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    // ⚙️ Android Gradle Plugin y Kotlin configurados a nivel superior
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}

// 🧩 Configuración global
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        // No se requiere classpath adicional: Flutter gestiona su propio plugin de compilación
        // Si necesitas herramientas externas (Crashlytics, Google Services, etc.) agrégalas aquí.
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// ✅ Asegurar compatibilidad con Java 17
tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
}

// ✅ Limpieza
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
