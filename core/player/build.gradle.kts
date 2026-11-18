plugins {
    alias(libs.plugins.streambox.android.library)
    alias(libs.plugins.streambox.hilt)
}

android {
    namespace = "com.streambox.player.core.player"
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.media3.exoplayer)
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.session)
    implementation(libs.nextlib.media3ext)
    implementation(libs.kotlinx.coroutines.core)
}
