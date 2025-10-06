import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    // Versions estables y compatibles con Flutter/Gradle 8.x
    id("com.android.application") version "8.7.2" apply false
    id("com.android.library")    version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
}

// Repos para todos los subproyectos
allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// Fuerza compatibilidad Java 17 en todos los módulos
subprojects {
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_17.toString()
        targetCompatibility = JavaVersion.VERSION_17.toString()
        options.encoding = "UTF-8"
    }
}

// Tarea clean
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
