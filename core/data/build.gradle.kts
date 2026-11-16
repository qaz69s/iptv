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
    implementation(projects.core.database)
    implementation(projects.core.network)
    implementation(projects.core.parser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.paging.common)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(projects.core.testing)
}
