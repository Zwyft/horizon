# Horizon — ProGuard rules
# Keep Room DAOs and entities
-keep class com.zwyft.horizon.data.entity.** { *; }
-keep class com.zwyft.horizon.data.dao.** { *; }
-keep class com.zwyft.horizon.data.HorizonDatabase { *; }

# Keep @Keep-annotated classes
-keep @androidx.annotation.Keep class * { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @dagger.hilt.Inject <init>(...);
}

# Compose
-keepclassmembers class androidx.compose.** { *; }

# Gson (for JSON import)
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# WorkManager
-keep class androidx.work.** { *; }

# Google Drive API
-keep class com.google.api.services.drive.** { *; }
