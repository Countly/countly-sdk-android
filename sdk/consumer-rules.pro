# Shipped inside the AAR and applied to the integrating app's R8 run.
#
# Platform classes the SDK touches only behind Build.VERSION.SDK_INT checks, so they are never loaded
# on devices that lack them. An app compiling against an older SDK (24 and up is supported) has none
# of them in its android.jar, and R8 fails such a build with "Missing class" unless told the
# references are expected. This tells it.
-dontwarn android.app.usage.StorageStatsManager
-dontwarn android.graphics.Insets
-dontwarn android.view.DisplayCutout
-dontwarn android.view.WindowInsets$Type
-dontwarn android.view.WindowInsetsController
-dontwarn android.view.WindowMetrics

# Not one of ours: at compileSdk 25 or lower, AGP's core-lambda-stubs.jar declares this class but omits
# it, and R8 reports the dangling reference whenever any lambda (the SDK has some) is in the build. Lambdas
# are rewritten to plain classes, so nothing touches it at runtime; this only silences the bogus report.
-dontwarn java.lang.invoke.MethodHandleInfo
