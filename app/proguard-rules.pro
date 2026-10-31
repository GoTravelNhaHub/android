# kotlinx.serialization: keep generated serializers of the API models
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.gotravelnha.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.gotravelnha.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.slf4j.**

# No logging in release builds (errors stay, for crash reports)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
