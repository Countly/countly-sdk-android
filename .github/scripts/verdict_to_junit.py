#!/usr/bin/env python3
"""Convert content_test_runner verdict.json files into a single JUnit XML.

Each verdict.json (one per variant under <run_dir>/<variant>/verdict.json) becomes
a <testsuite>; each checklist key becomes a <testcase>. Two synthetic cases are
added per suite — `no_fatal_exceptions` and `no_strict_mode_violations` — so the
runner-level signals (which live outside the checklist) also surface in the
test-result UI rather than only in the artifact.

JUnit semantics used:
  PASS  → bare <testcase>
  FAIL  → <testcase><failure message=detail/></testcase>
  WARN  → <testcase><failure type="WARN" message=detail/></testcase>
          (kept as failure, not skipped, so it's visible in the action's UI;
           type="WARN" lets a reader distinguish it from a hard fail)
  SKIP  → <testcase><skipped/></testcase>

Runner-level errors[] (e.g. "am start ... did not bring activity to foreground")
are emitted as <system-err> on the testsuite, so they show in the suite detail
without being miscounted as a test failure on top of the checklist failure they
already produced.

Usage: verdict_to_junit.py <run_dir> <output_xml>
"""

import json
import sys
from pathlib import Path
from xml.etree import ElementTree as ET


def _add_status_child(testcase: ET.Element, status: str, detail: str) -> None:
    """Attach the JUnit child element matching `status`. PASS adds nothing."""
    if status == "PASS":
        return
    if status == "SKIP":
        ET.SubElement(testcase, "skipped", {"message": detail or "skipped"})
        return
    # FAIL and WARN both render as <failure>. WARN is tagged via `type` so the
    # reader can tell them apart in the published check.
    attrs = {"message": detail or status}
    if status == "WARN":
        attrs["type"] = "WARN"
    ET.SubElement(testcase, "failure", attrs)


def verdict_to_suite(verdict: dict, run_dir: Path) -> ET.Element:
    kind = verdict.get("kind", "test")
    variant = verdict.get("variant", "unknown")
    suite_name = f"{kind}.{variant}"

    checklist = verdict.get("checklist") or {}
    duration = float(verdict.get("duration_s") or 0.0)

    # Pre-count for the suite-level attributes — the publish action uses these
    # for the headline tally rather than recomputing from children.
    failures = sum(
        1 for c in checklist.values() if c.get("status") in ("FAIL", "WARN")
    )
    skipped = sum(1 for c in checklist.values() if c.get("status") == "SKIP")

    # Synthetic cases bump totals too, so account for them up front.
    fatals = int(verdict.get("fatal_exceptions") or 0)
    strict = int(verdict.get("incorrect_context_use_violations") or 0)
    if fatals > 0:
        failures += 1
    if strict > 0:
        failures += 1

    total = len(checklist) + 2  # +2 synthetic cases

    suite = ET.Element("testsuite", {
        "name": suite_name,
        "tests": str(total),
        "failures": str(failures),
        "skipped": str(skipped),
        "errors": "0",
        "time": f"{duration:.2f}",
    })

    # Per-checklist testcases. classname groups them under the variant in the
    # action's UI; name is the checklist key (e.g. widget_present, x_close).
    for key, item in checklist.items():
        status = (item.get("status") or "").upper()
        detail = item.get("detail") or ""
        tc = ET.SubElement(suite, "testcase", {
            "classname": suite_name,
            "name": key,
            "time": "0",
        })
        _add_status_child(tc, status, detail)

    # Synthetic: fatal exceptions seen in logcat during this variant. The
    # runner counts these but doesn't always reflect them in the checklist.
    tc_fatal = ET.SubElement(suite, "testcase", {
        "classname": suite_name,
        "name": "no_fatal_exceptions",
        "time": "0",
    })
    if fatals > 0:
        ET.SubElement(tc_fatal, "failure", {
            "message": f"{fatals} FATAL exception(s) recorded in logcat",
        })

    # Synthetic: StrictMode "incorrect context use" violations. These are
    # actionable SDK bugs (Activity context vs Application context) caught by
    # the App.java StrictModeConfigurator.
    tc_strict = ET.SubElement(suite, "testcase", {
        "classname": suite_name,
        "name": "no_strict_mode_violations",
        "time": "0",
    })
    if strict > 0:
        ET.SubElement(tc_strict, "failure", {
            "message": f"{strict} StrictMode incorrect-context-use violation(s)",
        })

    # Suite-level errors[]: these are runner diagnostics, not assertion fails.
    # Emit as system-err so they're visible in the suite detail without
    # double-counting against the failure tally.
    errors = verdict.get("errors") or []
    if errors:
        sys_err = ET.SubElement(suite, "system-err")
        sys_err.text = "\n".join(str(e) for e in errors)

    return suite


def main() -> int:
    if len(sys.argv) != 3:
        print(f"usage: {sys.argv[0]} <run_dir> <output_xml>", file=sys.stderr)
        return 2

    run_dir = Path(sys.argv[1])
    output_xml = Path(sys.argv[2])

    if not run_dir.is_dir():
        print(f"[!] run_dir not found: {run_dir}", file=sys.stderr)
        return 1

    verdicts = sorted(run_dir.glob("*/verdict.json"))
    if not verdicts:
        print(f"[!] no verdict.json under {run_dir} — emitting empty suites",
              file=sys.stderr)

    root = ET.Element("testsuites", {"name": "content_widget_test"})
    for vp in verdicts:
        try:
            with vp.open() as f:
                verdict = json.load(f)
        except (OSError, json.JSONDecodeError) as e:
            print(f"[!] skipping {vp}: {e}", file=sys.stderr)
            continue
        root.append(verdict_to_suite(verdict, run_dir))

    output_xml.parent.mkdir(parents=True, exist_ok=True)
    tree = ET.ElementTree(root)
    ET.indent(tree, space="  ")
    tree.write(output_xml, encoding="utf-8", xml_declaration=True)
    print(f"[+] wrote {output_xml} ({len(verdicts)} suite(s))")
    return 0


if __name__ == "__main__":
    sys.exit(main())
