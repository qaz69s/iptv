plugins {
    alias(libs.plugins.streambox.jvm.library)
    alias(libs.plugins.streambox.hilt)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
