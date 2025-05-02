# Keep your application class
-keep class au.com.penattilabs.variowatch.** { *; }

# Keep Kotlin Metadata
-keepattributes *Annotation*, Signature, Exception

# General Android rules
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile