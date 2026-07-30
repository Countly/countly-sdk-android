package ly.count.android.sdknative;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal ELF reader for the tests that inspect the prebuilt native libraries.
 *
 * The .so files under sdk-native are compiled by hand from src/cpp_precompilation and committed to
 * the repository. No Gradle task recompiles them, so the only way to notice that a rebuild produced
 * something wrong is to read the committed binaries. Rather than shell out to readelf, which is not
 * present on every machine that runs the unit tests, the handful of fields the tests need are read
 * straight out of the file.
 *
 * Handles both ELF32 and ELF64 in either endianness, because the module ships 32 and 64 bit ABIs.
 */
class ElfFile {
    static final int PT_LOAD = 1;
    static final int PT_GNU_RELRO = 0x6474e552;

    private static final int ELF_MAGIC = 0x7f454c46;
    private static final int ELFCLASS64 = 2;
    private static final int ELFDATA2MSB = 2;

    private final String name;
    private final ByteBuffer bytes;
    private final boolean is64Bit;

    /** Section name to {offset, size}, in the order the section header table lists them. */
    private final Map<String, long[]> sections = new LinkedHashMap<>();

    private ElfFile(String name, ByteBuffer bytes, boolean is64Bit) {
        this.name = name;
        this.bytes = bytes;
        this.is64Bit = is64Bit;
    }

    static ElfFile read(File file) throws IOException {
        ByteBuffer bytes = ByteBuffer.wrap(Files.readAllBytes(file.toPath()));
        if (bytes.limit() < 0x40 || bytes.getInt(0) != ELF_MAGIC) {
            throw new IOException(file + " is not an ELF file");
        }

        boolean is64Bit = bytes.get(4) == ELFCLASS64;
        bytes.order(bytes.get(5) == ELFDATA2MSB ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

        ElfFile elf = new ElfFile(describe(file), bytes, is64Bit);
        elf.readSectionHeaders();
        return elf;
    }

    /** "arm64-v8a/libcountly_native.so", the shape used in assertion messages. */
    private static String describe(File file) {
        return file.getParentFile().getName() + "/" + file.getName();
    }

    @Override public String toString() {
        return name;
    }

    boolean is64Bit() {
        return is64Bit;
    }

    List<ProgramHeader> programHeaders() {
        long tableOffset = is64Bit ? bytes.getLong(0x20) : unsigned(bytes.getInt(0x1c));
        int entrySize = word(is64Bit ? 0x36 : 0x2a);
        int entryCount = word(is64Bit ? 0x38 : 0x2c);

        List<ProgramHeader> headers = new ArrayList<>();
        for (int i = 0; i < entryCount; i++) {
            int entry = (int) tableOffset + i * entrySize;
            ProgramHeader header = new ProgramHeader();
            header.type = bytes.getInt(entry);
            if (is64Bit) {
                header.vaddr = bytes.getLong(entry + 0x10);
                header.memSize = bytes.getLong(entry + 0x28);
                header.align = bytes.getLong(entry + 0x30);
            } else {
                header.vaddr = unsigned(bytes.getInt(entry + 0x08));
                header.memSize = unsigned(bytes.getInt(entry + 0x14));
                header.align = unsigned(bytes.getInt(entry + 0x1c));
            }
            headers.add(header);
        }
        return headers;
    }

    Set<String> sectionNames() {
        return sections.keySet();
    }

    /**
     * Names of the symbols this library defines and exports, so callers can check that the JNI entry
     * points survived a rebuild. Symbols the library imports are skipped: they sit in section index
     * SHN_UNDEF and are not something this library provides.
     */
    Set<String> definedDynamicSymbolNames() {
        Set<String> names = new LinkedHashSet<>();

        long[] dynsym = sections.get(".dynsym");
        long[] dynstr = sections.get(".dynstr");
        if (dynsym == null || dynstr == null) {
            return names;
        }

        int entrySize = is64Bit ? 24 : 16;
        for (long offset = dynsym[0]; offset + entrySize <= dynsym[0] + dynsym[1]; offset += entrySize) {
            int entry = (int) offset;
            int sectionIndex = word(entry + (is64Bit ? 0x06 : 0x0e));
            if (sectionIndex == 0) { // SHN_UNDEF, an import rather than a definition
                continue;
            }
            names.add(stringAt(dynstr[0] + unsigned(bytes.getInt(entry))));
        }
        return names;
    }

    /**
     * The API level recorded in .note.android.ident, which is the platform the NDK compiled against,
     * or null when the note is absent. Anything the library links against above the module's minSdk
     * would fail to resolve at load time on older devices.
     */
    Integer androidApiLevel() {
        long[] note = sections.get(".note.android.ident");
        if (note == null) {
            return null;
        }

        int entry = (int) note[0];
        int nameSize = bytes.getInt(entry);
        // The note name is padded out to a 4 byte boundary before the descriptor starts.
        int descriptor = entry + 12 + ((nameSize + 3) / 4) * 4;
        return bytes.getInt(descriptor);
    }

    private void readSectionHeaders() {
        long tableOffset = is64Bit ? bytes.getLong(0x28) : unsigned(bytes.getInt(0x20));
        int entrySize = word(is64Bit ? 0x3a : 0x2e);
        int entryCount = word(is64Bit ? 0x3c : 0x30);
        int nameTableIndex = word(is64Bit ? 0x3e : 0x32);
        if (tableOffset == 0 || entryCount == 0) {
            return;
        }

        int nameOffsetField = 0x00;
        int offsetField = is64Bit ? 0x18 : 0x10;
        int sizeField = is64Bit ? 0x20 : 0x14;

        int nameTable = (int) tableOffset + nameTableIndex * entrySize;
        long nameTableOffset = is64Bit ? bytes.getLong(nameTable + offsetField) : unsigned(bytes.getInt(nameTable + offsetField));

        for (int i = 0; i < entryCount; i++) {
            int entry = (int) tableOffset + i * entrySize;
            long nameIndex = unsigned(bytes.getInt(entry + nameOffsetField));
            long offset = is64Bit ? bytes.getLong(entry + offsetField) : unsigned(bytes.getInt(entry + offsetField));
            long size = is64Bit ? bytes.getLong(entry + sizeField) : unsigned(bytes.getInt(entry + sizeField));
            sections.put(stringAt(nameTableOffset + nameIndex), new long[] { offset, size });
        }
    }

    private String stringAt(long offset) {
        StringBuilder text = new StringBuilder();
        for (int i = (int) offset; i < bytes.limit(); i++) {
            byte character = bytes.get(i);
            if (character == 0) {
                break;
            }
            text.append((char) (character & 0xff));
        }
        return text.toString();
    }

    /** Reads a 16 bit field. Java has no unsigned short, so widen it by hand. */
    private int word(int offset) {
        return bytes.getShort(offset) & 0xffff;
    }

    private static long unsigned(int value) {
        return value & 0xffffffffL;
    }

    static class ProgramHeader {
        int type;
        long vaddr;
        long memSize;
        long align;

        long end() {
            return vaddr + memSize;
        }
    }
}
