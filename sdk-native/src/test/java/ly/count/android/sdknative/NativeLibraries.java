package ly.count.android.sdknative;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Locates the prebuilt native libraries the AAR ships.
 *
 * The ABI and file lists here mirror src/cpp_precompilation/Application.mk. Keeping them written out
 * rather than derived from whatever happens to be on disk is the point: a rebuild that drops an ABI,
 * or a stale folder left behind by an older one, shows up as a failing test instead of silently
 * changing what gets published.
 */
final class NativeLibraries {
    /** APP_ABI in Application.mk: the ABIs build.sh still produces. */
    static final List<String> ABIS = Collections.unmodifiableList(Arrays.asList("armeabi-v7a", "arm64-v8a", "x86", "x86_64"));

    /**
     * Still shipped, but no longer produced by APP_ABI, so held to none of the checks that describe a
     * current build.
     *
     * armeabi (ARMv5, soft float) left the NDK in r17, so build.sh cannot regenerate it and the
     * committed binary is frozen at whatever the toolchain of the day emitted in 2019. It links the
     * platform's libstdc++.so rather than libc++_shared.so, so it loads on its own, but it predates
     * getBreakpadVersion and getBreakpadChecksum and exports only init and testCrash. It also carries
     * no .note.android.ident, so the platform it targets cannot be read back.
     *
     * Android picks one primary ABI per install, highest priority first, so this folder is only ever
     * chosen on a device that reports armeabi without armeabi-v7a: genuine ARMv5/ARMv6 hardware, which
     * tops out far below this module's minSdk. It stays because dropping it from a published artifact
     * would be a breaking change and nothing forces the issue.
     */
    static final List<String> FROZEN_ABIS = Collections.unmodifiableList(Arrays.asList("armeabi"));

    /** The ABIs Google Play enforces the 16 KB page size requirement on. */
    static final List<String> SIXTY_FOUR_BIT_ABIS = Collections.unmodifiableList(Arrays.asList("arm64-v8a", "x86_64"));

    /** The module itself, plus the shared STL it is built against. Both have to be present. */
    static final String SDK_LIBRARY = "libcountly_native.so";
    static final String STL_LIBRARY = "libc++_shared.so";

    /** Written by src/cpp_precompilation/build.sh and committed. */
    static final File BUILD_OUTPUT_DIR = new File("libs");

    /** The copyLibs task stages BUILD_OUTPUT_DIR here, and AGP packages this into the AAR. */
    static final File PACKAGED_DIR = new File("src/main/jniLibs");

    /** The NDK revision build.sh insists on. One line, nothing else. */
    static final File NDK_VERSION_FILE = new File("src/cpp_precompilation/ndk.version");

    private static final FilenameFilter SHARED_OBJECTS = (dir, name) -> name.endsWith(".so");

    private NativeLibraries() {
    }

    /** The NDK revision pinned in NDK_VERSION_FILE, as the NDK's source.properties spells it: "28.2.13676358". */
    static String pinnedNdkVersion() throws IOException {
        String version = new String(Files.readAllBytes(NDK_VERSION_FILE.toPath()), StandardCharsets.UTF_8).trim();
        assertFalse("Empty " + NDK_VERSION_FILE.getAbsolutePath(), version.isEmpty());
        return version;
    }

    /** The build number part of the pinned revision, which is what the NDK stamps into the libraries. */
    static String pinnedNdkBuildNumber() throws IOException {
        String version = pinnedNdkVersion();
        return version.substring(version.lastIndexOf('.') + 1);
    }

    /** Every shared object packaged into the AAR, across all ABIs. */
    static List<File> packaged() {
        return packaged(ABIS);
    }

    static List<File> packaged(List<String> abis) {
        List<File> libraries = new ArrayList<>();

        for (String abi : abis) {
            File abiFolder = new File(PACKAGED_DIR, abi);
            assertTrue("Missing packaged ABI folder " + abiFolder.getAbsolutePath(), abiFolder.isDirectory());

            File[] found = abiFolder.listFiles(SHARED_OBJECTS);
            if (found != null) {
                Arrays.sort(found);
                libraries.addAll(Arrays.asList(found));
            }
        }

        // Both alignment tests would pass vacuously if this ever came back empty.
        assertFalse("Found no packaged native libraries to inspect", libraries.isEmpty());
        return libraries;
    }

    /** The .so file names directly inside the given ABI folder, sorted, or null if there is no folder. */
    static List<String> sharedObjectNames(File parent, String abi) {
        File abiFolder = new File(parent, abi);
        if (!abiFolder.isDirectory()) {
            return null;
        }

        String[] found = abiFolder.list(SHARED_OBJECTS);
        List<String> names = new ArrayList<>(Arrays.asList(found == null ? new String[0] : found));
        Collections.sort(names);
        return names;
    }

    /** Names of the sub folders of the given directory, sorted. */
    static List<String> abiFolderNames(File parent) {
        File[] found = parent.listFiles(File::isDirectory);
        List<String> names = new ArrayList<>();
        for (File folder : found == null ? new File[0] : found) {
            names.add(folder.getName());
        }
        Collections.sort(names);
        return names;
    }
}
