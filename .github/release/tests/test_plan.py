import tempfile
import unittest
from pathlib import Path

from plan import PlanError, checkout_tags, plan_for_tag, plan_from_json
from tests.support import android_config

MARKER = "ly.count.android.plugins.upload-symbols:ly.count.android.plugins.upload-symbols.gradle.plugin"


class PlanTest(unittest.TestCase):
    def setUp(self):
        self.config = android_config()

    def test_tags(self):
        cases = [
            ("26.2.0", "staging", ["ly.count.android:sdk"], "26.2.0", True),
            ("26.2.0-rc1", "staging", ["ly.count.android:sdk"], "26.2.0-rc1", False),
            ("26.2.0-nw", "staging-nw", ["ly.count.android:sdk-nw"], "26.2.0", True),
            ("26.2.0-rc1-nw", "staging-nw", ["ly.count.android:sdk-nw"], "26.2.0-rc1", False),
            ("native-26.3.0", "staging", ["ly.count.android:sdk-native"], "26.3.0", True),
            ("plugin-26.3.0-rc2", "staging", ["ly.count.android:sdk-plugin", MARKER], "26.3.0-rc2", False),
            ("0.0.1", "staging", ["ly.count.android:sdk"], "0.0.1", True),
        ]
        for tag, branch, artifacts, version, listed in cases:
            with self.subTest(tag=tag):
                plan = plan_for_tag(self.config, tag)
                self.assertEqual(plan.branch, branch)
                self.assertEqual([artifact.coordinates for artifact in plan.artifacts], artifacts)
                self.assertEqual(plan.version, version)
                self.assertEqual(plan.listed, listed)
                self.assertEqual(plan.prerelease, not listed)

    def test_refused_tags(self):
        refused = ["v26.2.0", "26.2", "26.2.0-RC1", "26.2.0-rc.1", " 26.2.0", "26.2.0-nw-rc1", "native-26.3.0-nw", "26.2.0/../x", "java-26.2.0", "\u0662\u0666.2.0"]
        for tag in refused:
            with self.subTest(tag=tag):
                with self.assertRaises(PlanError):
                    plan_for_tag(self.config, tag)

    def test_modules_and_round_trip(self):
        plan = plan_for_tag(self.config, "plugin-26.3.0")
        self.assertEqual(plan.modules, [":upload-plugin"])
        self.assertEqual(plan.artifacts[0].module_dir, "upload-plugin")
        self.assertEqual(plan.artifacts[1].folder("26.3.0"), "ly/count/android/plugins/upload-symbols/ly.count.android.plugins.upload-symbols.gradle.plugin/26.3.0")
        self.assertEqual(plan_from_json(self.config, plan.to_json()), plan)

    def test_checkout_tags_follow_the_branch_and_each_version_file(self):
        root = Path(tempfile.mkdtemp())
        for relative, version in [("gradle.properties", "26.2.0"), ("sdk-native/gradle.properties", "26.3.0"), ("upload-plugin/gradle.properties", "26.3.1-rc1")]:
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(f"GROUP=ly.count.android\r\nVERSION_NAME={version}\r\n".encode("utf-8"))
        self.assertEqual(checkout_tags(self.config, root, "staging"), ["26.2.0", "native-26.3.0", "plugin-26.3.1-rc1"])
        self.assertEqual(checkout_tags(self.config, root, "staging-nw"), ["26.2.0-nw"])
        self.assertEqual(checkout_tags(self.config, root, "master"), [])

    def test_checkout_tags_need_a_readable_version(self):
        root = Path(tempfile.mkdtemp())
        with self.assertRaises(PlanError):
            checkout_tags(self.config, root, "staging-nw")
        (root / "gradle.properties").write_bytes(b"GROUP=ly.count.android\n")
        with self.assertRaises(PlanError):
            checkout_tags(self.config, root, "staging-nw")

    def test_artifact_details(self):
        native = plan_for_tag(self.config, "native-26.3.0").artifacts[0]
        self.assertEqual((native.native_libraries, native.consumer_probe, native.sbom_fragment), ("sdk-native/libs", "native", "sdk-native/sbom-native-components.json"))
        marker = plan_for_tag(self.config, "plugin-26.3.0").artifacts[1]
        self.assertEqual((marker.packaging, marker.sbom, marker.contract, marker.consumer_probe), ("marker", False, None, None))


if __name__ == "__main__":
    unittest.main()
