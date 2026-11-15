package com.streambox.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

private val JAVA_VERSION = JavaVersion.VERSION_17

internal fun Project.configureAndroidApplication(extension: ApplicationExtension) {
    extension.apply {
        compileSdk = libs.intVersion("compileSdk")
        defaultConfig {
            minSdk = libs.intVersion("minSdk")
            targetSdk = libs.intVersion("targetSdk")
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            vectorDrawables.useSupportLibrary = false
        }
        compileOptions {
            sourceCompatibility = JAVA_VERSION
            targetCompatibility = JAVA_VERSION
        }
        testOptions.unitTests.isReturnDefaultValues = true
        packaging.resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*.md")
    }
    configureKotlinCompiler()
}

internal fun Project.configureAndroidLibrary(extension: LibraryExtension) {
    extension.apply {
        compileSdk = libs.intVersion("compileSdk")
        defaultConfig {
            minSdk = libs.intVersion("minSdk")
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            consumerProguardFiles("consumer-rules.pro")
        }
        compileOptions {
            sourceCompatibility = JAVA_VERSION
            targetCompatibility = JAVA_VERSION
        }
        testOptions.unitTests.isReturnDefaultValues = true
    }
    configureKotlinCompiler()
}

internal fun Project.configureKotlinCompiler() {
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            if (this is org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions) {
                jvmTarget.set(JvmTarget.JVM_17)
            }
            allWarningsAsErrors.set(false)
            freeCompilerArgs.addAll(
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
        }
    }
}
