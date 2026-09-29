"""Signs the staged files with the key of the target: the release key for production, the dry-run key for tests."""

import subprocess
from pathlib import Path

from layout import is_checksum


class SigningError(Exception):
    """The keyring holds the wrong signing key for the target."""


def primary_secret_fingerprints(homedir, runner=subprocess.run):
    """Fingerprints of the primary secret keys in the keyring, without their subkeys."""
    listing = runner(["gpg", "--homedir", str(homedir), "--batch", "--with-colons", "--list-secret-keys"], capture_output=True, text=True, check=True).stdout
    lines = listing.splitlines()
    return [line.split(":")[9] for index, line in enumerate(lines) if line.startswith("fpr:") and index > 0 and lines[index - 1].startswith("sec:")]


def signing_fingerprint(homedir, release_fingerprint, production, runner=subprocess.run):
    """The key to sign with. Production requires the release key. A dry run requires exactly one other key and
    refuses the release key, so the release key never has to leave the production environment."""
    fingerprints = primary_secret_fingerprints(homedir, runner)
    if production:
        if release_fingerprint not in fingerprints:
            raise SigningError(f"the imported signing key is not {release_fingerprint}")
        return release_fingerprint
    if release_fingerprint in fingerprints:
        raise SigningError("a dry run must not use the release key; the maven-test environment needs its own dry-run key")
    if len(fingerprints) != 1:
        raise SigningError(f"a dry run needs exactly one signing key in the keyring, found {len(fingerprints)}")
    return fingerprints[0]


def export_public_key(homedir, fingerprint, path, runner=subprocess.run):
    """Writes the ASCII-armoured public key of the fingerprint to path."""
    armored = runner(["gpg", "--homedir", str(homedir), "--batch", "--armor", "--export", fingerprint], capture_output=True, text=True, check=True).stdout
    Path(path).write_bytes(armored.encode("ascii"))


def files_to_sign(staging_dir):
    """Every staged file except checksum files and existing signatures."""
    return sorted(path for path in Path(staging_dir).rglob("*") if path.is_file() and not is_checksum(path.name) and not path.name.endswith(".asc"))


def sign_staging(staging_dir, homedir, passphrase, fingerprint, runner=subprocess.run):
    """Writes one ASCII-armoured detached signature, made by exactly the given key, next to every file to sign; returns the signed files."""
    signed = files_to_sign(staging_dir)
    for path in signed:
        runner(
            ["gpg", "--homedir", str(homedir), "--batch", "--yes", "--pinentry-mode", "loopback", "--passphrase-fd", "0",
             "--local-user", f"{fingerprint}!", "--armor", "--detach-sign", "--output", f"{path}.asc", str(path)],
            input=passphrase, text=True, capture_output=True, check=True,
        )
    return signed
