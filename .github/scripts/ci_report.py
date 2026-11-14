"""Rebuilds the one CI report comment of the pull request whose head commit a finished workflow run tested."""

import io
import json
import os
import re
import subprocess
import sys
import zipfile
from datetime import datetime, timezone

MARKER = "<!-- countly-ci-report -->"
FRAGMENT_PREFIX = "ci-report-"
FRAGMENT_FILE = "report.md"
MAX_FRAGMENT_BYTES = 65536
MAX_SUMMARY_CHARS = 200
MAX_DETAILS_CHARS = 12000
MAX_BODY_CHARS = 60000
MAX_CELL_SUMMARIES = 3
# a summary that reports a failure, which opens its section when the run failed
FAILING = re.compile(
    r"\b([1-9][0-9]* (failed|new findings?|of [0-9]+ scenarios differ)|has a blocking vulnerability)\b")
ICONS = {
    "success": "✅ passed",
    "failure": "❌ failed",
    "cancelled": "⚪ cancelled",
    "timed_out": "⌛ timed out",
    "skipped": "⏭️ skipped",
    "neutral": "➖ neutral",
    "action_required": "⚠️ action required",
    "stale": "⚪ stale",
    "startup_failure": "❌ did not start",
}
ORDER = {"failure": 0, "startup_failure": 0, "timed_out": 0, "action_required": 1, "running": 2, "cancelled": 3}


def gh(*args, raw=False):
    """Runs gh with the given arguments and returns its output, parsed as JSON unless raw."""
    out = subprocess.run(["gh", *args], check=True, capture_output=True).stdout
    return out if raw else json.loads(out or b"null")


def paged(path):
    """Returns every page of a list endpoint, merged, for endpoints that answer with a list or with one keyed list."""
    pages = gh("api", "--paginate", "--slurp", path)
    items = []
    for page in pages:
        if isinstance(page, list):
            items.extend(page)
        else:
            items.extend(next(value for value in page.values() if isinstance(value, list)))
    return items


def pull_requests(repo, sha):
    """Returns the numbers of the open pull requests whose head is the given commit."""
    return [pr["number"] for pr in paged(f"repos/{repo}/pulls?state=open&per_page=100") if pr["head"]["sha"] == sha]


def latest_runs(repo, sha, own_name):
    """Returns the newest pull request run of each workflow for the commit, leaving out this report's own workflow."""
    runs = {}
    for run in paged(f"repos/{repo}/actions/runs?head_sha={sha}&event=pull_request&per_page=100"):
        if run["name"] == own_name:
            continue
        kept = runs.get(run["name"])
        if kept is None or (run["created_at"], run["id"]) > (kept["created_at"], kept["id"]):
            runs[run["name"]] = run
    return list(runs.values())


def defused(text):
    """Returns untrusted Markdown that cannot mention anyone, close the report's sections or fake its marker."""
    text = text.replace(MARKER, "")
    text = re.sub(r"@(?=[A-Za-z0-9])", "@&#8203;", text)
    # a fragment's own collapsible sections become bold headings, since they would close the report's section
    text = re.sub(r"<details[^>]*>\s*<summary[^>]*>(.*?)</summary>", r"**\1**", text, flags=re.IGNORECASE | re.DOTALL)
    text = re.sub(r"</details\s*>", "", text, flags=re.IGNORECASE)
    return re.sub(r"<(/?)(details|summary)", r"&lt;\1\2", text, flags=re.IGNORECASE)


def fragments(repo, run):
    """Returns the report fragments the run uploaded, each as a dict with key, title, summary and details."""
    found = []
    for artifact in paged(f"repos/{repo}/actions/runs/{run['id']}/artifacts?per_page=100"):
        if not artifact["name"].startswith(FRAGMENT_PREFIX) or artifact.get("expired"):
            continue
        zipped = gh("api", f"repos/{repo}/actions/artifacts/{artifact['id']}/zip", raw=True)
        archive = zipfile.ZipFile(io.BytesIO(zipped))
        if FRAGMENT_FILE not in archive.namelist():
            continue
        key = artifact["name"][len(FRAGMENT_PREFIX):]
        data = archive.read(FRAGMENT_FILE)[:MAX_FRAGMENT_BYTES].decode("utf-8", "replace")
        lines = defused(data).replace("\r\n", "\n").split("\n")
        title = lines[0].strip() or key
        summary = (lines[1].strip() if len(lines) > 1 else "")[:MAX_SUMMARY_CHARS]
        details = "\n".join(lines[2:]).strip()
        if len(details) > MAX_DETAILS_CHARS:
            details = details[:MAX_DETAILS_CHARS] + "\n\n…cut here; the run's job summary has the rest."
        found.append({"key": key, "title": title, "summary": summary, "details": details})
    return sorted(found, key=lambda fragment: fragment["key"])


