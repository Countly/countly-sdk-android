import subprocess
import unittest

from s3 import R2Bucket, S3Error


class RecordingRunner:
    """Stands in for subprocess.run and replays one prepared result."""

    def __init__(self, returncode=0, stdout="", stderr=""):
        self.result = (returncode, stdout, stderr)
        self.calls = []

    def __call__(self, args, **kwargs):
        self.calls.append((args, kwargs))
        return subprocess.CompletedProcess(args, *self.result)


class R2BucketTest(unittest.TestCase):
    def test_create_only_put_uses_if_none_match_and_the_r2_endpoint(self):
        runner = RecordingRunner()
        bucket = R2Bucket("countly-maven", "abc123", runner=runner)
        self.assertEqual(bucket.put_file("a/b.pom", "/tmp/b.pom", "application/xml", "public, max-age=60", create_only=True), "created")
        args, kwargs = runner.calls[0]
        self.assertEqual(args[:3], ["aws", "s3api", "put-object"])
        self.assertIn("--if-none-match", args)
        self.assertEqual(args[args.index("--endpoint-url") + 1], "https://abc123.r2.cloudflarestorage.com")
        self.assertEqual(kwargs["env"]["AWS_DEFAULT_REGION"], "auto")
        self.assertEqual(kwargs["env"]["AWS_REQUEST_CHECKSUM_CALCULATION"], "when_required")

    def test_existing_key_and_missing_key(self):
        exists = R2Bucket("b", "a", runner=RecordingRunner(254, "", "An error occurred (PreconditionFailed) when calling the PutObject operation"))
        self.assertEqual(exists.put_file("k", "/tmp/f", "t", "c", create_only=True), "exists")
        missing = R2Bucket("b", "a", runner=RecordingRunner(254, "", "An error occurred (404) when calling the HeadObject operation: Not Found"))
        self.assertIsNone(missing.head("k"))

    def test_other_errors_raise(self):
        broken = R2Bucket("b", "a", runner=RecordingRunner(255, "", "An error occurred (AccessDenied)"))
        with self.assertRaises(S3Error):
            broken.put_file("k", "/tmp/f", "t", "c", create_only=True)

    def test_head_and_list(self):
        head = R2Bucket("b", "a", runner=RecordingRunner(0, '{"ETag": "\\"0cc175b9c0f1b6a831c399e269772661\\"", "ContentLength": 1}'))
        self.assertEqual(head.head("k"), {"etag": "0cc175b9c0f1b6a831c399e269772661", "size": 1})
        listing = R2Bucket("b", "a", runner=RecordingRunner(0, '{"Contents": [{"Key": "x/1"}, {"Key": "x/2"}]}'))
        self.assertEqual(listing.list_keys("x/"), ["x/1", "x/2"])
        self.assertEqual(R2Bucket("b", "a", runner=RecordingRunner(0, "")).list_keys("x/"), [])


if __name__ == "__main__":
    unittest.main()
