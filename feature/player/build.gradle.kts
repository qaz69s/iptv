plugins {
    alias(libs.plugins.streambox.android.feature)
}

android {
    namespace = "com.streambox.player.feature.player"
}

dependencies {
    implementation(projects.core.player)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
