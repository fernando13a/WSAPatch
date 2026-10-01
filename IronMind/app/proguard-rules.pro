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

# MediaPipe tasks-genai references optional dependencies that are NOT on the
# runtime classpath, so R8 (full mode) fails the release build on "Missing class":
#   - com.google.auto.value.*      → AutoValue is a compile-time-only annotation.
#   - mediapipe.framework.image.*  → vision image extractors, unused by the
#                                    text-only LLM inference path.
#   - com.google.protobuf.*        → protobuf-lite annotation classes.
# None are needed at runtime, so silence the warnings instead of bundling them.
-dontwarn com.google.auto.value.**
-dontwarn com.google.mediapipe.framework.image.**
-dontwarn com.google.protobuf.**
