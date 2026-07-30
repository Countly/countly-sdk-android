APP_STL := c++_shared
# Pinned instead of "all": what "all" expands to has grown over the years and now adds riscv64,
# which the vendored linux_syscall_support.h has no syscalls for. These are the ABIs we ship.
APP_ABI := armeabi-v7a arm64-v8a x86 x86_64
APP_CXXFLAGS := -std=c++11 -D__STDC_LIMIT_MACROS
# android-12 predates the module's own minSdk of 21 and is below the floor NDK r28 accepts.
APP_PLATFORM := android-21

# Android 15 devices can run a 16 KB memory page size and Google Play requires apps targeting
# Android 15+ to support them.
#
# max-page-size aligns the loadable segments and is the flag that actually fixes the reported issue:
# without it p_align stays at 4 KB and the APK Analyzer reports "Does not support 16KB devices".
#
# common-page-size is what lld pads the RELRO region to, and it is a separate 4 KB default. It is not
# strictly required: the check accepts a RELRO region that either ends on a 16 KB boundary or ends
# exactly where its PT_LOAD does, and current lld gives RELRO its own PT_LOAD, so the second condition
# holds with max-page-size alone. It is kept because that second condition is a property of how the
# linker happens to group segments rather than a guarantee - the binaries this replaced failed exactly
# because their older toolchain packed writable data after RELRO in the same PT_LOAD - and because it
# puts the whole RELRO region on protectable pages. Measured to cost nothing on disk.
APP_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
