plugins {
    alias(libs.plugins.streambox.android.library)
    alias(libs.plugins.streambox.hilt)
}

android {
    namespace = "com.streambox.player.core.datastore"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
}
