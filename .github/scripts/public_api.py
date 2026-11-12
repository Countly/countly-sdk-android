"""Keeps the metalava compatibility errors that touch the public API integrators can reach.

Classes in the root packages are public API, and so is every class their signatures name, transitively,
together with the nested classes of each. A break in any other public class (an implementation class that is
public only so other packages of the SDK can call it) is printed as ignored instead of being reported.

Usage: public_api.py --root-package <package> [--root-package <package> ...] <metalava check log>
Prints each kept error message on its own line to stdout.
"""
import argparse
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
    """One metalava signature file: its class blocks and the classes integrators can reach."""

    def __init__(self, path, root_packages):
        """Parses the signature file at path and computes the classes reachable from root_packages."""
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
        self.reachable = self._reachable(root_packages)

    def _reachable(self, root_packages):
        """Returns the root package classes, the classes their signatures name, transitively, and nested classes."""
        classes = set(self.references)
        pending = [name for name in classes if any(_in_package(name, root) for root in root_packages)]
        reachable = set()
        while pending:
            name = pending.pop()
            if name in reachable:
                continue
            reachable.add(name)
            pending.extend(other for other in classes if other.startswith(f"{name}."))
            pending.extend(reference for reference in self.references.get(name, ()) if reference in classes)
        return reachable

    def class_at(self, line):
        """Returns the class whose block contains line, or None for a line outside every class."""
        for start, end, name in self.blocks:
            if start <= line <= end:
                return name
        return None


def main():
    """Reads a metalava check log and prints the compatibility errors that touch reachable public API."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--root-package", action="append", required=True)
    parser.add_argument("log")
    arguments = parser.parse_args()
    roots = set(arguments.root_package)
    signatures = {}
    found = 0
    with open(arguments.log, encoding="utf-8", errors="replace") as handle:
        for raw in handle:
            match = ERROR.match(ANSI.sub("", raw.strip()))
            if not match:
                continue
            found += 1
            path, message = match.group("path"), match.group("message")
            if path not in signatures:
                signatures[path] = Signature(path, roots)
            owner = signatures[path].class_at(int(match.group("line")))
            if owner is None or owner in signatures[path].reachable:
                print(message)
            else:
                print(f"Ignored, {owner} is not reachable from the public API: {message}", file=sys.stderr)
    if found == 0:
        sys.exit("No metalava error lines found in the log.")


if __name__ == "__main__":
    main()
