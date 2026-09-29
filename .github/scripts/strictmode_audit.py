#!/usr/bin/env python3
"""Checks StrictMode audit records against an allowlist and fails on anything else.

StrictModeAuditListener writes one strictmode-violations.tsv per device. Each violation is
attributed to its deepest frame that belongs to the SDK's main sources. Violations whose SDK frames
all belong to test code are ignored. A violation fails the check unless an allowlist entry matches
its policy, type and attributed method, and, when the entry names test classes, the test that
raised it.

Usage:
    strictmode_audit.py --sources sdk/src/main/java --allowlist .github/strictmode-allowlist.txt DIR...

Exit status: 0 when clean, 1 when a violation is outside the allowlist, 2 when no records exist.
"""
import argparse
import collections
import os
import re
import sys

RECORD_FILE = "strictmode-violations.tsv"
ANONYMOUS_CLASS = re.compile(r"\$\d+")


def main_classes(source_root):
    """Returns the fully qualified names of the classes declared under the main source root."""
    names = set()
    for directory, _, files in os.walk(source_root):
        for name in files:
            if name.endswith(".java") or name.endswith(".kt"):
                relative = os.path.relpath(os.path.join(directory, os.path.splitext(name)[0]), source_root)
                names.add(relative.replace(os.sep, "."))
    return names


def load_allowlist(path):
    """Parses '<policy> <violation> <Class.method> [tests=A,B]' lines, '#' starts a comment."""
    entries = []
    with open(path) as handle:
        for number, raw in enumerate(handle, 1):
            line = raw.split("#", 1)[0].strip()
            if not line:
                continue
            parts = line.split()
            if len(parts) < 3:
                sys.exit("%s:%d: expected '<policy> <violation> <Class.method> [tests=A,B]'" % (path, number))
            tests = set()
            for extra in parts[3:]:
                if not extra.startswith("tests="):
                    sys.exit("%s:%d: unexpected '%s'" % (path, number, extra))
                tests = set(extra[len("tests="):].split(","))
            entries.append({"policy": parts[0], "type": parts[1], "site": parts[2], "tests": tests, "line": number, "hits": 0})
    return entries


def record_files(directories):
    """Finds every record file below the given directories."""
    found = []
    for directory in directories:
        for root, _, files in os.walk(directory):
            if RECORD_FILE in files:
                found.append(os.path.join(root, RECORD_FILE))
    return sorted(found)


def attribute(frames, mains):
    """Returns ('Class.method', 'Class.method:line') for the deepest frame of an SDK main class."""
    for frame in frames.split(";"):
        qualified, _, line = frame.rpartition(":")
        class_name, _, method = qualified.rpartition(".")
        if class_name.split("$")[0] in mains:
            simple = ANONYMOUS_CLASS.sub("", class_name.rsplit(".", 1)[-1])
            return "%s.%s" % (simple, method), "%s.%s:%s" % (simple, method, line)
    return None, None


def matching_entry(entries, policy, violation, site, test_class):
    """The first allowlist entry covering the violation, or None."""
    for entry in entries:
        if entry["policy"] == policy and entry["type"] == violation and entry["site"] == site:
            if not entry["tests"] or test_class in entry["tests"]:
                return entry
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--sources", required=True, help="main source root, sdk/src/main/java")
    parser.add_argument("--allowlist", required=True)
    parser.add_argument("directories", nargs="+", help="directories holding strictmode-violations.tsv files")
    args = parser.parse_args()

    mains = main_classes(args.sources)
    entries = load_allowlist(args.allowlist)
    files = record_files(args.directories)
    if not files:
        print("No %s found: the audit did not run, check the test step." % RECORD_FILE)
        return 2

    failing = collections.OrderedDict()
    allowed = collections.Counter()
    notes = []
    for path in files:
        device = os.path.basename(os.path.dirname(path))
        started = finished = False
        with open(path, errors="replace") as handle:
            for raw in handle:
                line = raw.rstrip("\n")
                if line.startswith("# started"):
                    started = True
                    continue
                if line.startswith("# finished"):
                    finished = True
                    continue
                parts = line.split("\t")
                if len(parts) < 5:
                    continue
                policy = "VM" if parts[0] == "VM" else "THREAD"
                violation, test, frames = parts[1], parts[2], parts[4]
                site, where = attribute(frames, mains)
                if site is None:
                    continue
                test_class = test.split("#", 1)[0].rsplit(".", 1)[-1]
                entry = matching_entry(entries, policy, violation, site, test_class)
                if entry is not None:
                    entry["hits"] += 1
                    allowed[(policy, violation, site)] += 1
                    continue
                key = (policy, violation, site)
                group = failing.setdefault(key, {"count": 0, "where": where, "tests": [], "devices": set()})
                group["count"] += 1
                group["devices"].add(device)
                short_test = test.rsplit(".", 1)[-1]
                if short_test not in group["tests"] and len(group["tests"]) < 3:
                    group["tests"].append(short_test)
        if not started:
            notes.append("%s has no start marker, the listener did not run there." % device)
        elif not finished:
            notes.append("%s has no finish marker, the instrumentation process likely crashed and the audit is partial." % device)

    report = ["## StrictMode audit", ""]
    report.append("Records: %s" % ", ".join(os.path.basename(os.path.dirname(f)) for f in files))
    for note in notes:
        report.append("")
        report.append("> %s" % note)
    report.append("")
    if failing:
        report.append("**%d violation site(s) in SDK code are not allowlisted:**" % len(failing))
        report.append("")
        report.append("| Policy | Violation | Deepest SDK frame | Count | Tests |")
        report.append("|---|---|---|---|---|")
        for (policy, violation, _), group in failing.items():
            report.append("| %s | %s | `%s` | %d | %s |" % (policy, violation, group["where"], group["count"], ", ".join(group["tests"])))
        report.append("")
        report.append("Fix the SDK code, or add a justified entry to the allowlist when the site is inherent or test-only.")
    else:
        report.append("No violations in SDK code outside the allowlist.")
    if allowed:
        report.append("")
        report.append("<details><summary>Allowlisted sites (%d)</summary>" % len(allowed))
        report.append("")
        report.append("| Policy | Violation | Site | Count |")
        report.append("|---|---|---|---|")
        for (policy, violation, site), count in sorted(allowed.items()):
            report.append("| %s | %s | `%s` | %d |" % (policy, violation, site, count))
        report.append("")
        report.append("</details>")
    unused = [e for e in entries if e["hits"] == 0]
    if unused:
        report.append("")
        report.append("Allowlist entries not seen in this run, remove them once the site is gone: %s"
                      % ", ".join("line %d `%s`" % (e["line"], e["site"]) for e in unused))

    text = "\n".join(report) + "\n"
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as handle:
            handle.write(text)
    return 1 if failing else 0


if __name__ == "__main__":
    sys.exit(main())
