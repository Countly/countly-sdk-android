"""The list of staged files with sizes and hashes, handed from the build job to the publish and verify jobs."""

import hashlib
from pathlib import Path


def build_manifest(staging_dir, tag, commit):
    """Lists every staged file with its size and sha256."""
    root = Path(staging_dir)
    files = []
    for path in sorted(p for p in root.rglob("*") if p.is_file()):
        data = path.read_bytes()
        files.append({"path": path.relative_to(root).as_posix(), "size": len(data), "sha256": hashlib.sha256(data).hexdigest()})
    return {"tag": tag, "commit": commit, "files": files}


def verify_manifest(staging_dir, manifest):
    """Problems when the staging folder differs from the manifest: missing, unexpected or changed files."""
    root = Path(staging_dir)
    listed = {entry["path"]: entry for entry in manifest["files"]}
    present = {path.relative_to(root).as_posix() for path in root.rglob("*") if path.is_file()}
    problems = [f"missing {path}" for path in sorted(set(listed) - present)]
    problems += [f"unexpected {path}" for path in sorted(present - set(listed))]
    for path in sorted(set(listed) & present):
        data = (root / path).read_bytes()
        if len(data) != listed[path]["size"] or hashlib.sha256(data).hexdigest() != listed[path]["sha256"]:
            problems.append(f"changed {path}")
    return problems
