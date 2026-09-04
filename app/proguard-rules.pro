# kotlinx.serialization generates a companion serializer for every @Serializable class and looks
# it up reflectively. Without these the release build loads an empty database rather than crashing
# outright, which is the worst possible failure mode -- silent data loss.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.winterarc.core.**$$serializer { *; }
-keepclassmembers class com.winterarc.core.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.winterarc.core.** { *; }
