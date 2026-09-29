import hashlib
import tempfile
import unittest
from pathlib import Path

from manifest import build_manifest, verify_manifest


class ManifestTest(unittest.TestCase):
    def test_round_trip_and_changes(self):
        staging = Path(tempfile.mkdtemp())
        (staging / "a").mkdir()
        (staging / "a/x.pom").write_bytes(b"one")
        manifest = build_manifest(staging, "26.2.0", "c" * 40)
        self.assertEqual(manifest, {"tag": "26.2.0", "commit": "c" * 40, "files": [{"path": "a/x.pom", "size": 3, "sha256": hashlib.sha256(b"one").hexdigest()}]})
        self.assertEqual(verify_manifest(staging, manifest), [])
        (staging / "a/x.pom").write_bytes(b"two")
        (staging / "a/y.pom").write_bytes(b"new")
        self.assertEqual(verify_manifest(staging, manifest), ["unexpected a/y.pom", "changed a/x.pom"])


if __name__ == "__main__":
    unittest.main()
