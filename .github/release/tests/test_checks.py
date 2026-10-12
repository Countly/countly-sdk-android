import subprocess
import unittest

from checks import branch_problems, missing_checks

COMMIT = "c" * 40


class ChecksTest(unittest.TestCase):
    def test_reports_missing_and_failed_checks(self):
        runs = {"check_runs": [{"name": "Build and Test the SDK", "conclusion": "success"}, {"name": "Analyze (java)", "conclusion": "failure"}]}
        required = ["Build and Test the SDK", "Analyze (java)", "OSV scan (all modules)"]
        self.assertEqual(missing_checks(runs, required), ["Analyze (java)", "OSV scan (all modules)"])

    def test_tag_commit_must_be_on_the_release_branch(self):
        calls = []

        def on_branch(args, **kwargs):
            calls.append(args)
            return subprocess.CompletedProcess(args, 0)

        def elsewhere(args, **kwargs):
            return subprocess.CompletedProcess(args, 1)

        self.assertEqual(branch_problems(COMMIT, "staging", "/repo", runner=on_branch), [])
        self.assertEqual(calls[0], ["git", "-C", "/repo", "merge-base", "--is-ancestor", COMMIT, "origin/staging"])
        self.assertEqual(branch_problems(COMMIT, "staging-nw", "/repo", runner=elsewhere), [f"{COMMIT} is not on origin/staging-nw; the tag must point to that branch"])


if __name__ == "__main__":
    unittest.main()
