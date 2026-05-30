# Proguard rules for shrinking the APK size.
# Keep AndroidX and Material classes
-dontwarn androidx.**
-dontwarn com.google.material.**

# Keep our custom background service and receiver entry points
-keep class com.antigravity.blututprinter.PrintServerService { *; }
-keep class com.antigravity.blututprinter.BootReceiver { *; }
-keep class com.antigravity.blututprinter.RawBTIntentActivity { *; }
-keep class com.antigravity.blututprinter.MainActivity { *; }
