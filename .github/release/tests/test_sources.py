import tempfile
import unittest
from pathlib import Path

from plan import plan_for_tag
from sources import check_version_sources
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


if __name__ == "__main__":
    unittest.main()
