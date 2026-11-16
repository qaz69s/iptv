plugins {
    alias(libs.plugins.streambox.jvm.library)
}

// Pure Kotlin: no Android, no third-party runtime dependencies. Parsers are unit tested on the JVM.
