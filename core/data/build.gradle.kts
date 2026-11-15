plugins {
    alias(libs.plugins.streambox.android.library)
    alias(libs.plugins.streambox.hilt)
}

android {
    namespace = "com.streambox.player.core.data"
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.datastore)
    implementation(libs.kotlinx.coroutines.core)
}
