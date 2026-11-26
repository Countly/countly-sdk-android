import tempfile
import unittest
from pathlib import Path

from plan import plan_for_tag
from sources import changelog_section, check_version_sources
from tests.support import android_config

COUNTLY_JAVA = 'public class Countly {\n    private final String DEFAULT_COUNTLY_SDK_VERSION_STRING = "{version}";\n}\n'


class SourcesTest(unittest.TestCase):
    def setUp(self):
        self.config = android_config()
        self.root = Path(tempfile.mkdtemp())

    def write(self, relative, text):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(text.encode("utf-8"))

    def write_sdk(self, gradle_version, java_version, changelog):
        self.write("gradle.properties", f"GROUP=ly.count.android\nVERSION_NAME={gradle_version}\n")
        self.write("sdk/src/main/java/ly/count/android/sdk/Countly.java", COUNTLY_JAVA.replace("{version}", java_version))
        self.write("CHANGELOG.md", changelog)

    def test_matching_sources(self):
        self.write_sdk("26.2.0", "26.2.0", "## 26.2.0\n* Fixed things.\n")
        self.assertEqual(check_version_sources(self.root, plan_for_tag(self.config, "26.2.0"), self.config), [])

    def test_mismatches_are_reported(self):
        self.write_sdk("26.1.9", "26.2.0", "## XX.XX.XX\n")
        problems = check_version_sources(self.root, plan_for_tag(self.config, "26.2.0"), self.config)
        self.assertEqual(problems, ["gradle.properties: version is 26.1.9, the tag says 26.2.0", "CHANGELOG.md: no '## 26.2.0' heading"])

    def test_candidates_need_no_changelog_heading(self):
        self.write_sdk("26.2.0-rc1", "26.2.0-rc1", "## XX.XX.XX\n")
        self.assertEqual(check_version_sources(self.root, plan_for_tag(self.config, "26.2.0-rc1"), self.config), [])

    def test_nw_heading_uses_the_tag(self):
        self.write_sdk("26.2.0", "26.2.0", "## 26.2.0-nw\n")
        self.assertEqual(check_version_sources(self.root, plan_for_tag(self.config, "26.2.0-nw"), self.config), [])

    def test_add_on_uses_its_own_version(self):
        self.write("sdk-native/gradle.properties", "POM_ARTIFACT_ID=sdk-native\nVERSION_NAME=26.3.0\n")
        self.write("sdk-native/CHANGELOG.md", "## 26.3.0\n")
        self.assertEqual(check_version_sources(self.root, plan_for_tag(self.config, "native-26.3.0"), self.config), [])

    def test_windows_line_endings_are_accepted(self):
        self.write_sdk("26.2.0\r", "26.2.0", "## 26.2.0\r\n")
        self.assertEqual(check_version_sources(self.root, plan_for_tag(self.config, "26.2.0"), self.config), [])

    def section(self, tag):
        """The changelog section the notes step would send for the tag."""
        return changelog_section(self.root, plan_for_tag(self.config, tag), self.config)

    def test_section_stops_at_the_next_version(self):
        self.write("CHANGELOG.md", "## 26.2.1\n* Fixed c.\n\n## 26.2.0\n\n* Added a.\n  * Nested a.\n\n* Fixed b.\n\n## 26.1.0\n* Added a.\n* Fixed b.\n")
        self.assertEqual(self.section("26.2.0"), "* Added a.\n  * Nested a.\n\n* Fixed b.")

    def test_section_of_the_last_version_runs_to_the_end(self):
        self.write("CHANGELOG.md", "## 26.2.0\n* Added a.\n\n## 26.1.0\n* Fixed b.\n")
        self.assertEqual(self.section("26.1.0"), "* Fixed b.")

    def test_section_heading_must_match_exactly(self):
        self.write("CHANGELOG.md", "## 26.2.10\n* Added a.\n\n## 26.2.0-nw\n* Added b.\n")
        self.assertIsNone(self.section("26.2.0"))
        self.assertEqual(self.section("26.2.0-nw"), "* Added b.")

    def test_section_of_an_add_on_comes_from_its_own_changelog(self):
        self.write("CHANGELOG.md", "## 26.3.0\n* SDK entry.\n")
        self.write("sdk-native/CHANGELOG.md", "## 26.3.0\n* Native entry.\n")
        self.assertEqual(self.section("native-26.3.0"), "* Native entry.")

    def test_section_drops_windows_line_endings(self):
        self.write("CHANGELOG.md", "## 26.2.0\r\n* Added a.\r\n\r\n* Fixed b.\r\n")
        self.assertEqual(self.section("26.2.0"), "* Added a.\n\n* Fixed b.")

    def test_empty_section_is_none(self):
        self.write("CHANGELOG.md", "## 26.2.0\n\n## 26.1.0\n* Fixed b.\n")
        self.assertIsNone(self.section("26.2.0"))


if __name__ == "__main__":
    unittest.main()
