plugins {
    alias(libs.plugins.streambox.jvm.library)
}

dependencies {
    api(projects.core.model)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.core)
}
