package ly.count.android.plugins

import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

class BreakpadSymbolsTest {
  private static final String DEBUG_ID = 'A139F3051119770088C62C06AD1B2B800'

  @Test
  void parsesTheModuleRecordDumpSymsStartsWith() {
    def module = BreakpadSymbols.parseModuleLine("MODULE Linux arm64 ${DEBUG_ID} libembedded_wizard_activity.so")

    assertEquals('Linux', module.os)
    assertEquals('arm64', module.arch)
    assertEquals(DEBUG_ID, module.debugId)
    assertEquals('libembedded_wizard_activity.so', module.name)
  }

  @Test
  void storePathIsWhereMinidumpStackwalkLooksForTheSymbolFile() {
    def module = new BreakpadSymbols.Module('Linux', 'arm64', DEBUG_ID, 'libfoo.so')

    assertEquals("symbols/libfoo.so/${DEBUG_ID}/libfoo.so.sym".toString(), module.storePath())
  }

  @Test
  void describeGivesTheLineToCompareAgainstTheShippedApk() {
    def module = new BreakpadSymbols.Module('Linux', 'arm64', DEBUG_ID, 'libfoo.so')

    assertEquals("libfoo.so arm64 ${DEBUG_ID}".toString(), module.describe())
  }

  @Test
  void rejectsOutputThatIsNotAModuleRecord() {
    try {
      BreakpadSymbols.parseModuleLine('libfoo.so: could not read ELF header')
      fail('expected the first line to be rejected')
    } catch (IllegalArgumentException e) {
      assertTrue(e.message, e.message.contains('libfoo.so: could not read ELF header'))
    }
  }

  @Test
  void rejectsEmptyOutput() {
    for (String line : [null, '', '   ']) {
      try {
        BreakpadSymbols.parseModuleLine(line)
        fail("expected '${line}' to be rejected")
      } catch (IllegalArgumentException expected) {
        // dump_syms produced nothing usable
      }
    }
  }

  @Test
  void buildTypePlaceholderIsReplaced() {
    assertEquals('intermediates/merged_native_libs/debug',
        BreakpadSymbols.substituteBuildType('intermediates/merged_native_libs/BUILD_TYPE', 'debug'))
  }

  @Test
  void directoryWithoutPlaceholderIsLeftAlone() {
    assertEquals('intermediates/cmake/release/obj',
        BreakpadSymbols.substituteBuildType('intermediates/cmake/release/obj', 'debug'))
  }
}
