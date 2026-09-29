"""Expected files of a release in the staging folder, and checksum helpers."""

import hashlib
from pathlib import Path

CHECKSUM_ALGORITHMS = (("md5", "md5"), ("sha1", "sha1"), ("sha256", "sha256"), ("sha512", "sha512"))
CHECKSUM_SUFFIXES = tuple("." + suffix for suffix, _ in CHECKSUM_ALGORITHMS)


def primary_files(artifact, version):
    """Primary file names of one artifact version, before checksums and signatures."""
    base = artifact.base_name(version)
    if artifact.packaging == "marker":
        return [f"{base}.pom"]
    return [f"{base}.{artifact.packaging}", f"{base}.pom", f"{base}.module", f"{base}-sources.jar", f"{base}-javadoc.jar", f"{base}-cyclonedx.json"]


def expected_files(artifact, version):
    """Every file name expected in the version folder: the primary files and their four checksums."""
    return sorted(name + suffix for name in primary_files(artifact, version) for suffix in ("",) + CHECKSUM_SUFFIXES)


def is_checksum(name):
    """True for .md5, .sha1, .sha256 and .sha512 files."""
    return name.endswith(CHECKSUM_SUFFIXES)


def write_checksums(path):
    """Writes path.md5, .sha1, .sha256 and .sha512 as lowercase hex without a file name, like Maven Central."""
    data = Path(path).read_bytes()
    for suffix, algorithm in CHECKSUM_ALGORITHMS:
        Path(f"{path}.{suffix}").write_bytes(hashlib.new(algorithm, data).hexdigest().encode("ascii"))


def remove_index_files(staging_dir):
    """Deletes the maven-metadata.xml files Gradle writes; the publish job writes the real index."""
    for path in Path(staging_dir).rglob("maven-metadata.xml*"):
        path.unlink()


def check_staging(staging_dir, plan):
    """Problems with the staging folder: missing or unexpected files for the tag's artifacts, wrong checksums."""
    root = Path(staging_dir)
    problems = []
    expected_paths = set()
    for artifact in plan.artifacts:
        relative = artifact.folder(plan.version)
        folder = root / relative
        expected = expected_files(artifact, plan.version)
        expected_paths.update(f"{relative}/{name}" for name in expected)
        present = sorted(path.name for path in folder.iterdir()) if folder.is_dir() else []
        problems += [f"missing {relative}/{name}" for name in expected if name not in present]
        problems += [f"unexpected {relative}/{name}" for name in present if name not in expected]
        for name in primary_files(artifact, plan.version):
            path = folder / name
            if not path.is_file():
                continue
            data = path.read_bytes()
            for suffix, algorithm in CHECKSUM_ALGORITHMS:
                sidecar = folder / f"{name}.{suffix}"
                if sidecar.is_file() and sidecar.read_bytes().decode("ascii").strip() != hashlib.new(algorithm, data).hexdigest():
                    problems.append(f"wrong checksum {relative}/{name}.{suffix}")
    for path in root.rglob("*"):
        if path.is_file() and path.relative_to(root).as_posix() not in expected_paths:
            problems.append(f"unexpected {path.relative_to(root).as_posix()}")
    return sorted(set(problems))
