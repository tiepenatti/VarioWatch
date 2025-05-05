# Keep your application class
-keep class au.com.penattilabs.variowatch.** { *; }

# Keep app configuration and important classes
-keep class au.com.penattilabs.variowatch.VarioWatchApplication
-keep class au.com.penattilabs.variowatch.Constants { *; }

# Keep sensor-related classes
-keep class au.com.penattilabs.variowatch.PressureSensorManager { *; }
-keep class au.com.penattilabs.variowatch.PressureSensorManager$* { *; }

# Keep UserPreferences as it implements Parcelable
-keep class au.com.penattilabs.variowatch.UserPreferences { *; }
-keepclassmembers class au.com.penattilabs.variowatch.UserPreferences {
    public static final android.os.Parcelable$Creator *;
}

# Keep AltitudeCalculator as it contains important calculations
-keep class au.com.penattilabs.variowatch.AltitudeCalculator { *; }

# Keep service classes
-keep class au.com.penattilabs.variowatch.VarioService { *; }

# Keep Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# Wear OS specific rules
-keep class androidx.wear.** { *; }
-keep class * extends androidx.wear.watchface.complications.data.ComplicationData
-keep class * extends androidx.wear.watchface.style.UserStyleSetting
-keepclassmembers class * extends androidx.wear.tiles.TileService {
    public static final ** INSTANCE;
}

# Keep Kotlin Metadata and Coroutines
-keepattributes RuntimeVisibleAnnotations
-keepattributes AnnotationDefault
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable

# Keep Compose classes
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }
-keep class androidx.wear.compose.** { *; }
-keepclassmembers class androidx.wear.compose.** { *; }

# Keep Coroutines for background processing
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep sensor-related classes
-keep class android.hardware.** { *; }
-keep class android.hardware.Sensor { *; }
-keep class android.hardware.SensorEvent { *; }
-keep class android.hardware.SensorEventListener { *; }
-keep class android.hardware.SensorManager { *; }

# Remove debugging logs in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep any native methods
-keepclasseswithmembernames class * {
    native <methods>;
}