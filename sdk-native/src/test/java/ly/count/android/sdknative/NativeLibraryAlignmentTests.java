package ly.count.android.sdknative;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
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

    /** The ABIs Google Play enforces the 16 KB page size requirement on. */
    private static final String[] SIXTY_FOUR_BIT_ABIS = { "arm64-v8a", "x86_64" };

    private static final int PT_LOAD = 1;
    private static final int PT_GNU_RELRO = 0x6474e552;

    /**
     * Every loadable segment has to be mappable at a 16 KB granularity. This is the condition the
     * NDK's check_elf_alignment.sh and the Play Console both look at.
     */
    @Test
    public void loadSegmentsAreAtLeast16KbAligned() throws IOException {
        List<String> failures = new ArrayList<>();

        for (File library : shippedSixtyFourBitLibraries()) {
            for (ProgramHeader header : readProgramHeaders(library)) {
                if (header.type == PT_LOAD && header.align < PAGE_SIZE_16KB) {
                    failures.add(describe(library) + " PT_LOAD p_align=" + hex(header.align));
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
     */
    @Test
    public void relroEndsOn16KbBoundaryOrAtItsLoadSegmentEnd() throws IOException {
        List<String> failures = new ArrayList<>();

        for (File library : shippedSixtyFourBitLibraries()) {
            List<ProgramHeader> headers = readProgramHeaders(library);

            for (ProgramHeader relro : headers) {
                if (relro.type != PT_GNU_RELRO) {
                    continue;
                }

                long relroEnd = relro.vaddr + relro.memSize;
                if (relroEnd % PAGE_SIZE_16KB == 0 || relroEnd == containingLoadSegmentEnd(headers, relro)) {
                    continue;
                }

                failures.add(describe(library) + " RELRO ends at " + hex(relroEnd));
            }
        }

        assertTrue("RELRO not protectable on 16 KB pages: " + failures, failures.isEmpty());
    }

    private long containingLoadSegmentEnd(List<ProgramHeader> headers, ProgramHeader relro) {
        for (ProgramHeader header : headers) {
            if (header.type == PT_LOAD && header.vaddr <= relro.vaddr && relro.vaddr + relro.memSize <= header.vaddr + header.memSize) {
                return header.vaddr + header.memSize;
            }
        }
        return -1;
    }

    /**
     * The shared objects packaged into the AAR, for the 64 bit ABIs only. Both alignment tests would
     * pass vacuously if this ever returned nothing, so it asserts that it found libraries.
     */
    private List<File> shippedSixtyFourBitLibraries() {
        List<File> libraries = new ArrayList<>();

        for (String abi : SIXTY_FOUR_BIT_ABIS) {
            File abiFolder = new File("src/main/jniLibs/" + abi);
            assertTrue("Missing packaged ABI folder " + abiFolder.getAbsolutePath(), abiFolder.isDirectory());

            File[] found = abiFolder.listFiles((dir, name) -> name.endsWith(".so"));
            if (found != null) {
                for (File library : found) {
                    libraries.add(library);
                }
            }
        }

        assertFalse("Found no packaged native libraries to inspect", libraries.isEmpty());
        return libraries;
    }

    private String describe(File library) {
        return library.getParentFile().getName() + "/" + library.getName();
    }

    private String hex(long value) {
        return "0x" + Long.toHexString(value);
    }

    private List<ProgramHeader> readProgramHeaders(File library) throws IOException {
        ByteBuffer elf = ByteBuffer.wrap(Files.readAllBytes(library.toPath()));

        assertTrue(describe(library) + " is not an ELF file", elf.limit() > 0x40 && elf.getInt(0) == 0x7f454c46);
        assertTrue(describe(library) + " is not a 64 bit ELF file", elf.get(4) == 2);

        elf.order(elf.get(5) == 2 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

        long tableOffset = elf.getLong(0x20);
        int entrySize = elf.getShort(0x36) & 0xffff;
        int entryCount = elf.getShort(0x38) & 0xffff;

        List<ProgramHeader> headers = new ArrayList<>();
        for (int i = 0; i < entryCount; i++) {
            int entry = (int) tableOffset + i * entrySize;
            ProgramHeader header = new ProgramHeader();
            header.type = elf.getInt(entry);
            header.vaddr = elf.getLong(entry + 0x10);
            header.memSize = elf.getLong(entry + 0x28);
            header.align = elf.getLong(entry + 0x30);
            headers.add(header);
        }
        return headers;
    }

    private static class ProgramHeader {
        int type;
        long vaddr;
        long memSize;
        long align;
    }
}
