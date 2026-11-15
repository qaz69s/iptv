# Navigation type-safe routes are @Serializable objects; kotlinx.serialization ships its own
# consumer rules, so no extra keep rules are needed at this stage.
# Strip verbose logging in release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
