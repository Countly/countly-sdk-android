#!/usr/bin/env python3
"""Check resolved Gradle dependencies against the OSV vulnerability database.

Reads the "group:name:version<TAB>scope" file produced by dependency-report.gradle and
queries https://osv.dev.

Only the `published` scope can fail the run — those are the dependencies every integrator
inherits from ly.count.android:sdk / sdk-native / sdk-plugin, so a vulnerability there is
ours to fix. The `build` scope (sample apps, test-only dependencies, and the Gradle plugin
classpath) is reported but never blocking: it never reaches an integrator, and most of it is
AGP's own internals, which cannot be upgraded independently of AGP.

  python3 .github/scripts/osv_scan.py build/dependency-coordinates.txt

Options come from the environment so the workflow stays declarative:
  OSV_FAIL_ON            lowest severity that fails the run (default HIGH)
  OSV_ALLOWLIST          path to the allowlist file (default .github/dependency-scan-allowlist.txt)
  OSV_FAIL_ON_BUILD_SCOPE  set to "true" to also fail on build-only dependencies

OSV needs no API key, so this runs on forks and without repository secrets.
"""

import datetime
import json
import os
import sys
import time
import urllib.error
import urllib.request

OSV_BATCH_URL = "https://api.osv.dev/v1/querybatch"
OSV_VULN_URL = "https://api.osv.dev/v1/vulns/"
BATCH_SIZE = 100
SEVERITY_ORDER = ["UNKNOWN", "LOW", "MODERATE", "HIGH", "CRITICAL"]
PUBLISHED = "published"


def rank(severity):
    try:
        return SEVERITY_ORDER.index(severity)
    except ValueError:
        return 0


def post_json(url, payload, attempts=4):
    """POST with retries. A network failure must abort the scan, never pass it."""
    body = json.dumps(payload).encode()
    request = urllib.request.Request(url, body, {"Content-Type": "application/json"})
    return _send(request, url, attempts)


def get_json(url, attempts=4):
    return _send(urllib.request.Request(url), url, attempts)


def _send(request, url, attempts):
    last_error = None
    for attempt in range(attempts):
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.load(response)
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
            last_error = error
            if attempt < attempts - 1:
                time.sleep(2 ** attempt)
    raise SystemExit(f"error: could not reach OSV at {url}: {last_error}")


def read_coordinates(path):
    """Return {coordinate: scope}."""
    scopes = {}
    with open(path) as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            coordinate, _, scope = line.partition("\t")
            coordinate, scope = coordinate.strip(), (scope.strip() or "build")
            if coordinate.count(":") != 2:
                print(f"warning: skipping unparseable coordinate {coordinate!r}", file=sys.stderr)
                continue
            if scopes.get(coordinate) != PUBLISHED:
                scopes[coordinate] = scope
    return scopes


def read_allowlist(path):
    """Parse "<OSV id> <YYYY-MM-DD expiry> <reason>" lines.

    An expiry is mandatory. An entry that has passed its date stops suppressing its
    finding, so a temporary exception cannot quietly become permanent.
    """
    allowed = {}
    if not os.path.exists(path):
        return allowed
    today = datetime.date.today()
    with open(path) as handle:
        for number, line in enumerate(handle, 1):
            line = line.split("#", 1)[0].strip()
            if not line:
                continue
            parts = line.split(None, 2)
            if len(parts) < 3:
                raise SystemExit(
                    f"error: {path}:{number}: expected '<OSV id> <YYYY-MM-DD> <reason>', got {line!r}"
                )
            vuln_id, expiry_text, reason = parts
            try:
                expiry = datetime.date.fromisoformat(expiry_text)
            except ValueError:
                raise SystemExit(f"error: {path}:{number}: {expiry_text!r} is not a YYYY-MM-DD date")
            allowed[vuln_id] = {"expiry": expiry, "reason": reason, "expired": expiry < today}
    return allowed


def query_osv(coordinates):
    """Return {coordinate: [vuln id, ...]} for coordinates OSV knows something about."""
    queries = []
    for coordinate in coordinates:
        group, name, version = coordinate.split(":")
        queries.append({"version": version, "package": {"name": f"{group}:{name}", "ecosystem": "Maven"}})

    results = []
    for start in range(0, len(queries), BATCH_SIZE):
        chunk = queries[start:start + BATCH_SIZE]
        response = post_json(OSV_BATCH_URL, {"queries": chunk})
        batch = response.get("results", [])
        if len(batch) != len(chunk):
            raise SystemExit(
                f"error: OSV returned {len(batch)} results for {len(chunk)} queries; refusing to "
                "report a partial scan as clean"
            )
        results.extend(batch)

    hits = {}
    for coordinate, result in zip(coordinates, results):
        ids = [vuln["id"] for vuln in result.get("vulns", [])]
        if ids:
            hits[coordinate] = ids
    return hits


