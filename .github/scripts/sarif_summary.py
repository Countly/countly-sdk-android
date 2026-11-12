"""
Writes a Markdown summary of static analysis results stored as SARIF.

Usage: python3 sarif_summary.py <title> <file.sarif or directory> [...]

Prints the number of distinct findings per rule and level, then the first findings with their location. Exits with 1
when no SARIF file is found, so an analyzer that silently wrote nothing never reads as a clean result.
"""
import collections
import json
import pathlib
import sys
import urllib.parse
import urllib.request

MAX_LISTED = 50


def sarif_files(arguments):
    """Returns the SARIF files named by the arguments, expanding directories."""
    files = []
    for argument in arguments:
        path = pathlib.Path(argument)
        files.extend(sorted(path.rglob("*.sarif")) if path.is_dir() else [path] if path.is_file() else [])
    return files


def relative_path(uri):
    """Returns the path of a SARIF artifact URI, relative to the working directory when it lies inside it."""
    parsed = urllib.parse.urlparse(uri)
    path = urllib.request.url2pathname(parsed.path) if parsed.scheme == "file" else urllib.parse.unquote(uri)
    try:
        return pathlib.Path(path).resolve().relative_to(pathlib.Path.cwd().resolve()).as_posix()
    except (ValueError, OSError):
        return pathlib.PurePath(path).as_posix()


def location(result):
    """Returns 'path:line' for the first location of a result."""
    for loc in result.get("locations", []):
        physical = loc.get("physicalLocation", {})
        path = relative_path(physical.get("artifactLocation", {}).get("uri", ""))
        line = physical.get("region", {}).get("startLine")
        return f"{path}:{line}" if line else path
    return ""


def findings(files):
    """Yields (rule, level, description, location, message) for every result in the SARIF files."""
    for file in files:
        for run in json.loads(file.read_text(encoding="utf-8-sig")).get("runs", []):
            driver = run.get("tool", {}).get("driver", {})
            rules = {rule.get("id"): rule for rule in driver.get("rules", [])}
            for result in run.get("results", []):
                rule_id = result.get("ruleId") or result.get("rule", {}).get("id", "unknown")
                rule = rules.get(rule_id, {})
                level = result.get("level") or rule.get("defaultConfiguration", {}).get("level", "warning")
                description = (rule.get("shortDescription", {}).get("text")
                               or rule.get("fullDescription", {}).get("text") or rule.get("helpUri", ""))
                message = result.get("message", {}).get("text", "").splitlines()[0] if result.get("message") else ""
                yield rule_id, level, description, location(result), message


def cell(text):
    """Escapes text for a Markdown table cell."""
    return text.replace("|", "\\|").replace("\n", " ")[:160]


def main():
    """Prints the summary for the title and SARIF paths given on the command line."""
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    title, files = sys.argv[1], sarif_files(sys.argv[2:])
    if not files:
        sys.exit(f"::error title={title}::No SARIF file found in {' '.join(sys.argv[2:])}")
    # The same finding in a shared header is reported once per source file that includes it.
    rows = list(dict.fromkeys(findings(files)))
    print(f"### {title}: {len(rows)} finding{'s' if len(rows) != 1 else ''}\n")
    if not rows:
        return
    counts = collections.Counter((rule, level, description) for rule, level, description, _, _ in rows)
    print("| Rule | Level | Count | Description |\n|---|---|---|---|")
    for (rule, level, description), count in sorted(counts.items(), key=lambda item: (-item[1], item[0])):
        print(f"| {cell(rule)} | {level} | {count} | {cell(description)} |")
    print(f"\n<details><summary>First {min(MAX_LISTED, len(rows))} findings</summary>\n")
    print("| Location | Rule | Message |\n|---|---|---|")
    for rule, _, _, where, message in sorted(rows, key=lambda row: row[3])[:MAX_LISTED]:
        print(f"| {cell(where)} | {cell(rule)} | {cell(message)} |")
    print("\n</details>")


if __name__ == "__main__":
    main()
