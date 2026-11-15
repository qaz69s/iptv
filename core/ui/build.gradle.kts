plugins {
    alias(libs.plugins.streambox.android.library)
    alias(libs.plugins.streambox.android.compose)
}

android {
    namespace = "com.streambox.player.core.ui"
}

dependencies {
    api(projects.core.common)
    implementation(projects.core.designsystem)
}
