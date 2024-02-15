# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /projects/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Rules to keep the FCM dependency optional
-dontwarn com.google.firebase.messaging.RemoteMessage
-keep class com.google.firebase.messaging.RemoteMessage

# Rules recommended for Huawei PushKit
-ignorewarnings
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keep class com.hianalytics.android.**{*;}
-keep class com.huawei.updatesdk.**{*;}
-keep class com.huawei.hms.**{*;}

-assumenosideeffects class android.util.Log {
  public static boolean isLoggable(java.lang.String, int);
  public static int v(...);
  public static int i(...);
  public static int w(...);
  public static int d(...);
  public static int e(...);
  public static java.lang.String getStackTraceString(java.lang.Throwable);
}

-assumenosideeffects class java.lang.Exception {
  public void printStackTrace();
}

-assumenosideeffects class ly.count.android.sdk.ModuleLog {
    public void v(...);
    public void i(...);
    public void w(...);
    public void d(...);
    public void e(...);
}

-keep class ly.count.android.demo.** { *; }

-dontwarn com.squareup.okhttp.**

-keep class com.squareup.okhttp.** { *; }
-keep interface com.squareup.okhttp.** { *; }

-keep class * extends java.util.ListResourceBundle {
    protected Object[][] getContents();
}

-keep public class com.google.android.gms.common.internal.safeparcel.SafeParcelable {
    public static final *** NULL;
}

-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembernames class * {
    @com.google.android.gms.common.annotation.KeepName *;
}

-keepnames class * implements android.os.Parcelable {
    public static final ** CREATOR;
}