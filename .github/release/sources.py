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
            heading = plan.tag if changelog["heading"] == "tag" else plan.version
            text = (root / changelog["file"]).read_text(encoding="utf-8")
            if re.search(rf"^## {re.escape(heading)}\s*$", text, re.MULTILINE) is None:
                problems.append(f"{changelog['file']}: no '## {heading}' heading")
    return problems
