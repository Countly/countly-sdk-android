package ly.count.android.sdknative;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import ly.count.android.sdknative.ElfFile.ProgramHeader;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Android 15 introduced devices with a 16 KB memory page size, and Google Play requires apps
 * targeting Android 15+ to support them. Every 64 bit native library shipped inside this AAR has to
 * be laid out for that page size, otherwise integrators see "Does not support 16KB devices" and can
 * be blocked from publishing.
 *
 * The libraries are prebuilt from src/cpp_precompilation and committed to the repository, so no
 * Gradle task recompiles them and nothing else in the build would notice a regression. These tests
 * read the committed binaries directly.
 */
public class NativeLibraryAlignmentTests {
    private static final long PAGE_SIZE_16KB = 16 * 1024;

    /**
     * Every loadable segment has to be mappable at a 16 KB granularity. This is the condition the
     * NDK's check_elf_alignment.sh and the Play Console both look at.
     */
    @Test
    public void loadSegmentsAreAtLeast16KbAligned() throws IOException {
        List<String> failures = new ArrayList<>();

        for (File library : NativeLibraries.packaged(NativeLibraries.SIXTY_FOUR_BIT_ABIS)) {
            ElfFile elf = ElfFile.read(library);
            for (ProgramHeader header : elf.programHeaders()) {
                if (header.type == ElfFile.PT_LOAD && header.align < PAGE_SIZE_16KB) {
                    failures.add(elf + " PT_LOAD @" + hex(header.vaddr) + " p_align=" + hex(header.align));
                }
            }
        }

        assertTrue("Segments not aligned for 16 KB pages: " + failures, failures.isEmpty());
    }

    /**
     * The dynamic linker can only apply the read-only relocation protection a whole page at a time.
     * That works when the RELRO region ends on a 16 KB boundary, or when nothing writable follows it
     * inside the same loadable segment. When neither holds, the APK Analyzer reports "RELRO is not a
     * suffix and its end is not 16KB aligned" and the tail of RELRO stays writable at runtime.
     *
     * Getting this right needs both -Wl,-z,max-page-size=16384 and -Wl,-z,common-page-size=16384.
     * max-page-size on its own aligns the segments but leaves RELRO padded to lld's separate 4 KB
     * common-page-size default, which trips exactly this check.
     */
    @Test
    public void relroEndsOn16KbBoundaryOrAtItsLoadSegmentEnd() throws IOException {
        List<String> failures = new ArrayList<>();

        for (File library : NativeLibraries.packaged(NativeLibraries.SIXTY_FOUR_BIT_ABIS)) {
            ElfFile elf = ElfFile.read(library);
            List<ProgramHeader> headers = elf.programHeaders();

            for (ProgramHeader relro : headers) {
                if (relro.type != ElfFile.PT_GNU_RELRO) {
                    continue;
                }

                long relroEnd = relro.end();
                if (relroEnd % PAGE_SIZE_16KB == 0 || relroEnd == containingLoadSegmentEnd(headers, relro)) {
                    continue;
                }

                failures.add(elf + " RELRO ends at " + hex(relroEnd));
            }
        }

        assertTrue("RELRO not protectable on 16 KB pages: " + failures, failures.isEmpty());
    }

    private long containingLoadSegmentEnd(List<ProgramHeader> headers, ProgramHeader relro) {
        for (ProgramHeader header : headers) {
            if (header.type == ElfFile.PT_LOAD && header.vaddr <= relro.vaddr && relro.end() <= header.end()) {
                return header.end();
            }
        }
        return -1;
    }

    private String hex(long value) {
        return "0x" + Long.toHexString(value);
    }
}
