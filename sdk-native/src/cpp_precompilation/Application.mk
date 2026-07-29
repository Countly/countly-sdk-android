APP_STL := c++_shared
# Pinned instead of "all": what "all" expands to has grown over the years and now adds riscv64,
# which the vendored linux_syscall_support.h has no syscalls for. These are the ABIs we ship.
APP_ABI := armeabi-v7a arm64-v8a x86 x86_64
APP_CXXFLAGS := -std=c++11 -D__STDC_LIMIT_MACROS
# android-12 predates the module's own minSdk of 21 and is below the floor NDK r28 accepts.
APP_PLATFORM := android-21

# Android 15 devices can run a 16 KB memory page size and Google Play requires apps targeting
# Android 15+ to support them. max-page-size aligns the loadable segments. common-page-size is what
# lld pads the RELRO region to, and it is a separate 4 KB default: without it RELRO still ends on a
# 4 KB boundary and the APK Analyzer reports "RELRO is not a suffix and its end is not 16KB aligned"
# even though the segments themselves are aligned.
APP_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
