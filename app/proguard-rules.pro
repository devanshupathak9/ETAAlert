# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Gson model classes
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# WorkManager
-keep class androidx.work.** { *; }
