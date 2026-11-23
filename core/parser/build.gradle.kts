plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    compileOnly(libs.kxml2)
    testImplementation(libs.kxml2)
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.launcher)
}

tasks.test { useJUnitPlatform() }
