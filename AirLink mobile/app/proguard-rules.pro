# ProGuard and R8 rules for AirLink Mobile

# Keep class signatures and annotations
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Retain all data models and classes annotated with @Keep
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Explicitly keep all AirLink data models and crypto classes for serialization
-keep class com.airlink.mobile.data.** { *; }
-keepclassmembers class com.airlink.mobile.data.** { *; }
-keep class com.airlink.mobile.crypto.** { *; }
-keepclassmembers class com.airlink.mobile.crypto.** { *; }

# Gson rules
-keepclassmembers enum * { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
