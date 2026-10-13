## 0.0.1
* !! Major breaking change !! New plugin versions are published at `https://maven.countly.com` instead of Maven Central. Add the repository to `pluginManagement { repositories { } }` in `settings.gradle`, or to `buildscript { repositories { } }` when the plugin is on the buildscript classpath. Older versions stay on Maven Central.
* ! Minor breaking change ! The plugin now has its own version number and is released only when it changes. Use its latest version instead of the SDK's version number.
* ! Minor breaking change ! "uploadJavaSymbols" now fails the build when the mapping file is missing or the upload is rejected, as the native symbol upload already does, instead of only logging it.

* The plugin is compiled to Java 8 bytecode, so it loads on every JDK that a supported Gradle version runs on.
* Updated OkHttp to 4.12.0.

## 26.1.6
* ! Minor breaking change ! The native symbol upload now works per variant: `uploadNativeSymbols<Variant>` (for example `uploadNativeSymbolsRelease`) dumps the native libraries AGP merges for that variant, your own unstripped ones plus those from AARs, and builds them first so the symbols always belong to the current build. `uploadNativeSymbols` now covers every release variant. The previous default of reading `intermediates/cmake/release/obj` is gone; set `nativeObjectFilesDir` to keep dumping a directory of your choice, where `BUILD_TYPE` stands for the variant's build type.
* The plugin now fails the build when `dump_syms` fails or the server rejects the native symbol upload, instead of only logging it, and logs every dumped library's debug id so a crash that stays unsymbolicated can be checked against the shipped APK.
* The plugin is now compiled for Java 17, so Gradle running on JDK 17 or 18 can resolve and load it. Earlier versions declared Java 19, whichever JDK had built the release.
* The values in the `countly` block are now defaults, so a build that gives one variant its own `server` or `app_key` by configuring that variant's upload task keeps it. Previously such an override was silently replaced by the block's value unless it was written inside `afterEvaluate`.
