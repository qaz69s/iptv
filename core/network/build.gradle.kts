plugins {
    alias(libs.plugins.streambox.jvm.library)
    alias(libs.plugins.streambox.hilt)
}

// Plain JVM module: OkHttp has no Android dependency, so the HTTP layer is tested with MockWebServer on the JVM.
dependencies {
    api(projects.core.common)
    api(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
}
