# kotlinx.serialization: keep generated serializers of the API models
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.gotravelnha.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.gotravelnha.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.slf4j.**
