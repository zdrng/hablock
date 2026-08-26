-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# DataStore preferences are protobuf-lite messages read via field reflection;
# R8 full mode strips/renames their fields ("field value_ not found" crash) without this.
# DataStore ships protobuf shaded under androidx.datastore.preferences.protobuf.
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

-keepclassmembers @kotlinx.serialization.Serializable class dev.hablock.app.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class dev.hablock.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
