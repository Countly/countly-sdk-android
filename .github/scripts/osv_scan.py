#!/usr/bin/env python3
"""Check resolved Gradle dependencies against the OSV vulnerability database.

Reads "COORD <projectPath> <group>:<artifact>:<version> <scope>" lines (produced by
dependency-report.init.gradle) on stdin and queries https://osv.dev.

Why this rather than a stock scanner: these projects have no Gradle lockfile, so
lockfile-based scanners see nothing. Scanning the *resolved* graph of every module is
what catches a dependency that only appears transitively, or one that was upgraded in
the SDK but left behind in a demo module.

Scopes, and which of them fail the run:

  published   ships to integrators           -> blocks
  sample      demo apps and test-only deps   -> blocks (ours to fix, just not shipped)
  buildscript Gradle plugin classpath        -> reported, never blocks

`buildscript` is exempt because it is largely the Android Gradle Plugin's own transitive
internals, which cannot be upgraded independently of AGP. Blocking on them would make the
job permanently red, and a permanently red check is one nobody reads.

  python3 .github/scripts/osv_scan.py < resolved-dependencies.txt

Options come from the environment so the workflow stays declarative:
  OSV_FAIL_ON         lowest severity that fails the run (default HIGH)
  OSV_ALLOWLIST       allowlist file (default .github/dependency-scan-allowlist.txt)
  OSV_BLOCKING_SCOPES comma-separated scopes that may fail the run
                      (default "published,sample")
  OSV_REPORT_FILE     markdown report for the PR comment (default osv-report.md)

OSV needs no API key, so this runs on forks and without repository secrets.
"""

import datetime
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

OSV_HOST_PREFIX = "https://api.osv.dev/"
OSV_BATCH_URL = OSV_HOST_PREFIX + "v1/querybatch"
OSV_VULN_URL = OSV_HOST_PREFIX + "v1/vulns/"
BATCH_SIZE = 100
SEVERITY_ORDER = ["UNKNOWN", "LOW", "MODERATE", "HIGH", "CRITICAL"]
SCOPE_PRECEDENCE = {"buildscript": 0, "sample": 1, "published": 2}
KNOWN_SCOPES = set(SCOPE_PRECEDENCE)

# Advisory ids arrive inside an OSV response, i.e. from outside this repo, and are
# interpolated into a URL. Accept only the documented shape.
VULN_ID_RE = re.compile(r"^[A-Za-z0-9._-]{1,100}$")


def rank(severity):
    try:
        return SEVERITY_ORDER.index(severity)
    except ValueError:
        return 0


def _check_url(url):
    """Reject any URL that is not a plain https OSV endpoint.

    urlopen would happily accept file:/ or a custom scheme, so the host and scheme are
    pinned here rather than trusted from the caller.
    """
    if not url.startswith(OSV_HOST_PREFIX):
        raise ValueError(f"refusing to fetch a non-OSV URL: {url}")
    return url


def post_json(url, payload, attempts=4):
    """POST with retries. A network failure must abort the scan, never quietly pass it."""
    body = json.dumps(payload).encode()
    request = urllib.request.Request(_check_url(url), body, {"Content-Type": "application/json"})
    return _send(request, url, attempts)


def get_json(url, attempts=4):
    return _send(urllib.request.Request(_check_url(url)), url, attempts)


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


