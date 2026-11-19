package ly.count.android.sdknative;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The .so files this module publishes are compiled by hand from src/cpp_precompilation with the NDK
 * pinned in src/cpp_precompilation/ndk.version, and committed to the repository. Nothing in the
 * Gradle build recompiles or verifies them, so a rebuild that quietly changes what gets published
 * would otherwise only surface once integrators hit it.
 *
 * These tests cover the ways a rebuild has gone wrong or could go wrong: an ABI dropped or left
 * behind, a missing STL, a library that no longer exports the JNI entry points, debug symbols shipped
 * by accident, or a platform floor above the module's own minSdk. The 16 KB page size layout is
 * covered separately by {@link NativeLibraryAlignmentTests}.
 *
 * Everything except the ABI folder listing looks only at NativeLibraries.ABIS. The ABIs in
 * NativeLibraries.FROZEN_ABIS cannot be rebuilt by the current NDK, so holding them to the shape of a
 * current build would mean a permanently failing test rather than a useful signal.
 */
public class NativeLibraryPackagingTests {
    /**
     * Must stay in step with minSdk in build.gradle. The NDK records the platform it compiled against
     * in .note.android.ident, and a library built above the module's floor would fail to resolve
     * symbols at load time on the oldest devices the SDK claims to support.
     */
    private static final int MODULE_MIN_SDK = 21;

    /** The methods CountlyNative declares as native, in the mangled form the linker exports. */
    private static final List<String> JNI_ENTRY_POINTS = Arrays.asList(
        "Java_ly_count_android_sdknative_CountlyNative_init",
        "Java_ly_count_android_sdknative_CountlyNative_testCrash",
        "Java_ly_count_android_sdknative_CountlyNative_getBreakpadVersion",
        "Java_ly_count_android_sdknative_CountlyNative_getBreakpadChecksum");

    /**
     * The ABI folders are exactly the ones Application.mk builds plus the frozen ones we still ship,
     * in both the committed build output and the folder AGP packages from. Each actively built ABI
     * holds the SDK library and the STL it links against; each frozen one holds the SDK library alone,
     * because it predates libc++_shared.so and links the platform's libstdc++.so instead.
     *
     * Pinning the whole set both ways matters because the copy task only ever adds files. A folder left
     * behind by an ABI that is no longer built would keep shipping inside the AAR with nobody noticing,
     * and equally, quietly dropping one of the frozen ABIs would be a breaking change for anyone still
     * resolving it.
     */
    @Test
    public void everyPrecompiledAbiShipsTheSdkLibraryAndItsStl() {
        List<String> withStl = new ArrayList<>(Arrays.asList(NativeLibraries.STL_LIBRARY, NativeLibraries.SDK_LIBRARY));
        Collections.sort(withStl);
        List<String> sdkOnly = Collections.singletonList(NativeLibraries.SDK_LIBRARY);

        List<String> expectedAbis = new ArrayList<>(NativeLibraries.ABIS);
        expectedAbis.addAll(NativeLibraries.FROZEN_ABIS);
        Collections.sort(expectedAbis);

        for (File root : Arrays.asList(NativeLibraries.BUILD_OUTPUT_DIR, NativeLibraries.PACKAGED_DIR)) {
            assertTrue("Missing " + root.getAbsolutePath(), root.isDirectory());
            assertEquals("Unexpected set of ABI folders in " + root, expectedAbis, NativeLibraries.abiFolderNames(root));

            for (String abi : NativeLibraries.ABIS) {
                assertEquals("Unexpected set of libraries in " + root + "/" + abi,
                    withStl, NativeLibraries.sharedObjectNames(root, abi));
            }
            for (String abi : NativeLibraries.FROZEN_ABIS) {
                assertEquals("Unexpected set of libraries in " + root + "/" + abi,
                    sdkOnly, NativeLibraries.sharedObjectNames(root, abi));
            }
        }
    }

