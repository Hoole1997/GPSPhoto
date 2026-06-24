# App-level R8/ProGuard rules.
#
# The app module currently has a few nested Kotlin data classes:
# - StreetViewViewModel.ListState.Success/Error
# - SearchViewModel.SearchState.History/Results/Error
# - StreetViewRepository.MetadataOutcome.Found
#
# They are UI/domain state classes and are not parsed through Gson/Moshi or
# other reflection-based serializers. R8 can safely shrink/optimize their
# members. We only keep their generated nested class names and Kotlin metadata
# so sealed/data-class diagnostics and any SDK reflection around nested class
# names do not break after minification.

# Keep useful metadata for Kotlin sealed/data classes, generic signatures,
# annotations, and nested classes.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Keep source line numbers for Crashlytics / release stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Manifest/app entry points. Android Gradle Plugin handles many component
# references, but these classes are also exposed to the launcher SDK via
# LcbApp.metaautovault()/convertsafepower().
-keep class com.example.lcb.app.LcbApp { *; }
-keep class com.example.lcb.app.MainActivity { *; }
-keep class com.example.lcb.app.StreetViewActivity { *; }
-keep class com.example.lcb.app.SearchActivity { *; }
-keep class com.example.lcb.app.SettingsActivity { *; }
-keep class com.example.lcb.app.AboutActivity { *; }

# Fragments/bottom sheets are recreated by AndroidX through no-arg constructors.
-keep public class com.example.lcb.app.streetview.StreetViewListFragment {
    public <init>();
    public static *** newInstance(...);
}
-keep public class com.example.lcb.app.settings.LanguageBottomSheet {
    public <init>();
}

# Nested sealed/data state classes found in the app module. Preserve names only;
# allow R8 to shrink unused classes and optimize members.
-keepnames,allowshrinking class com.example.lcb.app.streetview.StreetViewViewModel$ListState$*
-keepnames,allowshrinking class com.example.lcb.app.streetview.SearchViewModel$SearchState$*
-keepnames,allowshrinking class com.example.lcb.app.streetview.StreetViewRepository$MetadataOutcome$*

# Gson is present as a dependency. Keep only annotation-driven model members, so
# future @SerializedName/@Expose models remain safe without disabling obfuscation
# for every data class in the app.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-dontwarn com.google.gson.**

# WebView bridge methods must remain visible when annotated.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Parcelable / enum helpers used reflectively by Android framework code.
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Native methods are resolved by name.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Google Maps marker clustering uses ClusterItem/renderer implementations across
# library boundaries. Keep app implementations stable while allowing members to
# be optimized normally where possible.
-keep class com.example.lcb.app.streetview.StreetViewClusterItem { *; }
-keep class com.example.lcb.app.streetview.StreetViewClusterRenderer { *; }

# Custom ad renderers are handed to the billing/ad SDK as callbacks.
-keep class com.example.lcb.app.ad.renderer.** { *; }
