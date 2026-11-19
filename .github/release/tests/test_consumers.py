import unittest

from consumers import android_command, gradle_executable, project_agp
from plan import plan_for_tag
from tests.support import android_config

FLOOR = {"name": "AGP 7.4.2 on Gradle 7.5", "gradle": "{env:GRADLE_7}", "agp": "7.4.2", "compileSdk": "24", "buildTools": "30.0.3"}
MIDDLE = {"name": "AGP 8.7.3 on Gradle 8.9", "gradle": "{env:GRADLE_8}", "agp": "8.7.3", "compileSdk": "35", "buildTools": "35.0.0"}
ENVIRON = {"GRADLE_7": "/gradle-7.5/bin/gradle", "GRADLE_8": "/gradle-8.9/bin/gradle"}
CURRENT = {"name": "the checkout's AGP on the wrapper's Gradle", "gradle": "{gradlew}", "agp": "{project}", "compileSdk": "37", "buildTools": "37.0.0"}


class ConsumersTest(unittest.TestCase):
    def setUp(self):
        self.config = android_config()

    def test_config_pairs(self):
        self.assertEqual(self.config["consumerPairs"], [FLOOR, MIDDLE, CURRENT])

    def test_sdk_on_the_floor_pair(self):
        sdk = plan_for_tag(self.config, "26.2.0").artifacts[0]
        command = android_command(FLOOR, "/repo/gradlew", "https://maven.test/", sdk, "26.2.0", "9.3.2", ENVIRON)
        self.assertEqual(command[:3], ["/gradle-7.5/bin/gradle", "-p", ".github/release/consumers/android"])
        for part in ["assembleDebug", "-PagpVersion=7.4.2", "-PconsumerCompileSdk=24", "-PconsumerBuildTools=30.0.3", "-PcountlyRepository=https://maven.test/", "-PcountlyDependency=ly.count.android:sdk:26.2.0", "-PcountlyProbe=sdk"]:
            self.assertIn(part, command)

    def test_plugin_on_the_current_pair_uses_the_checkout_agp(self):
        plugin = plan_for_tag(self.config, "plugin-26.3.0").artifacts[0]
        command = android_command(CURRENT, "/repo/gradlew", "https://maven.test/", plugin, "26.3.0", "9.2.0")
        self.assertEqual(command[0], "/repo/gradlew")
        self.assertIn("-PagpVersion=9.2.0", command)
        self.assertIn("-PcountlyPluginVersion=26.3.0", command)
        self.assertIn("-PcountlyProbe=none", command)
        self.assertFalse(any(part.startswith("-PcountlyDependency=") for part in command))

    def test_each_pair_resolves_its_own_gradle(self):
        self.assertEqual(gradle_executable(FLOOR, "/repo/gradlew", ENVIRON), "/gradle-7.5/bin/gradle")
        self.assertEqual(gradle_executable(MIDDLE, "/repo/gradlew", ENVIRON), "/gradle-8.9/bin/gradle")
        self.assertEqual(gradle_executable(CURRENT, "/repo/gradlew", ENVIRON), "/repo/gradlew")
        with self.assertRaisesRegex(ValueError, "GRADLE_8"):
            gradle_executable(MIDDLE, "/repo/gradlew", {})

    def test_reads_the_agp_version_of_the_checkout(self):
        build_gradle = "buildscript {\n  dependencies {\n    classpath 'com.android.tools.build:gradle:9.2.0'\n    classpath 'com.google.gms:google-services:4.4.3'\n  }\n}\n"
        self.assertEqual(project_agp(build_gradle), "9.2.0")


if __name__ == "__main__":
    unittest.main()
