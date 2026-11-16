plugins {
    alias(libs.plugins.streambox.android.feature)
}

android {
    namespace = "com.streambox.player.feature.playlist"
}

dependencies {
    implementation(libs.androidx.activity.compose)
}
