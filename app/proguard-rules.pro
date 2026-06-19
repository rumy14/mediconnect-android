# Add project specific ProGuard rules here.

# ── Kotlin Serialization ──
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep kotlinx.serialization internals
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all model classes and their generated serializers
-keep,includedescriptorclasses class com.mediconnect.data.model.** { *; }
-keepclassmembers class com.mediconnect.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.mediconnect.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Ktor ──
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ── Keep API client for logging ──
-keep class com.mediconnect.data.api.** { *; }
