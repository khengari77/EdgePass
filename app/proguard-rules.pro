# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep ONNX Runtime classes
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Keep JNI native methods
-keep class com.edgepass.lib.PassportProcessor {
    native <methods>;
}

# Keep Compose classes
-keep class androidx.compose.** { *; }

# Keep Kotlin metadata
-keepattributes RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# ML Kit face detection
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
