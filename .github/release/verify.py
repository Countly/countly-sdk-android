"""Checks a release from the public address, the way a customer's build sees it."""

import hashlib
import subprocess
import tempfile
import urllib.error
import urllib.request
from pathlib import Path

from layout import is_checksum


def fetch(url, timeout=60):
    """Downloads a URL; returns None for 404 and raises for any other error."""
    request = urllib.request.Request(url, headers={"User-Agent": "countly-sdk-release"})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise


def verify_public(manifest, plan, base_url, nocache, work_dir, fetcher=fetch):
    """Problems found when downloading the release: missing or changed files, missing signatures, wrong index. Downloaded files and signatures are kept in work_dir."""
    problems = []
    base = base_url.rstrip("/") + "/"
    for entry in manifest["files"]:
        body = fetcher(f"{base}{entry['path']}?nocache={nocache}")
        if body is None:
            problems.append(f"missing {entry['path']}")
            continue
        if hashlib.sha256(body).hexdigest() != entry["sha256"]:
            problems.append(f"changed {entry['path']}")
        if is_checksum(entry["path"]):
            continue
        signature = fetcher(f"{base}{entry['path']}.asc?nocache={nocache}")
        if signature is None:
            problems.append(f"missing {entry['path']}.asc")
            continue
        target = Path(work_dir) / entry["path"]
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(body)
        Path(f"{target}.asc").write_bytes(signature)
    for artifact in plan.artifacts:
        index = fetcher(f"{base}{artifact.group_path}/{artifact.artifact}/maven-metadata.xml?nocache={nocache}")
        listed = index is not None and f"<version>{plan.version}</version>".encode("utf-8") in index
        if plan.listed and not listed:
            problems.append(f"{artifact.coordinates} index does not list {plan.version}")
        if not plan.listed and listed:
            problems.append(f"{artifact.coordinates} index lists the release candidate {plan.version}")
    return problems


def verify_signatures(work_dir, public_key, runner=subprocess.run):
    """Checks every signature under work_dir against the given public key only; returns problems."""
    home = tempfile.mkdtemp()
    runner(["gpg", "--homedir", home, "--batch", "--import", str(public_key)], capture_output=True, check=True)
    problems = []
    for signature in sorted(Path(work_dir).rglob("*.asc")):
        result = runner(["gpg", "--homedir", home, "--batch", "--verify", str(signature), str(signature.with_suffix(""))], capture_output=True)
        if result.returncode != 0:
            problems.append(f"bad signature {signature.relative_to(work_dir).as_posix()}")
    return problems
