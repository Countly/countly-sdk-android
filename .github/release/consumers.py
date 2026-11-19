"""Gradle commands for the clean app in .github/release/consumers that builds against a staged release."""

import os
import re

ANDROID_PROJECT = ".github/release/consumers/android"
FLAGS = ["--no-daemon", "--no-configuration-cache", "--stacktrace"]
AGP_CLASSPATH = re.compile(r"""com\.android\.tools\.build:gradle:([^'"\s]+)""")
ENV_GRADLE = re.compile(r"\{env:(?P<name>[A-Za-z0-9_]+)\}")


def project_agp(build_gradle):
    """The AGP version the checkout's root build.gradle builds with."""
    return AGP_CLASSPATH.search(build_gradle).group(1)


def gradle_executable(pair, gradlew, environ=None):
    """The Gradle one pair builds with: the checkout's wrapper for "{gradlew}", the executable an environment variable
    holds for "{env:NAME}" (the release workflow installs each Gradle version and records its path there), else the
    command as written. Raises ValueError when the named variable is not set."""
    environ = os.environ if environ is None else environ
    if pair["gradle"] == "{gradlew}":
        return gradlew
    match = ENV_GRADLE.fullmatch(pair["gradle"])
    if match is None:
        return pair["gradle"]
    path = environ.get(match.group("name"))
    if not path:
        raise ValueError(f"{pair['name']}: {match.group('name')} does not name a Gradle executable in this environment")
    return path


def android_command(pair, gradlew, repository, artifact, version, checkout_agp, environ=None):
    """Builds the consumer app with one AGP/Gradle pair against one artifact version: libraries are depended on, the
    plugin is applied. A pair whose AGP is "{project}" uses the checkout's own AGP, which its Gradle wrapper supports."""
    gradle = gradle_executable(pair, gradlew, environ)
    agp = checkout_agp if pair["agp"] == "{project}" else pair["agp"]
    command = [
        gradle, "-p", ANDROID_PROJECT, *FLAGS, "assembleDebug",
        f"-PcountlyRepository={repository}", f"-PagpVersion={agp}",
        f"-PconsumerCompileSdk={pair['compileSdk']}", f"-PconsumerBuildTools={pair['buildTools']}",
    ]
    if artifact.consumer_probe == "plugin":
        return command + [f"-PcountlyPluginVersion={version}", "-PcountlyProbe=none"]
    return command + [f"-PcountlyDependency={artifact.coordinates}:{version}", f"-PcountlyProbe={artifact.consumer_probe}"]
