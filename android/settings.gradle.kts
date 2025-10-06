

pluginManagement {
    // Lee flutter.sdk desde local.properties sin usar apply{}
    val props = java.util.Properties()
    val lp = file("local.properties")
    if (!lp.exists()) {
    throw GradleException("No se encontró local.properties en ${lp.absolutePath}")
    }
    lp.inputStream().use { props.load(it) }

    val flutterSdkPath = props.getProperty("flutter.sdk")
        ?: throw GradleException("flutter.sdk not set in local.properties")

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    id("com.android.application") version "8.7.2" apply false
    id("com.android.library")    version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
}

rootProject.name = "Veriflow-FaceRecognition-Flutter"
include(":app")
