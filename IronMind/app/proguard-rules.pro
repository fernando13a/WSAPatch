# Default optimized ProGuard rules are pulled in from proguard-android-optimize.txt.

# Room generates code that reflects over entities; keep annotations intact.
-keep class androidx.room.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Keep Hilt-generated components.
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }

# On-device LLM native bindings (used from Stage 3).
-keep class com.google.mediapipe.** { *; }
