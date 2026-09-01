#!/bin/bash
set -e
libsPattern="(arm)|(x86)"

# Windows only ships ndk-build.cmd, so accept either name.
ndkBuild=$(command -v ndk-build || command -v ndk-build.cmd)
if [ -z "$ndkBuild" ]; then
    echo "ndk-build is not on PATH. Add the NDK revision named in ndk.version to PATH first."
    exit 1
fi

# The binaries are committed, so the toolchain that made them has to be a fact of the repository, not
# of whichever machine ran this last. The unit tests read the same revision back out of the built
# libraries' .note.android.ident, so a rebuild with any other NDK fails there as well.
pinnedNdk=$(tr -d '[:space:]' < ./ndk.version)
ndkDir=$(dirname "$ndkBuild")
actualNdk=$(sed -n 's/^Pkg.Revision *= *//p' "$ndkDir/source.properties" 2>/dev/null | tr -d '[:space:]')
if [ "$actualNdk" != "$pinnedNdk" ]; then
    echo "ndk-build on PATH is NDK ${actualNdk:-of unknown revision} ($ndkDir), but ndk.version pins $pinnedNdk."
    echo "Put that NDK first on PATH, or change ndk.version deliberately and rebuild every ABI."
    exit 1
fi

# Start from nothing: the dependency files a previous build left in obj/ name sources by path, and
# after a breakpad update they can point at files that no longer exist, which make reports as
# "No rule to make target" instead of compiling the new tree.
rm -rf ./obj ./libs

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
