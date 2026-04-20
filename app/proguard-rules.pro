# Media3 uses reflection in a few spots; keep everything to be safe.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Kotlin metadata used by reflection / Compose runtime
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# kotlinx.coroutines internal service loader
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory {
    <init>();
}
-keepnames class kotlinx.coroutines.** { *; }

# Compose: keep @Composable function parameters intact
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Reorderable library
-keep class sh.calvin.reorderable.** { *; }
