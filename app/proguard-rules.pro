# kotlinx-serialization 官方规则
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.cijian.app.**$$serializer { *; }
-keepclassmembers class com.cijian.app.** { *** Companion; }
-keepclasseswithmembers class com.cijian.app.** { kotlinx.serialization.KSerializer serializer(...); }
