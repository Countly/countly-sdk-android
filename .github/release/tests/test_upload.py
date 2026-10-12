import datetime
import tempfile
import unittest
from pathlib import Path

from index import rewrite_index
from plan import plan_for_tag
from tests.builders import write_artifact
from tests.support import FakeBucket, android_config
from upload import IMMUTABLE, UploadError, content_type, upload_order, upload_release

POM = "ly/count/android/sdk/26.2.0/sdk-26.2.0.pom"


def quiet(line):
    """Swallows log lines."""


class UploadTest(unittest.TestCase):
    def setUp(self):
        self.config = android_config()
        self.staging = Path(tempfile.mkdtemp())
        self.plan = plan_for_tag(self.config, "26.2.0")

    def test_pom_family_goes_last(self):
        names = ["a.pom", "a.pom.asc", "a.pom.sha1", "a.aar", "a.aar.sha1", "a.module"]
        self.assertEqual(upload_order(names, "a.pom"), ["a.aar", "a.aar.sha1", "a.module", "a.pom.asc", "a.pom.sha1", "a.pom"])

    def test_upload_creates_then_skips_identical_files(self):
        write_artifact(self.staging, self.plan.artifacts[0], self.plan.version)
        bucket = FakeBucket()
        upload_release(bucket, self.staging, self.plan, log=quiet)
        stored = dict(bucket.objects)
        self.assertEqual(bucket.calls[-1], POM)
        self.assertEqual(stored[POM][2], IMMUTABLE)
        self.assertEqual(stored["ly/count/android/sdk/26.2.0/sdk-26.2.0.aar"][1], "application/java-archive")
        upload_release(bucket, self.staging, self.plan, log=quiet)
        self.assertEqual(bucket.objects, stored)

    def test_rerun_keeps_signatures_from_the_first_attempt(self):
        folder = write_artifact(self.staging, self.plan.artifacts[0], self.plan.version)
        signature = folder / "sdk-26.2.0.pom.asc"
        signature.write_bytes(b"first attempt")
        bucket = FakeBucket()
        upload_release(bucket, self.staging, self.plan, log=quiet)
        signature.write_bytes(b"second attempt")
        upload_release(bucket, self.staging, self.plan, log=quiet)
        self.assertEqual(bucket.objects[POM + ".asc"][0], b"first attempt")

    def test_different_bytes_stop_the_upload(self):
        folder = write_artifact(self.staging, self.plan.artifacts[0], self.plan.version)
        bucket = FakeBucket()
        upload_release(bucket, self.staging, self.plan, log=quiet)
        (folder / "sdk-26.2.0.module").write_bytes(b"rebuilt")
        with self.assertRaises(UploadError):
            upload_release(bucket, self.staging, self.plan, log=quiet)

    def test_interrupted_upload_is_never_listed(self):
        write_artifact(self.staging, self.plan.artifacts[0], self.plan.version)
        bucket = FakeBucket(fail_on=POM)
        with self.assertRaises(RuntimeError):
            upload_release(bucket, self.staging, self.plan, log=quiet)
        self.assertIn("ly/count/android/sdk/26.2.0/sdk-26.2.0.aar", bucket.objects)
        self.assertEqual(rewrite_index(bucket, "ly.count.android", "sdk", datetime.datetime(2026, 10, 27), log=quiet), [])

    def test_content_types(self):
        cases = [("a.pom", "application/xml"), ("a.pom.sha1", "text/plain; charset=utf-8"), ("a.aar.asc", "application/pgp-signature"), ("a.module", "application/json"), ("a-cyclonedx.json", "application/json"), ("a.jar", "application/java-archive"), ("a.bin", "application/octet-stream")]
        for name, kind in cases:
            with self.subTest(name=name):
                self.assertEqual(content_type(name), kind)


if __name__ == "__main__":
    unittest.main()
