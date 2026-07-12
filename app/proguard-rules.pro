# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class app.stepbuddy.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Firestore uses reflection to (de)serialize model classes annotated data.
-keepclassmembers class app.stepbuddy.data.remote.** {
    <init>();
    <fields>;
}
