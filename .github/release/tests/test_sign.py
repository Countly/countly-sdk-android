import subprocess
import tempfile
import unittest
from pathlib import Path

from sign import SigningError, export_public_key, sign_staging, signing_fingerprint

RELEASE = "FC8DB0DE234A273BA45E562FB8C83A079A5BBD0C"
DRY_RUN = "1111111111111111111111111111111111111111"
OTHER = "2222222222222222222222222222222222222222"
SUBKEY = "9999999999999999999999999999999999999999"


def keyring(*fingerprints):
    """A fake gpg whose secret keyring holds one primary key, with an encryption subkey, per fingerprint."""
    listing = "".join(
        f"sec:u:4096:1:{fp[-16:]}:1:::u:::scESC:\nfpr:::::::::{fp}:\ngrp:::::::::{'A' * 40}:\n"
        f"ssb:u:4096:1:{SUBKEY[-16:]}:1::::::e:\nfpr:::::::::{SUBKEY}:\n"
        for fp in fingerprints
    )

    def fake_gpg(args, **kwargs):
        return subprocess.CompletedProcess(args, 0, listing, "")
    return fake_gpg


class SignTest(unittest.TestCase):
    def test_signs_everything_but_checksums_with_the_chosen_key(self):
        staging = Path(tempfile.mkdtemp())
        (staging / "a").mkdir()
        for name in ["x.pom", "x.pom.sha1", "x.aar", "x.aar.md5"]:
            (staging / "a" / name).write_bytes(b"x")
        calls = []

        def fake_gpg(args, **kwargs):
            calls.append((args, kwargs.get("input")))
            Path(args[args.index("--output") + 1]).write_bytes(b"signature")
            return subprocess.CompletedProcess(args, 0, "", "")

        signed = sign_staging(staging, "/tmp/gnupg", "secret", RELEASE, runner=fake_gpg)
        self.assertEqual([path.name for path in signed], ["x.aar", "x.pom"])
        self.assertTrue((staging / "a/x.pom.asc").is_file())
        self.assertFalse((staging / "a/x.pom.sha1.asc").exists())
        self.assertTrue(all(passphrase == "secret" and "--passphrase-fd" in args for args, passphrase in calls))
        self.assertTrue(all(args[args.index("--local-user") + 1] == RELEASE + "!" for args, _ in calls))

    def test_production_needs_the_release_key(self):
        self.assertEqual(signing_fingerprint("/tmp/gnupg", RELEASE, production=True, runner=keyring(RELEASE)), RELEASE)
        with self.assertRaises(SigningError):
            signing_fingerprint("/tmp/gnupg", RELEASE, production=True, runner=keyring(OTHER))

    def test_dry_run_refuses_the_release_key_and_needs_exactly_one_key(self):
        self.assertEqual(signing_fingerprint("/tmp/gnupg", RELEASE, production=False, runner=keyring(DRY_RUN)), DRY_RUN)
        for fingerprints in [(RELEASE,), (DRY_RUN, OTHER), ()]:
            with self.subTest(fingerprints=fingerprints):
                with self.assertRaises(SigningError):
                    signing_fingerprint("/tmp/gnupg", RELEASE, production=False, runner=keyring(*fingerprints))

    def test_exports_the_public_key(self):
        target = Path(tempfile.mkdtemp()) / "key.asc"
        calls = []

        def fake_gpg(args, **kwargs):
            calls.append(args)
            return subprocess.CompletedProcess(args, 0, "-----BEGIN PGP PUBLIC KEY BLOCK-----\nabc\n", "")

        export_public_key("/tmp/gnupg", DRY_RUN, target, runner=fake_gpg)
        self.assertTrue(target.read_bytes().startswith(b"-----BEGIN PGP PUBLIC KEY BLOCK-----"))
        self.assertIn("--export", calls[0])
        self.assertEqual(calls[0][-1], DRY_RUN)


if __name__ == "__main__":
    unittest.main()
