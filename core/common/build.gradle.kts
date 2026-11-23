plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.launcher)
}

tasks.test { useJUnitPlatform() }