def read_coordinates(stream):
    """Return {coordinate: {"scope": str, "modules": [str, ...]}}."""
    entries = {}
    for line in stream:
        parts = line.split()
        if len(parts) != 4 or parts[0] != "COORD":
            continue
        _, module, coordinate, scope = parts
        if coordinate.count(":") != 2:
            print(f"warning: skipping unparseable coordinate {coordinate!r}", file=sys.stderr)
            continue
        if scope not in KNOWN_SCOPES:
            raise SystemExit(f"error: unknown scope {scope!r} for {coordinate}")

        entry = entries.setdefault(coordinate, {"scope": scope, "modules": set()})
        entry["modules"].add(module)
        # The widest-reaching scope wins: an artifact reached both ways still ships.
        if SCOPE_PRECEDENCE[scope] > SCOPE_PRECEDENCE[entry["scope"]]:
            entry["scope"] = scope

    return {c: {"scope": e["scope"], "modules": sorted(e["modules"])}
            for c, e in sorted(entries.items())}


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
                    f"error: {path}:{number}: expected '<OSV id> <YYYY-MM-DD> <reason>', "
                    f"got {line!r}")
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
        queries.append({"version": version,
                        "package": {"name": f"{group}:{name}", "ecosystem": "Maven"}})

    results = []
    for start in range(0, len(queries), BATCH_SIZE):
        chunk = queries[start:start + BATCH_SIZE]
        batch = post_json(OSV_BATCH_URL, {"queries": chunk}).get("results", [])
        if len(batch) != len(chunk):
            raise SystemExit(
                f"error: OSV returned {len(batch)} results for {len(chunk)} queries; "
                "refusing to report a partial scan as clean")
        results.extend(batch)

    hits = {}
    for coordinate, result in zip(coordinates, results):
        ids = []
        for vuln in result.get("vulns", []):
            vuln_id = vuln.get("id", "")
            if VULN_ID_RE.match(vuln_id):
                ids.append(vuln_id)
            else:
                print(f"warning: ignoring malformed advisory id {vuln_id!r}", file=sys.stderr)
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
    fail_on = os.environ.get("OSV_FAIL_ON", "HIGH").upper()
    if fail_on not in SEVERITY_ORDER:
        raise SystemExit(f"error: OSV_FAIL_ON must be one of {', '.join(SEVERITY_ORDER)}")

    blocking_scopes = {s.strip() for s in
                       os.environ.get("OSV_BLOCKING_SCOPES", "published,sample").split(",")
                       if s.strip()}
    unknown = blocking_scopes - KNOWN_SCOPES
    if unknown:
        raise SystemExit(f"error: unknown scope(s) in OSV_BLOCKING_SCOPES: {', '.join(sorted(unknown))}")

    allowlist = read_allowlist(
        os.environ.get("OSV_ALLOWLIST", ".github/dependency-scan-allowlist.txt"))

    entries = read_coordinates(sys.stdin)
    if not entries:
        raise SystemExit("error: no dependency coordinates on stdin; the Gradle report step "
                         "produced nothing, so nothing was actually scanned")

    counts = {scope: sum(1 for e in entries.values() if e["scope"] == scope)
              for scope in sorted(KNOWN_SCOPES)}
    print(f"Scanning {len(entries)} resolved dependencies "
          f"({', '.join(f'{n} {s}' for s, n in counts.items())}), "
          f"failing on {fail_on}+ in {'/'.join(sorted(blocking_scopes))}")

    hits = query_osv(list(entries))

    cache = {}
    blocking, suppressed, informational = [], [], []
    for coordinate in sorted(hits):
        group, name, _ = coordinate.split(":")
        for vuln_id in hits[coordinate]:
            finding = describe(vuln_id, f"{group}:{name}", cache)
            finding["coordinate"] = coordinate
            finding["scope"] = entries[coordinate]["scope"]
            finding["modules"] = entries[coordinate]["modules"]
            entry = allowlist.get(vuln_id)

            can_block = finding["scope"] in blocking_scopes

            if entry and not entry["expired"]:
                finding["reason"] = entry["reason"]
                finding["expiry"] = entry["expiry"]
                suppressed.append(finding)
            elif can_block and rank(finding["severity"]) >= rank(fail_on):
                finding["expired_allowlist"] = bool(entry)
                blocking.append(finding)
            else:
                informational.append(finding)

    blocking.sort(key=lambda f: (-rank(f["severity"]), f["coordinate"]))
    report(entries, blocking, suppressed, informational, fail_on)
    return 1 if blocking else 0


def format_finding(finding):
    fixed = ", ".join(finding["fixed"]) if finding["fixed"] else "no fixed version published"
    return (f"[{finding['scope']}] {finding['coordinate']} — {finding['id']} "
            f"[{finding['severity']}]\n"
            f"    {finding['summary'] or '(no summary)'}\n"
            f"    fixed in: {fixed}\n"
            f"    used by: {', '.join(finding['modules'])}")


def report(entries, blocking, suppressed, informational, fail_on):
    lines = []

    if blocking:
        lines.append(f"BLOCKING — {len(blocking)} vulnerability(ies) at or above {fail_on}:\n")
        for finding in blocking:
            lines.append(format_finding(finding))
            if finding.get("expired_allowlist"):
                lines.append("    note: this advisory's allowlist entry has expired "
                             "and needs re-review")
        lines.append("")
    if suppressed:
        lines.append(f"ALLOWLISTED — {len(suppressed)}:\n")
        for finding in suppressed:
            lines.append(f"[{finding['scope']}] {finding['coordinate']} — {finding['id']} "
                         f"[{finding['severity']}] until {finding['expiry']}: {finding['reason']}")
        lines.append("")
    if informational:
        lines.append(f"INFORMATIONAL — {len(informational)} "
                     f"(non-blocking scope, or below the {fail_on} threshold):\n")
        for finding in informational:
            lines.append(format_finding(finding))
        lines.append("")
    if not (blocking or suppressed or informational):
        lines.append(f"No known vulnerabilities in {len(entries)} resolved dependencies.")
    elif not blocking:
        lines.append("No blocking findings.")

    text = "\n".join(lines).strip()
    print("\n" + text)

    markdown = ("## Dependency security scan\n\n"
                f"Scanned **{len(entries)}** resolved dependencies, failing on **{fail_on}** "
                f"and above.\n\n```\n{text}\n```\n")

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as handle:
            handle.write(markdown)

    # The job summary only shows on the workflow run page. This file is what the workflow
    # posts onto the pull request, where people actually look.
    with open(os.environ.get("OSV_REPORT_FILE", "osv-report.md"), "w", encoding="utf-8") as handle:
        handle.write(markdown)


if __name__ == "__main__":
    sys.exit(main())
