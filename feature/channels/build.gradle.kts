plugins {
    alias(libs.plugins.streambox.android.feature)
}

android {
    namespace = "com.streambox.player.feature.channels"
}

dependencies {
    implementation(libs.androidx.paging.compose)
    implementation(libs.coil.compose)
}
