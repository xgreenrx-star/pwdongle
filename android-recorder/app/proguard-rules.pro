# Proguard rules for PWDongle Recorder

# Keep AndroidX
-keep class androidx.** { *; }
-keepnames class androidx.** { *; }

# Keep Kotlin
-keep class kotlin.** { *; }
-keepnames class kotlin.** { *; }

# Keep Material Design
-keep class com.google.android.material.** { *; }
-keepnames class com.google.android.material.** { *; }

# Keep app classes
-keep class com.pwdongle.recorder.** { *; }
-keepnames class com.pwdongle.recorder.** { *; }

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve signatures of classes
-keepattributes Signature
