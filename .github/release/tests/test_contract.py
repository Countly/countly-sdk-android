import tempfile
import unittest
from pathlib import Path

from contract import actual_contract, committed_library_problems, compare, load_alignments, module_dependency_problems, summary_rows
from plan import plan_for_tag
from tests.builders import aar_bytes, elf_bytes, jar_bytes, module_bytes, pom_bytes, write_artifact
from tests.support import android_config

ALIGNED = elf_bytes(16384)
LIBRARIES = {"arm64-v8a/libcountly_native.so": ALIGNED, "x86_64/libcountly_native.so": ALIGNED}
KOTLIN = "org.jetbrains.kotlin:kotlin-stdlib:2.4.0"
NATIVE_CONTRACT = {
    "schema": 1,
    "coordinates": "ly.count.android:sdk-native",
    "pomDependencies": [],
    "moduleVariantAttributes": {"org.gradle.jvm.version": None},
    "aarMetadata": {"minCompileSdk": "21"},
    "maxClassFileMajor": 52,
    "nativeLibraries": {"abis": ["arm64-v8a", "x86_64"], "loadAlignment64": 16384},
}


class ContractTest(unittest.TestCase):
    def setUp(self):
        self.config = android_config()
        self.staging = Path(tempfile.mkdtemp())
        self.native = plan_for_tag(self.config, "native-26.3.0").artifacts[0]

    def stage_native(self, majors=(52,), metadata=None, libraries=None, pom_dependencies=(), module_dependencies=()):
        """Stages sdk-native 26.3.0 with the given contents and returns the contract it actually fulfils."""
        main = aar_bytes(list(majors), metadata or {"minCompileSdk": "21"}, LIBRARIES if libraries is None else libraries)
        write_artifact(self.staging, self.native, "26.3.0", main=main, pom=pom_bytes(list(pom_dependencies)), module=module_bytes(dependencies=module_dependencies))
        return actual_contract(self.staging, self.native, "26.3.0")

    def test_actual_contract_has_the_contract_file_shape(self):
        self.assertEqual(self.stage_native(), NATIVE_CONTRACT)

    def test_matching_contract(self):
        self.assertEqual(compare(NATIVE_CONTRACT, self.stage_native()), [])

    def test_regressions_are_reported(self):
        actual = self.stage_native(
            majors=(52, 55), metadata={"minCompileSdk": "37"}, libraries={"arm64-v8a/libcountly_native.so": elf_bytes(4096)},
            pom_dependencies=[KOTLIN + ":runtime"], module_dependencies=[KOTLIN],
        )
        problems = compare(NATIVE_CONTRACT, actual)
        joined = "\n".join(problems)
        self.assertEqual(len(problems), 5, joined)
        for fragment in ["kotlin-stdlib", "minCompileSdk is 37", "major version 55", "native abis", "aligned to 4096"]:
            self.assertIn(fragment, joined)

    def test_dependency_only_in_the_module_file_is_reported(self):
        self.stage_native()
        self.assertEqual(module_dependency_problems(self.staging, self.native, "26.3.0"), [])
        self.stage_native(module_dependencies=[KOTLIN])
        self.assertEqual(module_dependency_problems(self.staging, self.native, "26.3.0"), [f"the .module declares ['{KOTLIN}'] but the POM declares []"])

    def test_jar_contract_reads_the_jvm_attribute(self):
        plugin = plan_for_tag(self.config, "plugin-26.3.0").artifacts[0]
        okhttp = "com.squareup.okhttp3:okhttp:4.12.0"
        write_artifact(self.staging, plugin, "26.3.0", main=jar_bytes([52]), module=module_bytes(8, [okhttp]), pom=pom_bytes([okhttp + ":runtime"]))
        self.assertEqual(actual_contract(self.staging, plugin, "26.3.0"), {
            "schema": 1,
            "coordinates": "ly.count.android:sdk-plugin",
            "pomDependencies": [okhttp + ":runtime"],
            "moduleVariantAttributes": {"org.gradle.jvm.version": 8},
            "maxClassFileMajor": 52,
        })
        self.assertEqual(module_dependency_problems(self.staging, plugin, "26.3.0"), [])

    def test_native_libraries_must_be_the_committed_files(self):
        repo = Path(tempfile.mkdtemp())
        for name, data in LIBRARIES.items():
            path = repo / "sdk-native/libs" / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        aar = aar_bytes([52], {"minCompileSdk": "21"}, LIBRARIES)
        self.assertEqual(committed_library_problems(aar, repo, "sdk-native/libs"), [])
        (repo / "sdk-native/libs/x86_64/libcountly_native.so").write_bytes(elf_bytes(4096))
        (repo / "sdk-native/libs/x86").mkdir()
        (repo / "sdk-native/libs/x86/libcountly_native.so").write_bytes(b"\x7fELF")
        self.assertEqual(committed_library_problems(aar, repo, "sdk-native/libs"), [
            "sdk-native/libs/x86/libcountly_native.so is missing from the AAR",
            "jni/x86_64/libcountly_native.so differs from the committed sdk-native/libs/x86_64/libcountly_native.so",
        ])

    def test_load_alignments(self):
        self.assertEqual(load_alignments(elf_bytes(16384, loads=3)), [16384, 16384, 16384])
        self.assertEqual(load_alignments(b"\x7fELF"), [])
        self.assertEqual(load_alignments(b"\x7fELF\x01\x01" + b"\x00" * 58), [])

    def test_summary_rows(self):
        rows = summary_rows(NATIVE_CONTRACT, self.stage_native())
        self.assertIn(("native ABIs", "arm64-v8a, x86_64", "arm64-v8a, x86_64"), rows)
        self.assertIn(("aar-metadata minCompileSdk", "21", "21"), rows)
        self.assertIn(("64-bit LOAD alignment", "at least 16384", "16384"), rows)


if __name__ == "__main__":
    unittest.main()
