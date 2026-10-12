"""Uploads a signed release create-only, in an order that keeps the repository consistent at every moment."""

import hashlib
from pathlib import Path

IMMUTABLE = "public, max-age=31536000, immutable"
CONTENT_TYPES = (
    ((".md5", ".sha1", ".sha256", ".sha512"), "text/plain; charset=utf-8"),
    ((".asc",), "application/pgp-signature"),
    ((".pom", ".xml"), "application/xml"),
    ((".jar", ".aar"), "application/java-archive"),
    ((".module", ".json"), "application/json"),
)


class UploadError(Exception):
    """A stored object differs from the file that should be uploaded; the version number is burned."""


def content_type(name):
    """Content type of a repository file, chosen by its extension."""
    for suffixes, kind in CONTENT_TYPES:
        if name.endswith(suffixes):
            return kind
    return "application/octet-stream"


def upload_order(names, pom_name):
    """Every file except the POM family first, then the POM's checksums and signature, the POM itself last."""
    family = sorted(name for name in names if name.startswith(pom_name) and name != pom_name)
    others = sorted(name for name in names if not name.startswith(pom_name))
    return others + family + ([pom_name] if pom_name in names else [])


def upload_folder(bucket, local_folder, remote_folder, pom_name, log=print):
    """Uploads one version folder create-only. An identical stored file is skipped; a signature stored by an earlier
    attempt is kept; any other difference raises UploadError."""
    names = sorted(path.name for path in Path(local_folder).iterdir() if path.is_file())
    for name in upload_order(names, pom_name):
        path = Path(local_folder) / name
        key = f"{remote_folder}/{name}"
        if bucket.put_file(key, path, content_type(name), IMMUTABLE, create_only=True) == "created":
            log(f"created {key}")
            continue
        stored = bucket.head(key)
        if stored is not None and stored["etag"] == hashlib.md5(path.read_bytes()).hexdigest():
            log(f"identical {key}")
        elif stored is not None and name.endswith(".asc"):
            # Re-signing gives other bytes (a signature carries its creation time); the verify job checks the stored one.
            log(f"kept the signature stored earlier {key}")
        else:
            raise UploadError(f"{key} already exists with different content")


def upload_release(bucket, staging_dir, plan, log=print):
    """Uploads every artifact version folder of the plan."""
    for artifact in plan.artifacts:
        folder = artifact.folder(plan.version)
        upload_folder(bucket, Path(staging_dir) / folder, folder, f"{artifact.base_name(plan.version)}.pom", log)
