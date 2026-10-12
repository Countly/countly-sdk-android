"""Turns the CycloneDX plugin's per-project SBOM into the published component list of one artifact."""

import hashlib
import json
import subprocess
from pathlib import Path
from urllib.parse import quote

from layout import write_checksums


def purl(artifact, version, public_base_url):
    """Package URL of a published Countly artifact, naming the Countly repository."""
    kind = "aar" if artifact.packaging == "aar" else "jar"
    repository = quote(public_base_url.rstrip("/"), safe="")
    return f"pkg:maven/{artifact.group}/{artifact.artifact}@{version}?repository_url={repository}&type={kind}"


def normalize(bom, artifact, version, timestamp, public_base_url):
    """Sets the published coordinates, the licence and a fixed timestamp, and drops the random serial number."""
    metadata = bom.setdefault("metadata", {})
    old_ref = metadata.get("component", {}).get("bom-ref")
    ref = purl(artifact, version, public_base_url)
    metadata["component"] = {"type": "library", "bom-ref": ref, "group": artifact.group, "name": artifact.artifact, "version": version, "purl": ref, "licenses": [{"license": {"id": "MIT"}}]}
    metadata["timestamp"] = timestamp
    bom.pop("serialNumber", None)
    for dependency in bom.get("dependencies", []):
        if old_ref is not None and dependency.get("ref") == old_ref:
            dependency["ref"] = ref
    return bom


def merge_fragment(bom, fragment):
    """Adds the hand-kept components (the native crash libraries of sdk-native) and their build tools."""
    root_ref = bom["metadata"]["component"]["bom-ref"]
    added = [component["bom-ref"] for component in fragment.get("components", [])]
    bom.setdefault("components", []).extend(fragment.get("components", []))
    dependencies = bom.setdefault("dependencies", [])
    root = next((entry for entry in dependencies if entry.get("ref") == root_ref), None)
    if root is None:
        dependencies.append({"ref": root_ref, "dependsOn": added})
    else:
        root.setdefault("dependsOn", []).extend(added)
    tools = bom["metadata"].setdefault("tools", {"components": []})
    if isinstance(tools, dict):
        tools.setdefault("components", []).extend(fragment.get("tools", []))
    else:
        tools.extend(fragment.get("tools", []))
    return bom


def git_runner(repo_root):
    """Returns a function that runs git in the repository and returns its stdout as bytes."""
    def run(*args):
        return subprocess.run(["git", "-C", str(repo_root), *args], capture_output=True, check=True).stdout
    return run


def check_fragment(fragment, git):
    """Problems when the fragment no longer matches the committed submodule commit or vendored file (git blobs, so line endings never matter)."""
    problems = []
    for check in fragment.get("checks", []):
        if check["kind"] == "gitlink":
            fields = git("ls-tree", "HEAD", check["path"]).decode("utf-8").split()
            actual = fields[2] if len(fields) >= 3 else ""
        elif check["kind"] == "blob-sha256":
            actual = hashlib.sha256(git("show", f"HEAD:{check['path']}")).hexdigest()
        else:
            problems.append(f"unknown SBOM fragment check {check['kind']}")
            continue
        if actual != check["expect"]:
            problems.append(f"{check['path']} is {actual or 'missing'}, the SBOM fragment records {check['expect']}; update the fragment")
    return problems


def place_sbom(bom_path, staging_dir, artifact, version, timestamp, public_base_url, fragment=None):
    """Writes the published SBOM and its checksums into the staging folder and returns its path."""
    bom = normalize(json.loads(Path(bom_path).read_text(encoding="utf-8")), artifact, version, timestamp, public_base_url)
    if fragment is not None:
        merge_fragment(bom, fragment)
    target = Path(staging_dir) / artifact.folder(version) / f"{artifact.base_name(version)}-cyclonedx.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes((json.dumps(bom, indent=2) + "\n").encode("utf-8"))
    write_checksums(target)
    return target
