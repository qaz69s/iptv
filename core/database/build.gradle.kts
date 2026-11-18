plugins {
    alias(libs.plugins.streambox.android.library)
    alias(libs.plugins.streambox.android.robolectric)
    alias(libs.plugins.streambox.hilt)
}

android {
    namespace = "com.streambox.player.core.database"
    // Exported schemas are also packaged into androidTest assets for MigrationTestHelper.
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
}

ksp {
    // Room schema JSON is exported to core/database/schemas and committed (migration history).
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
    arg("room.incremental", "true")
}

dependencies {
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    api(libs.androidx.room.paging)
    api(libs.androidx.paging.common)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.core)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
