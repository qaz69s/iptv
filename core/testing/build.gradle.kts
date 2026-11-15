plugins {
    alias(libs.plugins.streambox.jvm.library)
}

dependencies {
    api(projects.core.domain)
    api(libs.kotlinx.coroutines.test)
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter)
}
