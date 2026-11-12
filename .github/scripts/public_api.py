"""Keeps the metalava compatibility errors that touch the public API integrators can reach."""
import argparse
import glob
import os
import re
import sys

ERROR = re.compile(r"^(?P<path>\S+?\.txt):(?P<line>\d+): error: (?P<message>.*)$")
PACKAGE = re.compile(r"^package (?P<name>[\w.]+) \{$")
CLASS = re.compile(r"^  (?:@\S+\s+)*(?:(?:public|protected|static|final|abstract|sealed)\s+)*"
                   r"(?:class|interface|enum|@interface)\s+(?P<name>[\w.$]+)")
REFERENCE = re.compile(r"\bly\.count(?:\.[A-Za-z_$][\w$]*)+")
ANSI = re.compile(r"\x1b\[[0-9;]*m")


def _in_package(name, package):
    """Returns whether the class name, nested or not, is declared directly in package and not in a subpackage."""
    return name.startswith(f"{package}.") and name[len(package) + 1:][:1].isupper()


class Signature:
    """One metalava signature file: its class blocks and the classes each class's signature names."""

    def __init__(self, path):
        """Parses the signature file at path."""
        self.blocks = []
        self.references = {}
        package, current, start = None, None, 0
        with open(path, encoding="utf-8") as handle:
            lines = handle.read().splitlines()
        for number, text in enumerate(lines, start=1):
            package_match, class_match = PACKAGE.match(text), CLASS.match(text)
            if package_match:
                package = package_match.group("name")
            elif class_match and package:
                current, start = f"{package}.{class_match.group('name')}", number
                self.references[current] = set(REFERENCE.findall(text))
            elif current and text == "  }":
                self.blocks.append((start, number, current))
                current = None
            elif current:
                self.references[current].update(REFERENCE.findall(text))

    def class_at(self, line):
        """Returns the class whose block contains line, or None for a line outside every class."""
        for start, end, name in self.blocks:
            if start <= line <= end:
                return name
        return None


def reachable(signatures, root_packages):
    """Returns the root package classes, the classes their signatures name, transitively, and nested classes."""
    references = {}
    for signature in signatures:
        for name, named in signature.references.items():
            references.setdefault(name, set()).update(named)
    classes = set(references)
    pending = [name for name in classes if any(_in_package(name, root) for root in root_packages)]
    found = set()
    while pending:
        name = pending.pop()
        if name in found:
            continue
        found.add(name)
        pending.extend(other for other in classes if other.startswith(f"{name}."))
        pending.extend(reference for reference in references.get(name, ()) if reference in classes)
    return found


def main():
    """Reads a metalava check log and prints the compatibility errors that touch reachable public API."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root-package", action="append", required=True,
                        help="a package whose classes are public API; repeat it for each root package")
    parser.add_argument("--signatures", required=True,
                        help="every module's base signatures: one module's public API can name another's classes")
    parser.add_argument("--module-dir", required=True,
                        help="the module's directory: metalava names this pull request's signature relative to it")
    parser.add_argument("log", help="the metalava compatibility check log")
    arguments = parser.parse_args()
    base = [Signature(path) for path in sorted(glob.glob(os.path.join(arguments.signatures, "*.txt")))]
    if not base:
        sys.exit(f"No base signatures in {arguments.signatures}.")
    public = reachable(base, set(arguments.root_package))
    signatures = {}
    found = 0
    with open(arguments.log, encoding="utf-8", errors="replace") as handle:
        for raw in handle:
            match = ERROR.match(ANSI.sub("", raw.strip()))
            if not match:
                continue
            found += 1
            path = os.path.join(arguments.module_dir, match.group("path"))
            message = match.group("message")
            if path not in signatures:
                signatures[path] = Signature(path)
            owner = signatures[path].class_at(int(match.group("line")))
            if owner is None or owner in public:
                print(message)
            else:
                # A class that is public only so other SDK packages can call it is not integrator API.
                print(f"Ignored, {owner} is not reachable from the public API: {message}", file=sys.stderr)
    if found == 0:
        sys.exit("No metalava error lines found in the log.")


if __name__ == "__main__":
    main()
