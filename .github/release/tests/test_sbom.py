import copy
import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from plan import plan_for_tag
from sbom import check_fragment, merge_fragment, normalize, place_sbom
from tests.support import android_config

PLUGIN_BOM = {
    "bomFormat": "CycloneDX",
    "specVersion": "1.6",
    "serialNumber": "urn:uuid:random",
    "metadata": {
        "timestamp": "2026-10-01T00:00:00Z",
        "component": {"type": "application", "bom-ref": "pkg:maven/ly.count.android/sdk@unspecified?type=jar", "group": "ly.count.android", "name": "sdk", "version": "unspecified"},
        "tools": {"components": [{"type": "application", "name": "cyclonedx-gradle-plugin"}]},
    },
    "components": [{"type": "library", "bom-ref": "pkg:maven/androidx.annotation/annotation@1.3.0?type=jar", "name": "annotation"}],
    "dependencies": [{"ref": "pkg:maven/ly.count.android/sdk@unspecified?type=jar", "dependsOn": ["pkg:maven/androidx.annotation/annotation@1.3.0?type=jar"]}],
}
BREAKPAD = "1c954c096c9be31e03615cb7de1a392a93145a4c"
FRAGMENT = {
    "checks": [
        {"kind": "gitlink", "path": "sdk-native/src/cpp_precompilation/breakpad", "expect": BREAKPAD},
        {"kind": "blob-sha256", "path": "sdk-native/src/cpp_precompilation/lss/linux_syscall_support.h", "expect": hashlib.sha256(b"lss\n").hexdigest()},
    ],
    "components": [{"type": "library", "bom-ref": f"pkg:github/Countly/countly-breakpad@{BREAKPAD}", "name": "countly-breakpad"}],
    "tools": [{"type": "application", "name": "android-ndk", "version": "28.2.13676358"}],
}


def fake_git(gitlink, blob):
    """Answers `git ls-tree HEAD <path>` and `git show HEAD:<path>` like a repository would."""
    def run(*args):
        if args[0] == "ls-tree":
            return f"160000 commit {gitlink}\t{args[2]}\n".encode("utf-8")
        return blob
    return run


class SbomTest(unittest.TestCase):
    def setUp(self):
        self.config = android_config()

    def test_normalize_sets_the_published_coordinates(self):
        sdk_nw = plan_for_tag(self.config, "26.2.0-nw").artifacts[0]
        bom = normalize(copy.deepcopy(PLUGIN_BOM), sdk_nw, "26.2.0", "2026-10-27T10:15:00+03:00", "https://maven.countly.com/")
        ref = "pkg:maven/ly.count.android/sdk-nw@26.2.0?repository_url=https%3A%2F%2Fmaven.countly.com&type=aar"
        self.assertEqual(bom["metadata"]["component"], {"type": "library", "bom-ref": ref, "group": "ly.count.android", "name": "sdk-nw", "version": "26.2.0", "purl": ref, "licenses": [{"license": {"id": "MIT"}}]})
        self.assertEqual(bom["metadata"]["timestamp"], "2026-10-27T10:15:00+03:00")
        self.assertNotIn("serialNumber", bom)
        self.assertEqual(bom["dependencies"][0]["ref"], ref)

    def test_fragment_is_merged(self):
        native = plan_for_tag(self.config, "native-26.3.0").artifacts[0]
        bom = merge_fragment(normalize(copy.deepcopy(PLUGIN_BOM), native, "26.3.0", "t", "https://maven.countly.com/"), FRAGMENT)
        self.assertIn(FRAGMENT["components"][0], bom["components"])
        self.assertIn(FRAGMENT["components"][0]["bom-ref"], bom["dependencies"][0]["dependsOn"])
        self.assertIn(FRAGMENT["tools"][0], bom["metadata"]["tools"]["components"])

    def test_fragment_checks_read_committed_content(self):
        self.assertEqual(check_fragment(FRAGMENT, fake_git(BREAKPAD, b"lss\n")), [])
        problems = check_fragment(FRAGMENT, fake_git("eb39669bb51f96add21da63a058fa3e1b63cdf08", b"lss\r\n"))
        self.assertEqual(len(problems), 2)
        self.assertIn("eb39669bb51f96add21da63a058fa3e1b63cdf08", problems[0])

    def test_place_sbom_writes_the_file_and_its_checksums(self):
        staging = Path(tempfile.mkdtemp())
        source = staging / "bom.json"
        source.write_bytes(json.dumps(PLUGIN_BOM).encode("utf-8"))
        sdk = plan_for_tag(self.config, "26.2.0").artifacts[0]
        target = place_sbom(source, staging, sdk, "26.2.0", "t", "https://maven.countly.com/")
        self.assertEqual(target, staging / "ly/count/android/sdk/26.2.0/sdk-26.2.0-cyclonedx.json")
        self.assertEqual(json.loads(target.read_bytes())["metadata"]["component"]["name"], "sdk")
        for suffix in [".md5", ".sha1", ".sha256", ".sha512"]:
            self.assertTrue(Path(f"{target}{suffix}").is_file())


if __name__ == "__main__":
    unittest.main()