    /**
     * Without these four symbols the library loads but every call from CountlyNative throws
     * UnsatisfiedLinkError, so native crash reporting is silently dead. They are the only contract
     * between the Java and the C++ side of this module.
     */
    @Test
    public void nativeLibrariesExportTheJniEntryPoints() throws IOException {
        List<String> failures = new ArrayList<>();

        for (String abi : NativeLibraries.ABIS) {
            File library = new File(new File(NativeLibraries.PACKAGED_DIR, abi), NativeLibraries.SDK_LIBRARY);
            Set<String> exported = ElfFile.read(library).definedDynamicSymbolNames();

            for (String entryPoint : JNI_ENTRY_POINTS) {
                if (!exported.contains(entryPoint)) {
                    failures.add(abi + "/" + NativeLibraries.SDK_LIBRARY + " does not export " + entryPoint);
                }
            }
        }

        assertTrue("JNI entry points missing from the prebuilt libraries: " + failures, failures.isEmpty());
    }

    /**
     * ndk-build strips what it compiles, but libc++_shared.so is copied straight out of the NDK
     * sysroot where it still carries several megabytes of debug information. build.sh strips it; this
     * catches a rebuild that skipped that step and would have inflated the AAR for every integrator.
     */
    @Test
    public void nativeLibrariesAreStrippedOfDebugInformation() throws IOException {
        List<String> failures = new ArrayList<>();

        for (File library : NativeLibraries.packaged()) {
            ElfFile elf = ElfFile.read(library);
            for (String section : elf.sectionNames()) {
                if (section.equals(".symtab") || section.startsWith(".debug")) {
                    failures.add(elf + " still has " + section);
                }
            }
        }

        assertTrue("Debug information left in the prebuilt libraries: " + failures, failures.isEmpty());
    }

    /**
     * build.sh refuses to run with any NDK other than the one in ndk.version, and the NDK writes its
     * release name and build number into every library it links. Reading that back closes the loop:
     * the committed binaries provably come from the pinned toolchain, and moving to another NDK is a
     * deliberate edit of one file rather than whatever happened to be on the maintainer's PATH.
     */
    @Test
    public void nativeLibrariesWereBuiltWithThePinnedNdk() throws IOException {
        String pinned = NativeLibraries.pinnedNdkVersion();
        String pinnedBuildNumber = NativeLibraries.pinnedNdkBuildNumber();
        List<String> failures = new ArrayList<>();

        for (String abi : NativeLibraries.ABIS) {
            File library = new File(new File(NativeLibraries.PACKAGED_DIR, abi), NativeLibraries.SDK_LIBRARY);
            ElfFile elf = ElfFile.read(library);
            String buildNumber = elf.androidNdkBuildNumber();

            if (buildNumber == null) {
                failures.add(abi + "/" + NativeLibraries.SDK_LIBRARY + " records no NDK build number in .note.android.ident");
            } else if (!buildNumber.equals(pinnedBuildNumber)) {
                failures.add(abi + "/" + NativeLibraries.SDK_LIBRARY + " was built with NDK " + elf.androidNdkVersion()
                    + " (build " + buildNumber + "), not the pinned " + pinned);
            }
        }

        assertTrue("Prebuilt libraries do not come from the NDK in " + NativeLibraries.NDK_VERSION_FILE + ": " + failures,
            failures.isEmpty());
    }

    /**
     * APP_PLATFORM in Application.mk decides the oldest Android the libraries can load on. Raising it
     * above the module's minSdk would let the SDK install on devices where the native library cannot
     * resolve its imports, which surfaces as a crash on first use rather than a build failure.
     */
    @Test
    public void nativeLibrariesTargetNoPlatformNewerThanTheModuleMinSdk() throws IOException {
        List<String> failures = new ArrayList<>();

        for (File library : NativeLibraries.packaged()) {
            ElfFile elf = ElfFile.read(library);
            Integer apiLevel = elf.androidApiLevel();

            if (apiLevel == null) {
                failures.add(elf + " has no .note.android.ident, so the NDK that built it is unknown");
            } else if (apiLevel > MODULE_MIN_SDK) {
                failures.add(elf + " was built against API " + apiLevel + ", above minSdk " + MODULE_MIN_SDK);
            }
        }

        assertTrue("Prebuilt libraries do not match the module's minSdk: " + failures, failures.isEmpty());
    }
}
