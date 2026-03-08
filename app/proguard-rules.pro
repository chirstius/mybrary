# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclassmembernames,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Gson
-keep class com.mybrary.app.data.remote.model.** { *; }
-keep class com.google.gson.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Google Auth
-keep class com.google.android.gms.** { *; }
