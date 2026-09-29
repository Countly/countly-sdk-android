## XX.XX.XX
* !! Major breaking change !! New versions of `sdk-native` are published at `https://maven.countly.com` instead of Maven Central. Add the repository as described in the SDK's CHANGELOG; versions released before this one stay available on Maven Central.
* ! Minor breaking change ! `sdk-native` now has its own version number and is released only when it changes. Use its latest version instead of the SDK's version number.

## 26.1.6
* Updated the bundled Breakpad crash reporter to latest.

* Mitigated an issue where native crashes could not be symbolicated on devices with a 4 KB memory page size when the crashing library was built for 16 KB page sizes, as the library was reported without its build identifier.
* Fixed a 26.1.5 regression where the module could only be used from projects compiling against SDK 37 with Kotlin 2.4 or newer. The AAR now declares compileSdk 21 as its minimum and no longer depends on the Kotlin standard library, matching the releases before 26.1.5.
* Fixed a regression where the library was compiled to Java 11 bytecode instead of Java 8, which older Android Gradle Plugin versions cannot read.
