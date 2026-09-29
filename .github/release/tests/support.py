"""Shared test helpers: the repository configuration and in-memory stand-ins for R2 and the web."""

import hashlib
from pathlib import Path

from plan import load_config


def android_config():
    """The real config.json of this repository."""
    return load_config(Path(__file__).resolve().parents[1] / "config.json")


class FakeBucket:
    """In-memory stand-in for s3.R2Bucket with MD5 ETags; `fail_on` makes one upload fail like a lost connection."""

    def __init__(self, fail_on=None):
        self.objects = {}
        self.calls = []
        self.fail_on = fail_on

    def head(self, key):
        """Returns {'etag', 'size'} or None."""
        if key not in self.objects:
            return None
        data = self.objects[key][0]
        return {"etag": hashlib.md5(data).hexdigest(), "size": len(data)}

    def put_file(self, key, path, content_type, cache_control, create_only):
        """Stores a file; with create_only an existing key is left alone and 'exists' is returned."""
        if key == self.fail_on:
            raise RuntimeError(f"connection lost while uploading {key}")
        self.calls.append(key)
        if create_only and key in self.objects:
            return "exists"
        self.objects[key] = (Path(path).read_bytes(), content_type, cache_control)
        return "created"

    def list_keys(self, prefix):
        """Keys starting with the prefix, sorted."""
        return sorted(key for key in self.objects if key.startswith(prefix))


class FakeWeb:
    """Serves a dict of URL to bytes (ignoring ?nocache=...); anything else is a 404 (None)."""

    def __init__(self, pages=None):
        self.pages = dict(pages or {})
        self.requests = []

    def __call__(self, url):
        self.requests.append(url)
        return self.pages.get(url.split("?nocache=")[0])
