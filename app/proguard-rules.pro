# Room generates code reflectively referenced by the runtime.
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# kotlinx.serialization keeps its generated serializers on the companion.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.winterarc.app.data.sync.**$$serializer { *; }
-keepclassmembers class com.winterarc.app.data.sync.** { *** Companion; }
-keepclasseswithmembers class com.winterarc.app.data.sync.** { kotlinx.serialization.KSerializer serializer(...); }

# The domain module is pure data and logic; keep its model classes intact.
-keep class com.winterarc.domain.model.** { *; }
