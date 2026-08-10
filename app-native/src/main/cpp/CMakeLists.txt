# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
        native-lib

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        native-lib.cpp)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        native-lib

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})

# Android 15 devices can run a 16 KB memory page size and Google Play requires apps targeting
# Android 15+ to support them. NDK r28 and newer lay the segments out that way by default, but this
# sample is pinned to r27 (see ndkVersion in build.gradle), so ask for it explicitly. Without this
# the sample's own library trips the "Does not support 16KB devices" warning in the APK Analyzer,
# which looks like the Countly SDK is at fault when it is not.
#
# max-page-size is the flag that aligns the loadable segments and is the one strictly required here.
# common-page-size additionally pads the RELRO region up to 16 KB. That is belt and braces rather
# than a requirement: the tooling accepts a RELRO region that either ends on a 16 KB boundary or ends
# exactly where its PT_LOAD does, and current lld gives RELRO its own PT_LOAD so the second condition
# already holds. It is kept because that second condition is a property of how the linker happens to
# group segments, while common-page-size satisfies the first one outright, and because it means the
# whole RELRO region lands on protectable pages. Costs nothing on disk. Matches the flags in
# sdk-native/src/cpp_precompilation/Application.mk.
target_link_options(native-lib PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384")