def describe(vuln_id, package_name, cache):
    """Fetch severity, summary and fixed versions for one advisory."""
    if vuln_id not in cache:
        cache[vuln_id] = get_json(OSV_VULN_URL + vuln_id)
    vuln = cache[vuln_id]

    fixed = set()
    for affected in vuln.get("affected", []):
        if affected.get("package", {}).get("name") != package_name:
            continue
        for entry in affected.get("ranges", []):
            for event in entry.get("events", []):
                if "fixed" in event:
                    fixed.add(event["fixed"])

    return {
        "id": vuln_id,
        "severity": (vuln.get("database_specific", {}).get("severity") or "UNKNOWN").upper(),
        "summary": (vuln.get("summary") or "").strip(),
        "fixed": sorted(fixed),
    }


def main():
    if len(sys.argv) != 2:
        raise SystemExit(f"usage: {sys.argv[0]} <dependency-coordinates.txt>")

    fail_on = os.environ.get("OSV_FAIL_ON", "HIGH").upper()
    if fail_on not in SEVERITY_ORDER:
        raise SystemExit(f"error: OSV_FAIL_ON must be one of {', '.join(SEVERITY_ORDER)}")
    allowlist_path = os.environ.get("OSV_ALLOWLIST", ".github/dependency-scan-allowlist.txt")
    fail_on_build_scope = os.environ.get("OSV_FAIL_ON_BUILD_SCOPE", "").lower() == "true"

    scopes = read_coordinates(sys.argv[1])
    if not scopes:
        raise SystemExit(f"error: no dependency coordinates found in {sys.argv[1]}")
    allowlist = read_allowlist(allowlist_path)

    published_count = sum(1 for scope in scopes.values() if scope == PUBLISHED)
    print(f"Scanning {len(scopes)} resolved dependencies "
          f"({published_count} published, {len(scopes) - published_count} build-only) "
          f"against OSV, failing on {fail_on}+")

    hits = query_osv(sorted(scopes))

    cache = {}
    blocking, suppressed, informational = [], [], []
    for coordinate in sorted(hits):
        group, name, _ = coordinate.split(":")
        for vuln_id in hits[coordinate]:
            finding = describe(vuln_id, f"{group}:{name}", cache)
            finding["coordinate"] = coordinate
            finding["scope"] = scopes[coordinate]
            entry = allowlist.get(vuln_id)

            can_block = finding["scope"] == PUBLISHED or fail_on_build_scope

            if entry and not entry["expired"]:
                finding["reason"] = entry["reason"]
                finding["expiry"] = entry["expiry"]
                suppressed.append(finding)
            elif can_block and rank(finding["severity"]) >= rank(fail_on):
                finding["expired_allowlist"] = bool(entry)
                blocking.append(finding)
            else:
                informational.append(finding)

    report(scopes, blocking, suppressed, informational, fail_on)
    return 1 if blocking else 0


def format_finding(finding):
    fixed = ", ".join(finding["fixed"]) if finding["fixed"] else "no fixed version published"
    summary = finding["summary"] or "(no summary)"
    return (f"[{finding['scope']}] {finding['coordinate']} — {finding['id']} [{finding['severity']}]\n"
            f"    {summary}\n    fixed in: {fixed}")


def report(scopes, blocking, suppressed, informational, fail_on):
    lines = []

    if blocking:
        lines.append(f"\nBLOCKING — {len(blocking)} published dependency vulnerability(ies) "
                     f"at or above {fail_on}:\n")
        for finding in blocking:
            lines.append(format_finding(finding))
            if finding.get("expired_allowlist"):
                lines.append("    note: this advisory's allowlist entry has expired and needs re-review")
    if suppressed:
        lines.append(f"\nALLOWLISTED — {len(suppressed)}:\n")
        for finding in suppressed:
            lines.append(f"[{finding['scope']}] {finding['coordinate']} — {finding['id']} "
                         f"[{finding['severity']}] until {finding['expiry']}: {finding['reason']}")
    if informational:
        lines.append(f"\nINFORMATIONAL — {len(informational)} "
                     f"(build-only, or below the {fail_on} threshold):\n")
        for finding in informational:
            lines.append(format_finding(finding))
    if not (blocking or suppressed or informational):
        lines.append(f"\nNo known vulnerabilities in {len(scopes)} resolved dependencies.")
    if not blocking:
        lines.append("\nNo blocking findings in published dependencies.")

    text = "\n".join(lines)
    print(text)

    headline = (f"Scanned **{len(scopes)}** resolved dependencies, failing on "
                f"**{fail_on}** and above in published dependencies.")
    markdown = ("## Dependency security scan\n\n" + headline + "\n\n```\n"
                + text.strip() + "\n```\n")

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as handle:
            handle.write(markdown)

    # The workflow posts this onto the pull request. Without it a passing scan is invisible:
    # a green check says nothing about what was actually scanned or what was waived.
    report_path = os.environ.get("OSV_REPORT_FILE")
    if report_path:
        with open(report_path, "w") as handle:
            handle.write(markdown)


if __name__ == "__main__":
    sys.exit(main())
