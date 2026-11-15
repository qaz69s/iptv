plugins {
    alias(libs.plugins.streambox.android.library)
    alias(libs.plugins.streambox.android.compose)
}

android {
    namespace = "com.streambox.player.core.designsystem"
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.compose.material3)
    api(libs.androidx.tv.material)
}
