"""Summarizes SARIF static analysis results as Markdown and, given the base's results, fails on new findings."""
import argparse
import collections
import json
import pathlib
import re
import sys
import urllib.parse
import urllib.request

MAX_LISTED = 50
GATED_LEVELS = ("error", "warning")
Finding = collections.namedtuple("Finding", "rule level description path line column message")


def sarif_files(arguments):
    """Returns the SARIF files named by the arguments, expanding directories."""
    files = []
    for argument in arguments:
        path = pathlib.Path(argument)
        files.extend(sorted(path.rglob("*.sarif")) if path.is_dir() else [path] if path.is_file() else [])
    return files


def relative_path(uri, root):
    """Returns the path of a SARIF artifact URI, relative to root when it lies inside it."""
    parsed = urllib.parse.urlparse(uri)
    path = urllib.request.url2pathname(parsed.path) if parsed.scheme == "file" else urllib.parse.unquote(uri)
    try:
        return (root / path).resolve().relative_to(root.resolve()).as_posix()
    except (ValueError, OSError):
        return pathlib.PurePath(path).as_posix()


def artifact_uri(location, base_ids):
    """Returns the artifact URI of a SARIF location, resolved against its uriBaseId when the run defines it."""
    uri = location.get("uri", "")
    base = base_ids.get(location.get("uriBaseId"), {}).get("uri")
    return urllib.parse.urljoin(base, uri) if base else uri


def findings(files, root):
    """Yields every distinct finding in the SARIF files, with paths relative to root."""
    seen = set()
    for file in files:
        for run in json.loads(file.read_text(encoding="utf-8-sig")).get("runs", []):
            rules = {rule.get("id"): rule for rule in run.get("tool", {}).get("driver", {}).get("rules", [])}
            base_ids = run.get("originalUriBaseIds", {})
            for result in run.get("results", []):
                rule_id = result.get("ruleId") or result.get("rule", {}).get("id", "unknown")
                rule = rules.get(rule_id, {})
                physical = (result.get("locations") or [{}])[0].get("physicalLocation", {})
                finding = Finding(
                    rule_id,
                    result.get("level") or rule.get("defaultConfiguration", {}).get("level", "warning"),
                    rule.get("shortDescription", {}).get("text")
                    or rule.get("fullDescription", {}).get("text") or rule.get("helpUri", ""),
                    relative_path(artifact_uri(physical.get("artifactLocation", {}), base_ids), root),
                    physical.get("region", {}).get("startLine"),
                    physical.get("region", {}).get("startColumn"),
                    (result.get("message", {}).get("text") or "").split("\n")[0],
                )
                # The same finding in a shared header is reported once per source file that includes it.
                if finding not in seen:
                    seen.add(finding)
                    yield finding


def fingerprint(finding):
    """Returns what identifies a finding across commits: rule, file and message, with numbers masked."""
    return finding.rule, finding.path, re.sub(r"\d+", "#", finding.message)


def cell(text):
    """Escapes text for a Markdown table cell."""
    return str(text).replace("|", "\\|").replace("\n", " ")[:160]


def where(finding):
    """Returns 'path:line', or the path alone when the finding has no line."""
    return f"{finding.path}:{finding.line}" if finding.line else finding.path


def print_summary(title, rows):
    """Prints the findings per rule and level, then the first findings with their location."""
    print(f"### {title}: {len(rows)} finding{'s' if len(rows) != 1 else ''}\n")
    if not rows:
        return
    counts = collections.Counter((row.rule, row.level, row.description) for row in rows)
    print("| Rule | Level | Count | Description |\n|---|---|---|---|")
    for (rule, level, description), count in sorted(counts.items(), key=lambda item: (-item[1], item[0])):
        print(f"| {cell(rule)} | {level} | {count} | {cell(description)} |")
    print(f"\n<details><summary>First {min(MAX_LISTED, len(rows))} findings</summary>\n")
    print("| Location | Rule | Message |\n|---|---|---|")
    for row in sorted(rows, key=lambda row: (row.path, row.line or 0))[:MAX_LISTED]:
        print(f"| {cell(where(row))} | {cell(row.rule)} | {cell(row.message)} |")
    print("\n</details>\n")


def print_new(title, rows, base_rows):
    """Prints the findings that outnumber the base's findings with the same fingerprint; returns the gated count."""
    base_counts = collections.Counter(fingerprint(row) for row in base_rows)
    groups = collections.defaultdict(list)
    for row in rows:
        groups[fingerprint(row)].append(row)
    new = {key: group for key, group in groups.items() if len(group) > base_counts[key]}
    added = sum(len(group) - base_counts[key] for key, group in new.items())
    gated = sum(len(group) - base_counts[key] for key, group in new.items() if group[0].level in GATED_LEVELS)
    print(f"### New compared with the base branch: {added}\n")
    if not new:
        print("No finding that the base branch does not have.\n")
        return 0
    print("| Location | Rule | Level | Message | Base | Head |\n|---|---|---|---|---|---|")
    for key, group in sorted(new.items()):
        locations = ", ".join(where(row) for row in group[:5]) + (", ..." if len(group) > 5 else "")
        print(f"| {cell(locations)} | {cell(key[0])} | {group[0].level} | {cell(group[0].message)} "
              f"| {base_counts[key]} | {len(group)} |")
    print()
    if gated:
        print(f"::error title={title}::{gated} new finding{'s' if gated != 1 else ''} at "
              f"{' or '.join(GATED_LEVELS)} level: fix or suppress them, the job summary lists them.", file=sys.stderr)
    return gated


def load(title, paths, root):
    """Returns the findings of the SARIF files under paths, failing when there is no SARIF at all."""
    files = sarif_files(paths)
    if not files:
        # An analyzer that silently wrote nothing must not read as a clean result.
        sys.exit(f"::error title={title}::No SARIF file found in {' '.join(paths)}")
    return list(findings(files, root))


def main():
    """Prints the summary; with --base, also the new findings, and exits with 1 when any is gated."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("title", help="the analyzer's name, used as the heading")
    parser.add_argument("sarif", nargs="+", help="SARIF files or directories of the working directory's analysis")
    parser.add_argument("--base", nargs="+", help="SARIF files or directories of the pull request base's analysis")
    parser.add_argument("--base-root", default=".", help="the base checkout, which the base's paths are relative to")
    arguments = parser.parse_args()
    rows = load(arguments.title, arguments.sarif, pathlib.Path.cwd())
    print_summary(arguments.title, rows)
    if arguments.base and print_new(arguments.title, rows,
                                    load(arguments.title, arguments.base, pathlib.Path(arguments.base_root))):
        sys.exit(1)


if __name__ == "__main__":
    main()
