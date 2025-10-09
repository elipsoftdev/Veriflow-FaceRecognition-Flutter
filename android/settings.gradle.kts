import java.util.Properties

// =========================================================
//  SETTINGS.GRADLE.KTS - Veriflow-FaceRecognition-Flutter
// =========================================================

fun loadFlutterSdkPath(): String {
    val props = Properties()
    val localProperties = file("local.properties")
    if (!localProperties.exists()) {
        throw GradleException("No se encontró local.properties en ${localProperties.absolutePath}")
    }
    localProperties.inputStream().use { props.load(it) }
    return props.getProperty("flutter.sdk")
        ?: throw GradleException("flutter.sdk not set in local.properties")
}

val flutterSdkPath = loadFlutterSdkPath()

pluginManagement {
    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    // 🔧 Definir versiones globales de plugins
    plugins {
        id("dev.flutter.flutter-plugin-loader") version "1.0.0"
        id("dev.flutter.flutter-gradle-plugin") version "1.0.0" apply false
        id("com.android.application") version "8.7.2" apply false
        id("com.android.library") version "8.7.2" apply false
        id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    }
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
