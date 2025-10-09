import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Versiones estables y compatibles con Flutter/Gradle 8.13
    id("com.android.application") version "8.7.2" apply false
    id("com.android.library") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

// Repos para todos los subproyectos
allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// Fuerza compatibilidad Java/Kotlin 17 en todos los módulos
subprojects {
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_17.toString()
        targetCompatibility = JavaVersion.VERSION_17.toString()
        options.encoding = "UTF-8"
    }
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = freeCompilerArgs + listOf("-Xjvm-default=all")
        }
    }
}

// Tarea clean
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
