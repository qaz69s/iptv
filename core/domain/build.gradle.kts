plugins {
    alias(libs.plugins.streambox.jvm.library)
}

dependencies {
    api(projects.core.model)
    // PagingData is part of the repository contract; paging-common is a pure Kotlin artifact.
    api(libs.androidx.paging.common)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.core)
}
