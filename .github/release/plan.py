"""Maps a release tag to what it publishes, using the repository's .github/release/config.json."""

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Optional, Tuple

FINAL_VERSION = r"\d+\.\d+\.\d+"
RELEASE_VERSION = FINAL_VERSION + r"(?:-rc\d+)?"
RC_PATTERN = re.compile(r"-rc\d+$")
CONFIG_PATH = Path(__file__).resolve().parent / "config.json"


class PlanError(Exception):
    """A tag or repository state that must stop the release."""


@dataclass(frozen=True)
class Artifact:
    """One Maven artifact of the repository and the Gradle module that builds it."""

    key: str
    group: str
    artifact: str
    packaging: str
    module: str
    sbom: bool
    contract: Optional[str]
    sbom_fragment: Optional[str]
    native_libraries: Optional[str]
    consumer_probe: Optional[str]

    @property
    def group_path(self):
        """Group as a folder path, e.g. ly/count/android."""
        return self.group.replace(".", "/")

    @property
    def coordinates(self):
        """group:artifact."""
        return f"{self.group}:{self.artifact}"

    @property
    def module_dir(self):
        """Folder of the Gradle module, e.g. sdk-native for :sdk-native."""
        return self.module.lstrip(":").replace(":", "/")

    def folder(self, version):
        """Repository folder of one version, e.g. ly/count/android/sdk/26.2.0."""
        return f"{self.group_path}/{self.artifact}/{version}"

    def base_name(self, version):
        """File name stem of one version, e.g. sdk-26.2.0."""
        return f"{self.artifact}-{version}"


@dataclass(frozen=True)
class Plan:
    """What one tag publishes."""

    tag: str
    version: str
    listed: bool
    prerelease: bool
    branch: str
    artifacts: Tuple[Artifact, ...]

    @property
    def modules(self):
        """Gradle modules to build, in order, without duplicates."""
        return list(dict.fromkeys(artifact.module for artifact in self.artifacts))

    def to_json(self):
        """Serializable form, handed between workflow jobs as plan.json."""
        return {"tag": self.tag, "version": self.version, "listed": self.listed, "prerelease": self.prerelease, "branch": self.branch, "artifacts": [artifact.key for artifact in self.artifacts]}


def load_config(path=CONFIG_PATH):
    """Reads config.json."""
    return json.loads(Path(path).read_text(encoding="utf-8"))


def artifact_from_config(config, key):
    """Builds the Artifact described by config['artifacts'][key]."""
    spec = config["artifacts"][key]
    return Artifact(
        key, spec["group"], spec["artifact"], spec["packaging"], spec["module"], spec["sbom"], spec.get("contract"),
        spec.get("sbomFragment"), spec.get("nativeLibrariesFrom"), spec.get("consumerProbe"),
    )


def plan_for_tag(config, tag):
    """Maps a tag to its plan; raises PlanError for a tag outside the repository's grammar (ASCII digits only)."""
    for rule in config["tagRules"]:
        prefix, suffix = rule["tag"].split("{version}")
        match = re.fullmatch(f"{re.escape(prefix)}(?P<version>{RELEASE_VERSION}){re.escape(suffix)}", tag, re.ASCII)
        if match:
            version = match.group("version")
            prerelease = bool(RC_PATTERN.search(version))
            artifacts = tuple(artifact_from_config(config, key) for key in rule["artifacts"])
            return Plan(tag, version, not prerelease, prerelease, rule["branch"], artifacts)
    raise PlanError(f'"{tag}" is not a release tag of {config["repository"]}')


def checkout_tags(config, repo_root, branch):
    """Tags the checkout would be released as from the branch: one per tag rule of that branch, carrying the version
    held by the first version source of the rule's first artifact. Raises PlanError when that version is unreadable."""
    tags = []
    for rule in config["tagRules"]:
        if rule["branch"] != branch:
            continue
        source = config["artifacts"][rule["artifacts"][0]]["versionSources"][0]
        path = Path(repo_root) / source["file"]
        match = re.search(source["regex"], path.read_text(encoding="utf-8"), re.MULTILINE) if path.is_file() else None
        if match is None:
            raise PlanError(f"{source['file']}: no version found")
        tags.append(rule["tag"].format(version=match.group(1).strip()))
    return tags


def plan_from_json(config, data):
    """Rebuilds a plan written by Plan.to_json."""
    artifacts = tuple(artifact_from_config(config, key) for key in data["artifacts"])
    return Plan(data["tag"], data["version"], data["listed"], data["prerelease"], data["branch"], artifacts)
