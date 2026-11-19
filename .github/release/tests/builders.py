"""Builders for staged artifacts: class files, jars, AARs with native libraries, POMs and Gradle module files."""

import io
import json
import struct
import zipfile
from pathlib import Path

from layout import primary_files, write_checksums


def class_bytes(major):
    """A minimal class-file header with the given major version."""
    return b"\xca\xfe\xba\xbe" + (0).to_bytes(2, "big") + major.to_bytes(2, "big") + b"\x00" * 8


def jar_bytes(majors):
    """A jar holding one class file per major version."""
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        for number, major in enumerate(majors):
            archive.writestr(f"ly/count/C{number}.class", class_bytes(major))
    return buffer.getvalue()


def elf_bytes(alignment, loads=2):
    """A minimal 64-bit little-endian ELF header with `loads` PT_LOAD program headers aligned to `alignment`."""
    header = bytearray(64)
    header[0:6] = b"\x7fELF\x02\x01"
    struct.pack_into("<Q", header, 0x20, 64)
    struct.pack_into("<HH", header, 0x36, 56, loads)
    programs = b"".join(struct.pack("<IIQQQQQQ", 1, 5, 0, 0, 0, 0, 0, alignment) for _ in range(loads))
    return bytes(header) + programs


def aar_bytes(majors, metadata, libraries=None):
    """An AAR with classes.jar, aar-metadata.properties and native libraries given as {'<abi>/<file>.so': bytes}."""
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("classes.jar", jar_bytes(majors))
        archive.writestr("META-INF/com/android/build/gradle/aar-metadata.properties", "".join(f"{k}={v}\n" for k, v in metadata.items()))
        for name, data in (libraries or {}).items():
            archive.writestr(f"jni/{name}", data)
    return buffer.getvalue()


def pom_bytes(dependencies):
    """A POM with the given group:artifact:version:scope dependencies."""
    entries = "".join(
        f"<dependency><groupId>{g}</groupId><artifactId>{a}</artifactId><version>{v}</version><scope>{s}</scope></dependency>"
        for g, a, v, s in (dependency.split(":") for dependency in dependencies)
    )
    return f'<?xml version="1.0"?><project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><dependencies>{entries}</dependencies></project>'.encode("utf-8")


def module_bytes(jvm_version=None, dependencies=()):
    """A Gradle module file with a runtime library variant (optionally with org.gradle.jvm.version and group:module:version dependencies) and a sources variant."""
    attributes = {"org.gradle.category": "library", "org.gradle.usage": "java-runtime"}
    if jvm_version is not None:
        attributes["org.gradle.jvm.version"] = jvm_version
    declared = []
    for dependency in dependencies:
        group, module, version = dependency.split(":")
        declared.append({"group": group, "module": module, "version": {"requires": version}})
    variants = [{"name": "runtime", "attributes": attributes, "dependencies": declared}, {"name": "sources", "attributes": {"org.gradle.category": "documentation"}}]
    return json.dumps({"formatVersion": "1.1", "variants": variants}).encode("utf-8")


def write_artifact(staging, artifact, version, main=None, pom=None, module=None):
    """Writes a complete staged artifact version (primary files plus four checksums each) and returns its folder."""
    folder = Path(staging) / artifact.folder(version)
    folder.mkdir(parents=True, exist_ok=True)
    base = artifact.base_name(version)
    contents = {name: f"content of {name}".encode("utf-8") for name in primary_files(artifact, version)}
    if artifact.packaging == "aar":
        contents[f"{base}.aar"] = main if main is not None else aar_bytes([52], {"minCompileSdk": "24"})
    if artifact.packaging == "jar":
        contents[f"{base}.jar"] = main if main is not None else jar_bytes([52])
    if artifact.packaging != "marker":
        contents[f"{base}.module"] = module if module is not None else module_bytes()
    contents[f"{base}.pom"] = pom if pom is not None else pom_bytes([])
    for name, data in contents.items():
        (folder / name).write_bytes(data)
        write_checksums(folder / name)
    return folder
