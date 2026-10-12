import tempfile
import unittest
from pathlib import Path

from layout import check_staging, expected_files, primary_files, remove_index_files, write_checksums
from plan import plan_for_tag
from tests.builders import write_artifact
from tests.support import android_config


class LayoutTest(unittest.TestCase):
    def setUp(self):
        self.config = android_config()
        self.staging = Path(tempfile.mkdtemp())

    def test_expected_files_of_an_aar(self):
        sdk = plan_for_tag(self.config, "26.2.0").artifacts[0]
        self.assertEqual(primary_files(sdk, "26.2.0"), ["sdk-26.2.0.aar", "sdk-26.2.0.pom", "sdk-26.2.0.module", "sdk-26.2.0-sources.jar", "sdk-26.2.0-javadoc.jar", "sdk-26.2.0-cyclonedx.json"])
        self.assertEqual(len(expected_files(sdk, "26.2.0")), 30)

    def test_marker_has_only_a_pom(self):
        marker = plan_for_tag(self.config, "plugin-26.3.0").artifacts[1]
        pom = "ly.count.android.plugins.upload-symbols.gradle.plugin-26.3.0.pom"
        self.assertEqual(expected_files(marker, "26.3.0"), sorted(pom + suffix for suffix in ["", ".md5", ".sha1", ".sha256", ".sha512"]))

    def test_checksums_are_plain_hex(self):
        path = self.staging / "a.txt"
        path.write_bytes(b"abc")
        write_checksums(path)
        self.assertEqual((self.staging / "a.txt.md5").read_bytes(), b"900150983cd24fb0d6963f7d28e17f72")
        self.assertEqual((self.staging / "a.txt.sha1").read_bytes(), b"a9993e364706816aba3e25717850c26c9cd0d89d")

    def test_complete_staging_passes(self):
        plan = plan_for_tag(self.config, "plugin-26.3.0")
        for artifact in plan.artifacts:
            write_artifact(self.staging, artifact, plan.version)
        self.assertEqual(check_staging(self.staging, plan), [])

    def test_missing_unexpected_and_wrong_files_are_reported(self):
        plan = plan_for_tag(self.config, "26.2.0")
        folder = write_artifact(self.staging, plan.artifacts[0], plan.version)
        (folder / "sdk-26.2.0-javadoc.jar").unlink()
        (folder / "notes.txt").write_bytes(b"x")
        (folder / "sdk-26.2.0.pom.sha1").write_bytes(b"0" * 40)
        (self.staging / "ly/count/android/other").mkdir(parents=True)
        (self.staging / "ly/count/android/other/x.jar").write_bytes(b"x")
        self.assertEqual(check_staging(self.staging, plan), [
            "missing ly/count/android/sdk/26.2.0/sdk-26.2.0-javadoc.jar",
            "unexpected ly/count/android/other/x.jar",
            "unexpected ly/count/android/sdk/26.2.0/notes.txt",
            "wrong checksum ly/count/android/sdk/26.2.0/sdk-26.2.0.pom.sha1",
        ])

    def test_index_files_are_removed(self):
        path = self.staging / "ly/count/android/sdk/maven-metadata.xml.sha1"
        path.parent.mkdir(parents=True)
        path.write_bytes(b"x")
        remove_index_files(self.staging)
        self.assertFalse(path.exists())


if __name__ == "__main__":
    unittest.main()
