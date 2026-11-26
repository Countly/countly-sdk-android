"""Checks that every version source of the tag's artifacts carries the tag's version."""

import re
from pathlib import Path


def check_version_sources(repo_root, plan, config):
    """Returns problems; empty when every version source and, for final releases, every changelog heading matches."""
    problems = []
    root = Path(repo_root)
    for artifact in plan.artifacts:
        spec = config["artifacts"][artifact.key]
        for source in spec["versionSources"]:
            path = root / source["file"]
            if not path.is_file():
                problems.append(f"{source['file']} does not exist")
                continue
            match = re.search(source["regex"], path.read_text(encoding="utf-8"), re.MULTILINE)
            if match is None:
                problems.append(f"{source['file']}: no version found")
            elif match.group(1).strip() != plan.version:
                problems.append(f"{source['file']}: version is {match.group(1).strip()}, the tag says {plan.version}")
        changelog = spec.get("changelog")
        if changelog and not plan.prerelease:
            heading = changelog_heading(changelog, plan)
            text = (root / changelog["file"]).read_text(encoding="utf-8")
            if re.search(rf"^## {re.escape(heading)}\s*$", text, re.MULTILINE) is None:
                problems.append(f"{changelog['file']}: no '## {heading}' heading")
    return problems


def changelog_heading(changelog, plan):
    """The version the tag's changelog heading names: the whole tag or only its version, as config.json says."""
    return plan.tag if changelog["heading"] == "tag" else plan.version


def tag_changelog(plan, config):
    """The changelog entry of config.json that the tag's artifacts use; None when they have none."""
    for artifact in plan.artifacts:
        changelog = config["artifacts"][artifact.key].get("changelog")
        if changelog:
            return changelog
    return None


def changelog_section(repo_root, plan, config):
    """The entries under the tag's changelog heading, up to the next heading and without the blank lines around them; None when there are none."""
    changelog = tag_changelog(plan, config)
    if not changelog:
        return None
    text = (Path(repo_root) / changelog["file"]).read_text(encoding="utf-8").replace("\r\n", "\n")
    heading = re.escape(changelog_heading(changelog, plan))
    match = re.search(rf"^## {heading}[ \t]*$(.*?)(?=^## |\Z)", text, re.MULTILINE | re.DOTALL)
    section = match.group(1).strip() if match else ""
    return section or None


def clip_notes(notes, limit, tail):
    """The notes cut after the last whole line that fits in limit characters together with tail, which then ends them; notes that fit are returned unchanged."""
    if len(notes) <= limit:
        return notes
    kept = []
    size = len(tail)
    for line in notes.split("\n"):
        if size + len(line) + 1 > limit:
            break
        kept.append(line)
        size += len(line) + 1
    return "\n".join(kept).rstrip() + "\n" + tail
