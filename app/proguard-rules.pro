# Gson serialization / deserialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.mucephei.app.data.model.** { *; }
-keep class com.mucephei.app.ai.** { *; }

# Room Database
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.mucephei.app.data.db.** { *; }

# MediaPipe & GenAI Tasks
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keepclassmembers class com.google.mediapipe.** {
    native <methods>;
    *;
}

# AutoValue
-dontwarn com.google.auto.value.**

# Llama.cpp / Native JNI
-keep class org.nehuatl.llamacpp.** { *; }
-dontwarn org.nehuatl.llamacpp.**
-keep class io.github.ljcamargo.** { *; }
-dontwarn io.github.ljcamargo.**
-keepclassmembers class * {
    native <methods>;
}

# Coroutines & Serialization
-keepattributes InnerClasses,Signature,EnclosingMethod
-dontwarn kotlinx.coroutines.**
-dontwarn sun.misc.Unsafe
