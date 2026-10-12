"""Compares what a staged artifact promises integrators with its committed contract file."""

import io
import json
import struct
import zipfile
import xml.etree.ElementTree as ElementTree
from pathlib import Path

POM_NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}
JVM_VERSION = "org.gradle.jvm.version"
ABIS_64 = ("arm64-v8a", "x86_64")
PT_LOAD = 1


def _child(element, tag, default=""):
    return (element.findtext(f"m:{tag}", default, POM_NAMESPACE) or default).strip()


def pom_dependencies(pom):
    """Sorted group:artifact:version:scope strings of the POM's direct dependencies."""
    root = ElementTree.fromstring(pom)
    return sorted(
        f"{_child(d, 'groupId')}:{_child(d, 'artifactId')}:{_child(d, 'version')}:{_child(d, 'scope', 'compile')}"
        for d in root.findall("m:dependencies/m:dependency", POM_NAMESPACE)
    )


def module_variants(module):
    """Library variants (not sources or javadoc) of a Gradle module file."""
    return [variant for variant in json.loads(module).get("variants", []) if variant.get("attributes", {}).get("org.gradle.category") == "library"]


def module_dependencies(module):
    """Sorted group:module:version strings declared by the library variants of a Gradle module file."""
    found = set()
    for variant in module_variants(module):
        for dependency in variant.get("dependencies", []):
            version = dependency.get("version", {})
            number = version.get("requires") or version.get("strictly") or version.get("prefers") or ""
            found.add(f"{dependency['group']}:{dependency['module']}:{number}")
    return sorted(found)


def aar_metadata(aar):
    """Entries of META-INF/com/android/build/gradle/aar-metadata.properties."""
    with zipfile.ZipFile(io.BytesIO(aar)) as archive:
        text = archive.read("META-INF/com/android/build/gradle/aar-metadata.properties").decode("utf-8")
    entries = {}
    for line in text.splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            entries[key.strip()] = value.strip()
    return entries


def max_class_major(jar):
    """Highest class-file major version in a jar (52 is Java 8), ignoring multi-release folders."""
    highest = 0
    with zipfile.ZipFile(io.BytesIO(jar)) as archive:
        for name in archive.namelist():
            if name.endswith(".class") and not name.startswith("META-INF/versions/"):
                head = archive.read(name)[:8]
                if len(head) == 8 and head[:4] == b"\xca\xfe\xba\xbe":
                    highest = max(highest, (head[6] << 8) | head[7])
    return highest


def native_libraries(aar):
    """Native libraries of an AAR as {'<abi>/<file>.so': bytes}."""
    with zipfile.ZipFile(io.BytesIO(aar)) as archive:
        return {name[len("jni/"):]: archive.read(name) for name in archive.namelist() if name.startswith("jni/") and name.endswith(".so")}


def load_alignments(elf):
    """p_align of every PT_LOAD segment of a 64-bit little-endian ELF file; empty for any other file."""
    if len(elf) < 64 or elf[:4] != b"\x7fELF" or elf[4] != 2 or elf[5] != 1:
        return []
    (offset,) = struct.unpack_from("<Q", elf, 0x20)
    entry_size, count = struct.unpack_from("<HH", elf, 0x36)
    alignments = []
    for index in range(count):
        start = offset + index * entry_size
        if start + 56 <= len(elf) and struct.unpack_from("<I", elf, start)[0] == PT_LOAD:
            alignments.append(struct.unpack_from("<Q", elf, start + 48)[0])
    return alignments


def _common(variants, key):
    """The attribute value all variants share (None when absent), or a note that they differ."""
    values = {json.dumps(variant.get("attributes", {}).get(key)) for variant in variants}
    if len(values) == 1:
        return json.loads(values.pop())
    return None if not values else "differs between variants"


def actual_contract(staging_dir, artifact, version):
    """The contract the staged files of one artifact version fulfil, in the shape of the contract files (schema 1)."""
    folder = Path(staging_dir) / artifact.folder(version)
    base = artifact.base_name(version)
    main = (folder / f"{base}.{artifact.packaging}").read_bytes()
    contract = {
        "schema": 1,
        "coordinates": artifact.coordinates,
        "pomDependencies": pom_dependencies((folder / f"{base}.pom").read_bytes()),
        "moduleVariantAttributes": {JVM_VERSION: _common(module_variants((folder / f"{base}.module").read_bytes()), JVM_VERSION)},
    }
    if artifact.packaging == "aar":
        with zipfile.ZipFile(io.BytesIO(main)) as archive:
            classes = archive.read("classes.jar")
        contract["aarMetadata"] = aar_metadata(main)
        contract["maxClassFileMajor"] = max_class_major(classes)
        libraries = native_libraries(main)
        if libraries:
            alignments = [value for name, data in libraries.items() if name.split("/")[0] in ABIS_64 for value in load_alignments(data)]
            contract["nativeLibraries"] = {"abis": sorted({name.split("/")[0] for name in libraries}), "loadAlignment64": min(alignments) if alignments else None}
    else:
        contract["maxClassFileMajor"] = max_class_major(main)
    return contract