def failing_jobs(repo, run):
    """Returns the names of the run's jobs that did not succeed or get skipped."""
    jobs = paged(f"repos/{repo}/actions/runs/{run['id']}/jobs?per_page=100")
    return [job["name"] for job in jobs if job.get("conclusion") not in (None, "success", "skipped", "neutral")]


def state(run):
    """Returns the run's conclusion, or "running" while it has none."""
    return run["conclusion"] if run["status"] == "completed" and run["conclusion"] else "running"


def label(run, fragment):
    """Returns the fragment's title, or nothing when the title only repeats its workflow's name."""
    return "" if fragment["title"].casefold() == run["name"].casefold() else fragment["title"]


def joined(*parts):
    """Returns the non-empty parts joined by a colon."""
    return ": ".join(part for part in parts if part)


def body(repo, sha, runs):
    """Returns the comment: a table with one row per workflow, then a collapsible section per fragment."""
    rows, sections = [], []
    for run in sorted(runs, key=lambda run: (ORDER.get(state(run), 4), run["name"].lower())):
        result = state(run)
        found = fragments(repo, run) if result != "running" else []
        parts = [joined(label(run, fragment), fragment["summary"]) for fragment in found if fragment["summary"]]
        if len(parts) > MAX_CELL_SUMMARIES:
            parts = parts[:MAX_CELL_SUMMARIES] + [f"{len(parts) - MAX_CELL_SUMMARIES} more below"]
        failed = failing_jobs(repo, run) if result in ("failure", "timed_out") else []
        if failed:
            parts.insert(0, "Failed: " + ", ".join(failed))
        summary = "; ".join(parts)
        icon = "⏳ running" if result == "running" else ICONS.get(result, result)
        cell = summary.replace("|", "\\|")
        rows.append(f"| [{run['name']}]({run['html_url']}) | {icon} | {cell} |")
        for fragment in found:
            opened = " open" if result in ("failure", "timed_out") and FAILING.search(fragment["summary"]) else ""
            heading = joined(run["name"], label(run, fragment))
            if fragment["summary"]:
                heading += f", {fragment['summary']}"
            details = fragment["details"] or "No details."
            sections.append(f"<details{opened}>\n<summary><b>{heading}</b></summary>\n\n{details}\n\n</details>\n")
    when = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    head = [MARKER, "### CI report", "", "| Check | Result | Summary |", "| --- | --- | --- |", *rows, ""]
    tail = [f"<sub>For {sha[:7]}, updated {when}. Each check's run page has its full log.</sub>"]
    text = "\n".join(head + sections + tail)
    while len(text) > MAX_BODY_CHARS and sections:
        sections.pop()
        text = "\n".join(head + sections + ["…more sections did not fit in one comment.", ""] + tail)
    return text


def publish(repo, number, text):
    """Updates the pull request's report comment, or posts it when there is none yet."""
    comments = paged(f"repos/{repo}/issues/{number}/comments?per_page=100")
    mine = [comment for comment in comments
            if comment["user"]["login"] == "github-actions[bot]" and comment["body"].startswith(MARKER)]
    if mine:
        gh("api", "--method", "PATCH", f"repos/{repo}/issues/comments/{mine[-1]['id']}", "-f", f"body={text}")
    else:
        gh("api", "--method", "POST", f"repos/{repo}/issues/{number}/comments", "-f", f"body={text}")


def main():
    """Rebuilds the report for the commit in HEAD_SHA, or prints it when DRY_RUN is set."""
    repo, sha = os.environ["REPO"], os.environ["HEAD_SHA"]
    numbers = [int(os.environ["PR"])] if os.environ.get("PR") else pull_requests(repo, sha)
    if not numbers:
        print(f"No open pull request has {sha} as its head, so there is nothing to report on.")
        return 0
    text = body(repo, sha, latest_runs(repo, sha, os.environ.get("OWN_WORKFLOW", "CI report")))
    if os.environ.get("DRY_RUN"):
        print(text)
        return 0
    for number in numbers:
        publish(repo, number, text)
        print(f"Updated the CI report of #{number}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
