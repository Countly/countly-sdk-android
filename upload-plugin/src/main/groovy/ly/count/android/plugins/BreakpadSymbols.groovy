package ly.count.android.plugins

/**
 * The parts of a breakpad symbol upload that need no Gradle: what dump_syms writes, where the symbol
 * store expects it, and how the documented BUILD_TYPE placeholder is resolved. Kept apart from the
 * task so they can be unit tested without a build.
 */
final class BreakpadSymbols {
  /** The first line dump_syms writes for an object: MODULE <os> <arch> <debug id> <file name>. */
  static final class Module {
    final String os
    final String arch
    final String debugId
    final String name

    Module(String os, String arch, String debugId, String name) {
      this.os = os
      this.arch = arch
      this.debugId = debugId
      this.name = name
    }

    /**
     * Where minidump_stackwalk looks for the symbol file inside the uploaded store. The debug id in
     * the path is the only thing that ever matches a crash to this file: it derives from the ELF build
     * id of the exact binary, so a relinked library gets a new directory and the old symbols stop
     * applying to it.
     */
    String storePath() {
      "symbols/${name}/${debugId}/${name}.sym"
    }

    /**
     * One line per library for the build log. When a crash stays unsymbolicated, this debug id is what
     * to compare against the library inside the APK that actually shipped.
     */
    String describe() {
      "${name} ${arch} ${debugId}"
    }
  }

  static Module parseModuleLine(String line) {
    if (line == null || line.trim().isEmpty()) {
      throw new IllegalArgumentException("dump_syms produced no output")
    }
    String[] words = line.trim().split(/\s+/)
    if (words.length < 5 || words[0] != 'MODULE') {
      throw new IllegalArgumentException("Expected a breakpad MODULE record from dump_syms, got: ${line}")
    }
    // The file name is everything after the id, so a name with spaces in it stays whole.
    new Module(words[1], words[2], words[3], words[4..-1].join(' '))
  }

  /** BUILD_TYPE in nativeObjectFilesDir stands for the variant's build type, as documented. */
  static String substituteBuildType(String directory, String buildType) {
    directory.replace('BUILD_TYPE', buildType)
  }

  private BreakpadSymbols() {
  }
}
