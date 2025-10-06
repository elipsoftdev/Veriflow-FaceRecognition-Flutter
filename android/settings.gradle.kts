pluginManagement {
    // Carga las propiedades de Flutter
    val props = java.util.Properties().apply {
        file("local.properties").inputStream().use { load(it) }
    }
    val flutterSdkPath = props.getProperty("flutter.sdk")
        ?: error("flutter.sdk not set in local.properties")

    // Incluye el build de Flutter
    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// 🔧 Plugins
plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}

// ✅ Incluye solo el módulo principal
include(":app")



// 🚀 BuildDir personalizado (seguro y sin recursión)
gradle.beforeProject {
    if (project.name != rootProject.name) {
        project.layout.buildDirectory.set(file("${rootDir}/build/${project.name}"))
    }
}

rootProject.name = "Veriflow-FaceRecognition-Flutter"
