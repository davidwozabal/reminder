# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.wozabal.reminder.**$$serializer { *; }
-keepclassmembers class com.wozabal.reminder.** {
    *** Companion;
}
-keepclasseswithmembers class com.wozabal.reminder.** {
    kotlinx.serialization.KSerializer serializer(...);
}
