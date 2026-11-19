"""Minimal R2 access through the AWS CLI (S3 API), so the release job needs no Python packages."""

import json
import os
import subprocess


class S3Error(Exception):
    """An S3 call failed for a reason other than 'not found' or 'already exists'."""


class R2Bucket:
    """One R2 bucket, reached with `aws s3api` at the account's R2 endpoint using the AWS_* credentials in the environment."""

    def __init__(self, bucket, account_id, runner=subprocess.run):
        self.bucket = bucket
        self.endpoint = f"https://{account_id}.r2.cloudflarestorage.com"
        self.runner = runner

    def _aws(self, *args):
        env = dict(os.environ, AWS_DEFAULT_REGION="auto", AWS_REQUEST_CHECKSUM_CALCULATION="when_required", AWS_RESPONSE_CHECKSUM_VALIDATION="when_required")
        command = ["aws", "s3api", *args, "--bucket", self.bucket, "--endpoint-url", self.endpoint, "--output", "json"]
        return self.runner(command, capture_output=True, text=True, env=env)

    def head(self, key):
        """Returns {'etag': MD5 hex, 'size': bytes} or None when the key does not exist."""
        result = self._aws("head-object", "--key", key)
        if result.returncode == 0:
            data = json.loads(result.stdout)
            return {"etag": data["ETag"].strip('"'), "size": int(data["ContentLength"])}
        if "Not Found" in result.stderr or "(404)" in result.stderr:
            return None
        raise S3Error(result.stderr.strip())

    def put_file(self, key, path, content_type, cache_control, create_only):
        """Uploads a file in one request; with create_only an existing key is never overwritten and 'exists' is returned."""
        args = ["put-object", "--key", key, "--body", str(path), "--content-type", content_type, "--cache-control", cache_control]
        if create_only:
            args += ["--if-none-match", "*"]
        result = self._aws(*args)
        if result.returncode == 0:
            return "created"
        if create_only and ("PreconditionFailed" in result.stderr or "(412)" in result.stderr):
            return "exists"
        raise S3Error(result.stderr.strip())

    def list_keys(self, prefix):
        """Every key under the prefix (the CLI follows continuation tokens itself)."""
        result = self._aws("list-objects-v2", "--prefix", prefix)
        if result.returncode != 0:
            raise S3Error(result.stderr.strip())
        if not result.stdout.strip():
            return []
        return [item["Key"] for item in json.loads(result.stdout).get("Contents") or []]
