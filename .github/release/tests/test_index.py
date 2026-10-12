import datetime
import hashlib
import unittest

from index import METADATA_CACHE, complete_versions, listed_versions, render, rewrite_index
from tests.support import FakeBucket
from upload import IMMUTABLE

NOW = datetime.datetime(2026, 10, 27, 10, 15, 0)


def quiet(line):
    """Swallows log lines."""


class IndexTest(unittest.TestCase):
    def test_complete_versions_need_a_pom(self):
        keys = [
            "ly/count/android/sdk/26.2.0/sdk-26.2.0.pom",
            "ly/count/android/sdk/26.2.0/sdk-26.2.0.aar",
            "ly/count/android/sdk/26.3.0/sdk-26.3.0.aar",
            "ly/count/android/sdk/maven-metadata.xml",
            "ly/count/android/sdk-nw/26.2.0/sdk-nw-26.2.0.pom",
        ]
        self.assertEqual(complete_versions(keys, "ly/count/android", "sdk"), {"26.2.0"})

    def test_only_final_versions_are_listed_in_numeric_order(self):
        folders = {"26.1.10", "26.2.0-rc1", "26.1.6-rc2", "26.1.6", "26.10.0", "26.9.1", "19.09-sdk2-rc", "21.11.0-RC1", "26.3.0-hotfix"}
        self.assertEqual(listed_versions(folders), ["26.1.6", "26.1.10", "26.9.1", "26.10.0"])

    def test_render(self):
        self.assertEqual(render("ly.count.android", "sdk", ["26.1.10", "26.2.0"], NOW), "\n".join([
            '<?xml version="1.0" encoding="UTF-8"?>',
            "<metadata>",
            "  <groupId>ly.count.android</groupId>",
            "  <artifactId>sdk</artifactId>",
            "  <versioning>",
            "    <latest>26.2.0</latest>",
            "    <release>26.2.0</release>",
            "    <versions>",
            "      <version>26.1.10</version>",
            "      <version>26.2.0</version>",
            "    </versions>",
            "    <lastUpdated>20261027101500</lastUpdated>",
            "  </versioning>",
            "</metadata>",
            "",
        ]))

    def test_rewrite_writes_checksums_first_and_the_body_last(self):
        bucket = FakeBucket()
        for version in ["26.1.6", "26.2.0", "26.2.0-rc1"]:
            bucket.objects[f"ly/count/android/sdk/{version}/sdk-{version}.pom"] = (b"pom", "application/xml", IMMUTABLE)
        self.assertEqual(rewrite_index(bucket, "ly.count.android", "sdk", NOW, log=quiet), ["26.1.6", "26.2.0"])
        self.assertEqual(bucket.calls, [f"ly/count/android/sdk/maven-metadata.xml.{suffix}" for suffix in ["md5", "sha1", "sha256", "sha512"]] + ["ly/count/android/sdk/maven-metadata.xml"])
        body, kind, cache = bucket.objects["ly/count/android/sdk/maven-metadata.xml"]
        self.assertEqual((kind, cache), ("application/xml", METADATA_CACHE))
        self.assertEqual(bucket.objects["ly/count/android/sdk/maven-metadata.xml.sha1"][0], hashlib.sha1(body).hexdigest().encode("ascii"))
        self.assertIn(b"<release>26.2.0</release>", body)

    def test_no_listed_version_writes_nothing(self):
        bucket = FakeBucket()
        bucket.objects["ly/count/android/sdk/26.2.0-rc1/sdk-26.2.0-rc1.pom"] = (b"pom", "application/xml", IMMUTABLE)
        self.assertEqual(rewrite_index(bucket, "ly.count.android", "sdk", NOW, log=quiet), [])
        self.assertEqual(bucket.calls, [])


if __name__ == "__main__":
    unittest.main()
