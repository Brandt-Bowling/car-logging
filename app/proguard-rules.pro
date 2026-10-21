# Proguard rules for Car Logger

# Room
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}

# Google API Client & Drive
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-dontwarn com.google.api.client.**

# Google Gen AI
-keep class com.google.ai.client.generativeai.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Coil
-dontwarn coil3.**
