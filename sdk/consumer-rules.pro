# Shipped inside the AAR and applied to the integrating app's R8 run.
#
# Platform classes the SDK touches only behind Build.VERSION.SDK_INT checks, so they are never loaded
# on devices that lack them. An app compiling against an older SDK (21 and up is supported) has none
# of them in its android.jar, and R8 fails such a build with "Missing class" unless told the
# references are expected. This tells it.
-dontwarn android.app.usage.StorageStatsManager
-dontwarn android.graphics.Insets
-dontwarn android.os.storage.StorageVolume
-dontwarn android.view.DisplayCutout
-dontwarn android.view.WindowInsets$Type
-dontwarn android.view.WindowInsetsController
-dontwarn android.view.WindowMetrics
-dontwarn android.webkit.WebResourceError

# Not one of ours. When an app compiles against SDK 25 or lower, AGP puts its core-lambda-stubs.jar
# on the path so lambdas can be compiled against an android.jar that has no java.lang.invoke. That
# stub declares MethodHandles.Lookup.revealDirect() returning a class the stub jar leaves out, and
# R8 reports the dangling reference as soon as any lambda is in the build, the SDK's own included.
# R8 rewrites every lambda into a plain class, so nothing calls it at runtime; this only silences
# the bogus report for apps that have no lambdas of their own and would otherwise blame the SDK.
-dontwarn java.lang.invoke.MethodHandleInfo
