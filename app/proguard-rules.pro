# PocketScan ProGuard / R8 rules.

# Keep OpenCV JNI bindings — reflection + native method lookups.
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# ML Kit text recognition loads models via reflection.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# TensorFlow Lite interpreter + native delegates.
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Room generated implementations.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }

# Kotlin metadata / coroutines.
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**
