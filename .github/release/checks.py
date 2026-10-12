"""Checks on the tag commit: its release branch and the required status checks."""

import subprocess


def branch_problems(commit, branch, repo_root, runner=subprocess.run):
    """Problems unless the commit is an ancestor of origin/<branch>."""
    result = runner(["git", "-C", str(repo_root), "merge-base", "--is-ancestor", commit, f"origin/{branch}"], capture_output=True)
    return [] if result.returncode == 0 else [f"{commit} is not on origin/{branch}; the tag must point to that branch"]


def missing_checks(check_runs, required):
    """Required check names without a successful run on the commit, from the GitHub check-runs API response."""
    succeeded = {run["name"] for run in check_runs.get("check_runs", []) if run.get("conclusion") == "success"}
    return [name for name in required if name not in succeeded]
