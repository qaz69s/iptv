import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.streamdeck.player"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.streamdeck.player"
        minSdk = 23
        targetSdk = 35
        versionCode = 12
        versionName = "0.10.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            val props = Properties()
            val f = rootProject.file("keystore.properties")
            if (f.exists()) {
                f.inputStream().use(props::load)
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without a release keystore, sign with the debug key so the optimised (R8) build can be installed for testing.
            signingConfig = signingConfigs.getByName(if (rootProject.file("keystore.properties").exists()) "release" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests.all { it.useJUnitPlatform() } }
    androidResources { generateLocaleConfig = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:player"))
    implementation(project(":feature:playlist"))
    implementation(project(":feature:channels"))
    implementation(project(":feature:player-ui"))
    implementation(project(":feature:epg"))
    implementation(project(":feature:vod"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:cast"))
    implementation(project(":feature:monetization"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    implementation(libs.androidx.fragment)
    implementation(libs.coil.compose)
    debugImplementation(libs.leakcanary)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.launcher)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext)
}
