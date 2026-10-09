# R8 rules for the release build.

# Keep line numbers so crash stack traces stay readable; hide the source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Gson maps JSON to these classes by field name through reflection, so their names
# and fields must survive shrinking and obfuscation. Every API request/response model
# lives in a `dto` package.
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses
-keep class com.thedavelopers.eventqr.**.dto.** { *; }
-keep class com.thedavelopers.eventqr.features.organizer.rewards.RewardSettingsRequest { *; }

# Strip verbose and debug logging from release builds; info/warn/error stay.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# uCrop (image cropper) inflates its views by reflection.
-dontwarn com.yalantis.ucrop.**
-keep class com.yalantis.ucrop.** { *; }
-keep interface com.yalantis.ucrop.** { *; }
