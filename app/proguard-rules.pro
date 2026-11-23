# Media3 FFmpeg extension uses JNI and is loaded reflectively by DefaultRenderersFactory.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class org.jellyfin.media3.** { *; }

# kotlinx.serialization: the compiler plugin ships its own rules; keep companion serializers of our DTOs.
-keepclassmembers class com.streamdeck.player.** {
    *** Companion;
}
-keepclasseswithmembers class com.streamdeck.player.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit service interfaces are created via reflection.
-keep,allowobfuscation interface com.streamdeck.player.core.network.XtreamService

# Cast options provider is referenced only from the manifest (also in feature/cast consumer rules).
-keep class com.streamdeck.player.feature.cast.CastOptionsProvider { *; }

# Strip verbose logging in release.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
