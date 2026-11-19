"""Rewrites maven-metadata.xml of an artifact from the complete version folders in the bucket."""

import hashlib
import re
import tempfile
from pathlib import Path
from xml.sax.saxutils import escape

from layout import CHECKSUM_ALGORITHMS
from plan import FINAL_VERSION

METADATA_CACHE = "public, max-age=60, must-revalidate"


def complete_versions(keys, group_path, artifact_name):
    """Versions whose folder holds the POM; the POM is always uploaded last, so its presence means complete."""
    prefix = f"{group_path}/{artifact_name}/"
    versions = set()
    for key in keys:
        if key.startswith(prefix):
            parts = key[len(prefix):].split("/")
            if len(parts) == 2 and parts[1] == f"{artifact_name}-{parts[0]}.pom":
                versions.add(parts[0])
    return versions


def listed_versions(versions):
    """Versions that belong in the index, in ascending numeric order: final versions only, so release candidates and
    any folder the release workflow cannot produce stay unlisted."""
    final = [version for version in versions if re.fullmatch(FINAL_VERSION, version, re.ASCII)]
    return sorted(final, key=lambda version: tuple(int(part) for part in version.split(".")))


def render(group, artifact_name, versions, now):
    """maven-metadata.xml for ascending versions; latest and release are the highest version."""
    highest = escape(versions[-1])
    return "\n".join([
        '<?xml version="1.0" encoding="UTF-8"?>',
        "<metadata>",
        f"  <groupId>{escape(group)}</groupId>",
        f"  <artifactId>{escape(artifact_name)}</artifactId>",
        "  <versioning>",
        f"    <latest>{highest}</latest>",
        f"    <release>{highest}</release>",
        "    <versions>",
        *[f"      <version>{escape(version)}</version>" for version in versions],
        "    </versions>",
        f"    <lastUpdated>{now.strftime('%Y%m%d%H%M%S')}</lastUpdated>",
        "  </versioning>",
        "</metadata>",
        "",
    ])


def rewrite_index(bucket, group, artifact_name, now, log=print):
    """Rewrites the index from the bucket: the four checksum files first, the body last. Returns the listed versions."""
    group_path = group.replace(".", "/")
    versions = listed_versions(complete_versions(bucket.list_keys(f"{group_path}/{artifact_name}/"), group_path, artifact_name))
    if not versions:
        log(f"no listed version of {group}:{artifact_name}; index left unchanged")
        return []
    body = render(group, artifact_name, versions, now).encode("utf-8")
    key = f"{group_path}/{artifact_name}/maven-metadata.xml"
    with tempfile.TemporaryDirectory() as folder:
        for suffix, algorithm in CHECKSUM_ALGORITHMS:
            sidecar = Path(folder) / f"maven-metadata.xml.{suffix}"
            sidecar.write_bytes(hashlib.new(algorithm, body).hexdigest().encode("ascii"))
            bucket.put_file(f"{key}.{suffix}", sidecar, "text/plain; charset=utf-8", METADATA_CACHE, create_only=False)
        main = Path(folder) / "maven-metadata.xml"
        main.write_bytes(body)
        bucket.put_file(key, main, "application/xml", METADATA_CACHE, create_only=False)
    log(f"index {key}: {len(versions)} versions, latest {versions[-1]}")
    return versions
