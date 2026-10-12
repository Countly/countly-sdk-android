"""Gradle commands for the clean app in .github/release/consumers that builds against a staged release."""

import re

ANDROID_PROJECT = ".github/release/consumers/android"
FLAGS = ["--no-daemon", "--no-configuration-cache", "--stacktrace"]
AGP_CLASSPATH = re.compile(r"""com\.android\.tools\.build:gradle:([^'"\s]+)""")


def project_agp(build_gradle):
    """The AGP version the checkout's root build.gradle builds with."""
    return AGP_CLASSPATH.search(build_gradle).group(1)


def android_command(pair, gradlew, repository, artifact, version, checkout_agp):
    """Builds the consumer app with one AGP/Gradle pair against one artifact version: libraries are depended on, the
    plugin is applied. A pair whose AGP is "{project}" uses the checkout's own AGP, which its Gradle wrapper supports."""
    gradle = gradlew if pair["gradle"] == "{gradlew}" else pair["gradle"]
    agp = checkout_agp if pair["agp"] == "{project}" else pair["agp"]
    command = [
        gradle, "-p", ANDROID_PROJECT, *FLAGS, "assembleDebug",
        f"-PcountlyRepository={repository}", f"-PagpVersion={agp}",
        f"-PconsumerCompileSdk={pair['compileSdk']}", f"-PconsumerBuildTools={pair['buildTools']}",
    ]
    if artifact.consumer_probe == "plugin":
        return command + [f"-PcountlyPluginVersion={version}", "-PcountlyProbe=none"]
    return command + [f"-PcountlyDependency={artifact.coordinates}:{version}", f"-PcountlyProbe={artifact.consumer_probe}"]
