// =========================================================
//  SETTINGS.GRADLE.KTS - Veriflow-FaceRecognition-Flutter
// =========================================================

// Gestión de plugins (debe ir SIEMPRE antes de cualquier include)
pluginManagement {
    // Cargar flutter.sdk desde local.properties sin usar apply{}
    val props = java.util.Properties()
    val lp = file("local.properties")
    if (!lp.exists()) {
        throw GradleException("No se encontró local.properties en ${lp.absolutePath}")
    }
    lp.inputStream().use { props.load(it) }

    val flutterSdkPath = props.getProperty("flutter.sdk")
        ?: throw GradleException("flutter.sdk not set in local.properties")

    // Incluye las herramientas del SDK de Flutter
    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    // 🔧 Definir versiones globales de plugins
    plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    id("com.android.application") version "8.5.2" apply false
    id("com.android.library") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}


// Repositorios compartidos para todos los subproyectos
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Nombre raíz del proyecto
rootProject.name = "Veriflow-FaceRecognition-Flutter"

// Incluir módulo principal
include(":app")
