# «سهرة وتسالي» 1.7: R8 rules for the release build (minifyEnabled + shrinkResources in build.gradle).
#
# The app is a Capacitor shell: the web page calls native code through the JS bridge by NAME
# (window.Capacitor.Plugins.<Plugin>.<method>), Capacitor finds the plugins by class name (assets/capacitor.plugins.json,
# Class.forName) and their methods by reflection + annotations. Nothing the bridge reaches may be renamed or removed, so
# Capacitor itself and every plugin are kept whole (they are small; the big libraries - Play services ads, Firebase,
# Facebook, AndroidX - still get shrunk with their own consumer rules).
# Capacitor's own rules (node_modules/@capacitor/android/capacitor/proguard-rules.pro) arrive as consumer rules too; the
# lines below repeat and widen them on purpose.

# ---- line numbers in Play's crash stacks (the mapping file is packed into the .aab) ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# ---- Capacitor core: bridge, plugin loader, annotations (read at run time) ----
-keep class com.getcapacitor.** { *; }
-keep interface com.getcapacitor.** { *; }
-keep @interface com.getcapacitor.** { *; }
-dontwarn com.getcapacitor.**
# the optional SSL pinning add-on Capacitor looks up by name (not shipped; keep R8 quiet about it)
-dontwarn io.ionic.sslpinning.**

# ---- Capacitor plugins (official @capacitor/* = com.capacitorjs.plugins.*, community = com.getcapacitor.community.*) ----
-keep @com.getcapacitor.annotation.CapacitorPlugin public class * { *; }
-keep @com.getcapacitor.NativePlugin public class * { *; }
-keep public class * extends com.getcapacitor.Plugin { *; }
-keep class com.capacitorjs.plugins.** { *; }
-keep class com.getcapacitor.community.** { *; }
-keepclassmembers class * {
    @com.getcapacitor.PluginMethod public <methods>;
    @com.getcapacitor.annotation.PermissionCallback <methods>;
    @com.getcapacitor.annotation.ActivityCallback <methods>;
}

# ---- Cordova compatibility layer (capacitor-cordova-android-plugins; no Cordova plugins today) ----
-keep class org.apache.cordova.** { *; }
-keep public class * extends org.apache.cordova.CordovaPlugin { *; }
-dontwarn org.apache.cordova.**

# ---- the web view's JS interfaces (androidBridge, CapacitorHttp/CookiesAndroidInterface, any future one) ----
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * extends android.webkit.WebViewClient { public *; }
-keepclassmembers class * extends android.webkit.WebChromeClient { public *; }

# ---- this app ----
-keep class online.geddo.sahrah.** { *; }

# ---- Firebase Cloud Messaging (push): the plugin's MessagingService is kept through the manifest; Firebase ships its own
#      consumer rules. Kept explicitly anyway: a stripped FCM class would only show up as "no notifications". ----
-keep class com.google.firebase.messaging.** { *; }
-keep class com.google.firebase.iid.** { *; }
-dontwarn com.google.firebase.**

# ---- Google Play Install Referrer (1.7) and Play in-app review (1.5) ----
-keep class com.android.installreferrer.** { *; }
-keep class com.google.android.play.core.review.** { *; }
-dontwarn com.google.android.play.core.**

# ---- Facebook SDK core (1.6): ships consumer rules; quiet the optional parts it references ----
-dontwarn com.facebook.**

# ---- Google Mobile Ads / UMP: ship consumer rules ----
-dontwarn com.google.android.gms.**
