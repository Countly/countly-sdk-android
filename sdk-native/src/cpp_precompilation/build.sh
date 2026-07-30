#!/bin/bash
set -e
libsPattern="(arm)|(x86)"

# Windows only ships ndk-build.cmd, so accept either name.
ndkBuild=$(command -v ndk-build || command -v ndk-build.cmd)
if [ -z "$ndkBuild" ]; then
    echo "ndk-build is not on PATH. Add your NDK r28 or newer directory to PATH first."
    exit 1
fi

"$ndkBuild" NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=./Android.mk NDK_APPLICATION_MK=./Application.mk NDK_LIBS_OUT=./libs NDK_OUT=./obj

# ndk-build strips the modules it builds, but copies libc++_shared.so straight out of the NDK
# sysroot where it still carries around 9 MB of debug info. Strip it so the AAR keeps the size it
# has always had.
llvmStrip=$(ls "$(dirname "$ndkBuild")"/toolchains/llvm/prebuilt/*/bin/llvm-strip* 2>/dev/null | head -1)
if [ -z "$llvmStrip" ]; then
    echo "Could not find llvm-strip next to ndk-build. Stopping rather than shipping debug symbols."
    exit 1
fi
find ./libs -name 'libc++_shared.so' -exec "$llvmStrip" --strip-all {} \;

for D in ./libs/*; do
   if [ -d "${D}" ] && [[ "${D}" =~ $libsPattern ]]; then
       cp -r $D "../../libs"
       echo "${D} copied"   # your processing here
   fi
done
