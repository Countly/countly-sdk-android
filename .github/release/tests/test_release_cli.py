import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from plan import plan_for_tag
from tests.support import android_config

RELEASE = Path(__file__).resolve().parents[1] / "release.py"


def run_cli(*args, output=""):
    """Runs release.py like the workflow does; GitHub's step output file is written only when `output` names one."""
    env = dict(os.environ, GITHUB_OUTPUT=output, GITHUB_STEP_SUMMARY="")
    return subprocess.run([sys.executable, str(RELEASE), *args], capture_output=True, text=True, env=env)


class ReleaseCliTest(unittest.TestCase):
    def setUp(self):
        self.folder = Path(tempfile.mkdtemp())

    def test_plan_writes_plan_json(self):
        out = self.folder / "plan.json"
        result = run_cli("plan", "--tag", "native-26.3.0", "--target", "test", "--out", str(out))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(json.loads(out.read_text(encoding="utf-8"))["artifacts"], ["sdk-native"])

    def test_plan_refuses_an_unknown_tag(self):
        result = run_cli("plan", "--tag", "v1", "--target", "test", "--out", str(self.folder / "plan.json"))
        self.assertEqual(result.returncode, 1)
        self.assertIn("::error::", result.stdout)

    def test_checkout_tags_are_a_json_list_the_matrix_can_read(self):
        output = self.folder / "output"
        output.write_bytes(b"")
        result = run_cli("checkout-tags", "--branch", "staging", output=str(output))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        line = output.read_text(encoding="utf-8").strip()
        self.assertTrue(line.startswith("tags="), line)
        tags = json.loads(line[len("tags="):])
        self.assertEqual([plan_for_tag(android_config(), tag).artifacts[0].key for tag in tags], ["sdk", "sdk-native", "sdk-plugin"])

    def test_a_branch_without_releases_gets_the_empty_list_the_workflow_skips_on(self):
        output = self.folder / "output"
        output.write_bytes(b"")
        result = run_cli("checkout-tags", "--branch", "master", output=str(output))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(output.read_text(encoding="utf-8").strip(), "tags=[]")

    def test_prerelease_flag_must_match(self):
        cases = [("26.2.0", "true", 1), ("26.2.0-rc1", "false", 1), ("26.2.0", "unknown", 1), ("26.2.0-rc1", "true", 0), ("26.2.0", "false", 0)]
        for tag, prerelease, code in cases:
            with self.subTest(tag=tag, prerelease=prerelease):
                result = run_cli("plan", "--tag", tag, "--target", "production", "--prerelease", prerelease, "--out", str(self.folder / "plan.json"))
                self.assertEqual(result.returncode, code, result.stdout)


if __name__ == "__main__":
    unittest.main()