def compare(expected, actual):
    """Differences between the committed contract and the staged artifact, as readable problems."""
    problems = []
    if expected["coordinates"] != actual["coordinates"]:
        problems.append(f"coordinates are {actual['coordinates']}, the contract says {expected['coordinates']}")
    if expected["pomDependencies"] != actual["pomDependencies"]:
        problems.append(f"POM dependencies are {actual['pomDependencies']}, the contract says {expected['pomDependencies']}")
    for key, value in expected.get("moduleVariantAttributes", {}).items():
        found = actual["moduleVariantAttributes"].get(key)
        if found != value:
            problems.append(f"{key} is {found}, the contract says {value}")
    for key, value in expected.get("aarMetadata", {}).items():
        found = actual.get("aarMetadata", {}).get(key)
        if found != value:
            problems.append(f"aar-metadata {key} is {found}, the contract says {value}")
    if actual["maxClassFileMajor"] > expected["maxClassFileMajor"]:
        problems.append(f"class files reach major version {actual['maxClassFileMajor']}, the contract allows {expected['maxClassFileMajor']}")
    if "nativeLibraries" in expected:
        found = actual.get("nativeLibraries", {})
        if found.get("abis", []) != expected["nativeLibraries"]["abis"]:
            problems.append(f"native abis are {found.get('abis', [])}, the contract says {expected['nativeLibraries']['abis']}")
        wanted = expected["nativeLibraries"].get("loadAlignment64")
        if wanted is not None and (found.get("loadAlignment64") or 0) < wanted:
            problems.append(f"64-bit native libraries are aligned to {found.get('loadAlignment64')} bytes, the contract requires {wanted}")
    return problems


def module_dependency_problems(staging_dir, artifact, version):
    """Problems when the .module declares other dependencies than the POM; Gradle builds read the .module, Maven builds the POM."""
    folder = Path(staging_dir) / artifact.folder(version)
    base = artifact.base_name(version)
    in_module = module_dependencies((folder / f"{base}.module").read_bytes())
    in_pom = sorted(entry.rsplit(":", 1)[0] for entry in pom_dependencies((folder / f"{base}.pom").read_bytes()))
    return [] if in_module == in_pom else [f"the .module declares {in_module} but the POM declares {in_pom}"]


def committed_library_problems(aar, repo_root, relative_dir):
    """Problems when the AAR's native libraries are not exactly the files committed under relative_dir."""
    root = Path(repo_root) / relative_dir
    packaged = native_libraries(aar)
    committed = {path.relative_to(root).as_posix(): path.read_bytes() for path in root.rglob("*.so")}
    problems = [f"jni/{name} is not committed under {relative_dir}" for name in sorted(set(packaged) - set(committed))]
    problems += [f"{relative_dir}/{name} is missing from the AAR" for name in sorted(set(committed) - set(packaged))]
    problems += [f"jni/{name} differs from the committed {relative_dir}/{name}" for name in sorted(set(packaged) & set(committed)) if packaged[name] != committed[name]]
    return problems


def _shown(value):
    return "absent" if value is None else str(value)


def summary_rows(expected, actual):
    """(property, contract, built) rows for the job summary table."""
    def listing(values):
        return ", ".join(values) if values else "none"

    rows = [
        ("POM dependencies", listing(expected["pomDependencies"]), listing(actual["pomDependencies"])),
        (JVM_VERSION, _shown(expected.get("moduleVariantAttributes", {}).get(JVM_VERSION)), _shown(actual["moduleVariantAttributes"].get(JVM_VERSION))),
        ("highest class file version", f"at most {expected['maxClassFileMajor']}", str(actual["maxClassFileMajor"])),
    ]
    for key, value in expected.get("aarMetadata", {}).items():
        rows.append((f"aar-metadata {key}", value, _shown(actual.get("aarMetadata", {}).get(key))))
    if "nativeLibraries" in expected:
        found = actual.get("nativeLibraries", {})
        rows.append(("native ABIs", listing(expected["nativeLibraries"]["abis"]), listing(found.get("abis", []))))
        rows.append(("64-bit LOAD alignment", f"at least {expected['nativeLibraries'].get('loadAlignment64')}", _shown(found.get("loadAlignment64"))))
    return rows
