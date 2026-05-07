#!/usr/bin/env python3
"""Countly Android SDK content/feedback widget UI test runner.

Iterates the configured CONTENT_VARIANTS and FEEDBACK_TYPES (see
content_test_config.py), drives the demo app via adb + UIAutomator, records a
video per variant, and writes per-variant verdict.json plus a summary.md.

Usage:
    python3 content_test_runner.py
    python3 content_test_runner.py --only modal,nps
    python3 content_test_runner.py --no-feedback
    python3 content_test_runner.py --device emulator-5556

Requires adb on PATH and a device/emulator with the demo app installed.
Stdlib only (no pip install needed).
"""

import argparse
import json
import random
import re
import secrets
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path
from typing import Optional

# Make config importable when script is run directly
sys.path.insert(0, str(Path(__file__).parent))
from content_test_config import (  # noqa: E402
    DEMO_PACKAGE,
    DEMO_LAUNCH_ACTIVITY,
    CONTENT_ZONE_ACTIVITY,
    FEEDBACK_ACTIVITY,
    CONTENT_ZONE_DEVICE_ID_FIELD,
    CONTENT_ZONE_BTN_CHANGE_DEVICE_ID,
    CONTENT_ZONE_BTN_ENTER_ZONE,
    CONTENT_ZONE_BTN_EXIT_ZONE,
    ACTIVITY_NAVIGATION,
    CONTENT_VARIANTS,
    FULLSCREEN_VARIANTS,
    FEEDBACK_TYPES,
    POKE_ACTIVITIES,
    LOG_PATTERNS,
    TIMEOUTS,
    CHROME_PACKAGE_HINTS,
    CHROME_URL_BAR_IDS,
    WEBVIEW_HINTS,
    # Feedback widget selectors + lorem text pool
    WIDGET_AUTOMATION_TEXT,
    SURVEY_V2_CLOSE_SELECTOR,
    SURVEY_V2_TERMS_LINK,
    SURVEY_V2_PRIVACY_LINK,
    SURVEY_SUBMIT_BUTTON,
    SURVEY_CONSENT_CHECKBOX,
    SURVEY_RADIO_OPTION,
    NPS_NEXT_BUTTON,
    NPS_SUBMIT_BUTTON,
    NPS_CONSENT_CHECKBOX,
    NPS_RATING_BUTTON_FMT,
    NPS_COMMENT_TEXTAREA,
    RATING_CLOSE_SELECTOR,
    RATING_EMOJI_FMT,
    RATING_ADD_COMMENT_CHECKBOX,
    RATING_COMMENT_TEXTAREA,
    RATING_EMAIL_CHECKBOX,
    RATING_EMAIL_INPUT,
    RATING_CONSENT_CHECKBOX,
    RATING_TERMS_LINK,
    RATING_PRIVACY_LINK,
    RATING_SUBMIT_BUTTON,
    LOREM_TEXT_POOL,
    LOREM_EMAIL,
)
from cdp_client import CDP, CDPError, remove_forward  # noqa: E402

# ============================================================================
# Globals
# ============================================================================

_DEVICE_SERIAL: Optional[str] = None  # set from --device CLI arg
VERBOSE: bool = False                 # set from --verbose CLI arg


def vlog(msg: str) -> None:
    if VERBOSE:
        print(f"    {msg}")


def retry_action(action_fn, predicate_fn=None, *, attempts: int = 3,
                 settle_s: float = 0.5, label: str = "") -> bool:
    """Generic retry wrapper for semantic actions. Calls `action_fn()` up to
    `attempts` times; after each call, checks `predicate_fn()` (or treats the
    action's truthy return value as success when no predicate is provided).
    Returns True on the first successful attempt, False if all attempts fail.

    Used at semantic-action boundaries — `ensure_on_content_zone`, navigation,
    close-click, device-id-set — where flaky emulator state causes a single
    attempt to miss but a retry usually succeeds. Not applied to primitive
    `tap`/`key`/`input_text` (those have no post-condition we can verify;
    `_input_shell` already retries once on TimeoutExpired).
    """
    for attempt in range(1, attempts + 1):
        try:
            result = action_fn()
        except Exception as e:
            vlog(f"[retry {label or 'action'}] attempt {attempt}/{attempts} "
                 f"raised {type(e).__name__}: {e}")
            result = None
        if predicate_fn is None:
            if result:
                return True
        else:
            try:
                if predicate_fn():
                    return True
            except Exception as e:
                vlog(f"[retry {label or 'action'}] predicate raised "
                     f"{type(e).__name__}: {e}")
        if attempt < attempts:
            vlog(f"[retry {label or 'action'}] attempt {attempt} failed; "
                 f"settling {settle_s}s before retry")
            time.sleep(settle_s)
    return False


def _adb_args(extra: list[str]) -> list[str]:
    cmd = ["adb"]
    if _DEVICE_SERIAL:
        cmd += ["-s", _DEVICE_SERIAL]
    return cmd + extra


# ============================================================================
# adb helpers
# ============================================================================

def adb(*args: str, timeout: float = 30) -> subprocess.CompletedProcess:
    return subprocess.run(
        _adb_args(list(args)),
        capture_output=True, text=True, timeout=timeout
    )


def shell(cmd: str, timeout: float = 30) -> str:
    return adb("shell", cmd, timeout=timeout).stdout


def _input_shell(cmd: str) -> None:
    """Runs an `input ...` command with one retry on TimeoutExpired.

    Android's `input` binary calls into InputManagerService over Binder; the
    emulator's IMS occasionally wedges for a few seconds, and any single
    `input keyevent / tap / text` call can time out. One retry after a short
    settle is enough to ride over transient hangs. A second timeout still
    propagates so a truly stuck device fails the sweep instead of silently
    hanging forever.
    """
    try:
        shell(cmd)
    except subprocess.TimeoutExpired:
        time.sleep(2.0)
        shell(cmd)


def tap(x: int, y: int) -> None:
    _input_shell(f"input tap {x} {y}")
    time.sleep(0.2)


def input_text(text: str) -> None:
    # adb input text doesn't handle spaces or quotes well; encode spaces.
    escaped = text.replace(" ", "%s").replace("'", "")
    _input_shell(f"input text '{escaped}'")
    time.sleep(0.15)


def key(keycode: str) -> None:
    _input_shell(f"input keyevent {keycode}")
    time.sleep(0.2)


def long_press_clear(x: int, y: int) -> None:
    """Approximation: tap field, select all, delete."""
    tap(x, y)
    _input_shell("input keyevent KEYCODE_MOVE_END")
    for _ in range(64):  # delete up to 64 chars
        _input_shell("input keyevent KEYCODE_DEL")


def rotation(value: int) -> None:
    """0=portrait, 1=landscape, 2=reverse-portrait, 3=reverse-landscape."""
    shell("settings put system accelerometer_rotation 0")
    shell(f"settings put system user_rotation {value}")
    time.sleep(1.0)  # emulator settles fast


def force_stop() -> None:
    shell(f"am force-stop {DEMO_PACKAGE}")
    time.sleep(1.5)  # let system clean up before next launch


def wake_and_unlock() -> None:
    """Make sure the screen is on, the keyguard is dismissed, and the
    orientation is portrait. Resetting orientation here covers the case where
    a previous (killed) test left the device in landscape — `user_rotation` is
    persisted in `Settings.System`, so it survives the runner restart and
    breaks every subsequent UI dump / coordinate-based tap."""
    shell("input keyevent KEYCODE_WAKEUP")
    time.sleep(0.2)
    # Swipe-up to dismiss keyguard. No-op if there isn't one.
    shell("input keyevent KEYCODE_MENU")  # tries to dismiss simple keyguards
    time.sleep(0.15)
    shell("wm dismiss-keyguard")
    # Force portrait — rotation() also pins accelerometer_rotation off so a
    # subsequent rotation(1) inside a test still works.
    shell("settings put system accelerometer_rotation 0")
    shell("settings put system user_rotation 0")
    time.sleep(0.5)


def wait_for_top_activity(activity_class: str, timeout: float = 6.0) -> bool:
    """Polls until the named activity is on top, or timeout. Returns True on success."""
    target = f"{DEMO_PACKAGE}/.{activity_class}"
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        top = top_activity()
        if target in top:
            return True
        time.sleep(0.4)
    return False


def am_start(activity: str, *, wait_for_top: bool = True) -> bool:
    """Launch an activity in the demo app. Returns True if it reaches the foreground.

    Uses `am start -W` (wait for launch) and parses the output for known errors.
    Note: this only works for *exported* activities. For non-exported demo
    activities, use `open_demo_activity` which navigates via MainActivity.
    """
    out = shell(f"am start -W -n {DEMO_PACKAGE}/.{activity}")
    if "Error" in out or "does not exist" in out or "SecurityException" in out:
        vlog(f"[!] am start failed for {activity}: {out.strip().splitlines()[0][:160]}")
        return False
    time.sleep(0.5)
    if wait_for_top and not wait_for_top_activity(activity):
        vlog(f"[!] {activity} did not reach foreground; top: {top_activity().strip()[:120]}")
        return False
    return True


def launcher_intent() -> bool:
    """Launch the demo via its LAUNCHER intent (always exported via the
    `<intent-filter>` on MainActivity). Returns True on successful foregrounding.

    Uses `FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK` (0x00008000 |
    0x10000000 = 0x18000000) so that if the demo's task already has activities
    above MainActivity (e.g., we're returning from a poke that opened
    `ActivityExampleUserDetails`), those get finished and MainActivity comes up
    fresh. Without these flags, a plain LAUNCHER intent only brings the task
    forward — the topmost activity stays visible and `MainActivity` never
    becomes the top, causing every subsequent navigation in a warm-app run to
    time out and fall through to a hard reset.
    """
    out = shell(
        f"am start -W "
        f"-a android.intent.action.MAIN "
        f"-c android.intent.category.LAUNCHER "
        f"-f 0x18000000 "
        f"-n {DEMO_PACKAGE}/.{DEMO_LAUNCH_ACTIVITY}"
    )
    if "Error" in out or "SecurityException" in out:
        vlog(f"[!] launcher intent error: {out.strip().splitlines()[0][:160]}")
        return False
    if wait_for_top_activity(DEMO_LAUNCH_ACTIVITY, timeout=10):
        return True
    # Last resort: monkey
    shell(f"monkey -p {DEMO_PACKAGE} -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1")
    return wait_for_top_activity(DEMO_LAUNCH_ACTIVITY, timeout=8)


def scroll_until_visible(text_hint: str, max_scrolls: int = 6) -> Optional[ET.Element]:
    """Swipe-scrolls the foreground until a node containing `text_hint` appears.
    Returns the matched node (clickable preferred) or None."""
    for attempt in range(max_scrolls + 1):
        root = dump_ui()
        if root is not None:
            # Prefer clickable matches; fall back to any match (we'll walk to a
            # clickable ancestor when tapping).
            clickable = find_nodes_by_text_contains(root, [text_hint], clickable_only=True)
            if clickable:
                return clickable[0]
            any_match = find_nodes_by_text_contains(root, [text_hint], clickable_only=False)
            if any_match:
                return any_match[0]
        if attempt == max_scrolls:
            break
        sw, sh = screen_size()
        # Swipe up = scroll content down
        shell(f"input swipe {sw // 2} {int(sh * 0.75)} {sw // 2} {int(sh * 0.25)} 300")
        time.sleep(0.6)
    return None


def open_demo_activity(activity_class: str) -> bool:
    """Open a demo activity reliably, handling non-exported activities by routing
    through MainActivity. Returns True if `activity_class` reaches the foreground.

    Does NOT call `dismiss_lingering_overlay()` here — that helper is for
    clearing stale overlays from killed-prior-runs, but during the sticky
    variants' pokes phase the overlay is the ACTIVE test's overlay (covering
    only the top 378px of MainActivity, leaving navigation links below
    directly tappable). The dismiss helper's fallback strategies couldn't find
    the sticky widget's actual close button — they tried screen-top-right and
    bottom-center, then force-stopped the demo as last resort, killing the
    test's own overlay mid-pokes. Between-variant cleanup is preserved via
    `ensure_on_content_zone()` which still calls dismiss at variant start.
    """
    # Try direct am start first — fast path for activities that are exported.
    if am_start(activity_class):
        return True

    # Fallback: launcher → MainActivity → tap navigation link → target
    link_text = ACTIVITY_NAVIGATION.get(activity_class)
    if not link_text:
        vlog(f"[!] No navigation link configured for {activity_class}")
        return False
    if not launcher_intent():
        vlog(f"[!] Launcher intent didn't bring {DEMO_LAUNCH_ACTIVITY} to top")
        return False
    vlog(f"  MainActivity on top, navigating to '{link_text}'")
    node = scroll_until_visible(link_text)
    if node is None:
        vlog(f"[!] '{link_text}' not visible after scrolling")
        return False
    tap_node(node)
    if not wait_for_top_activity(activity_class, timeout=8):
        vlog(f"[!] Tap on '{link_text}' didn't open {activity_class}; top: "
             f"{top_activity().strip()[:120]}")
        return False
    vlog(f"  {activity_class} on top")
    return True


def clear_logcat() -> None:
    adb("logcat", "-c")


def read_logcat() -> str:
    return adb("logcat", "-d").stdout


def screen_size() -> tuple[int, int]:
    out = shell("wm size")
    m = re.search(r"(\d+)x(\d+)", out)
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


def top_activity() -> str:
    """Returns a string containing the foregrounded activity reference.

    Three label variants observed across Android builds:
      - Pre-API 28: `mResumedActivity=...`
      - API 28+ AOSP: `topResumedActivity=...`
      - API 30 google-variant emulator (CI image): `ResumedActivity: ...`
        (no `m` prefix, colon separator)

    Some Android forks emit a mix. Match all six combinations
    (`m`/`top`/bare prefix × `=`/`:` separator) so a tighter dumpsys schema
    on one image doesn't silently break top-activity detection.
    """
    # Anchor to start-of-line (with optional indent) so `mLastResumedActivity`
    # or any longer label that happens to embed "ResumedActivity" can't match.
    out = shell(
        "dumpsys activity activities | "
        "grep -E '^[[:space:]]*(m|top)?ResumedActivity[=:]'"
    )
    return out.strip()


def is_chrome_on_top() -> bool:
    top = top_activity()
    return any(pkg in top for pkg in CHROME_PACKAGE_HINTS)


def read_chrome_url_bar(timeout: float = 4.0) -> Optional[str]:
    """Reads Chrome's address-bar text. Polls up to `timeout` because Chrome may
    take ~1s to populate the omnibox after launch. Returns None if not found
    (different Chrome version, browser dialog showing, etc.)."""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        root = dump_ui()
        if root is not None:
            for node in root.iter("node"):
                rid = node.get("resource-id") or ""
                if rid in CHROME_URL_BAR_IDS:
                    text = node.get("text") or node.get("content-desc") or ""
                    if text.strip():
                        return text
        time.sleep(0.4)
    return None


# ============================================================================
# Logcat assertion helpers
# ============================================================================

def wait_for_log(pattern_key: str, timeout: float) -> Optional[re.Match]:
    """Polls logcat for a regex match. Returns the Match or None on timeout.
    Logcat is cleared at test start so the full buffer is the relevant window.
    """
    rx = re.compile(LOG_PATTERNS[pattern_key])
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        out = read_logcat()
        for line in out.splitlines():
            m = rx.search(line)
            if m:
                return m
        time.sleep(0.4)
    return None


def count_log_matches(pattern_key: str) -> int:
    rx = re.compile(LOG_PATTERNS[pattern_key])
    out = read_logcat()
    return sum(1 for line in out.splitlines() if rx.search(line))


def _all_webview_urls() -> list[str]:
    """Returns every URL captured by CountlyWebViewClient since logcat was cleared."""
    rx = re.compile(LOG_PATTERNS["webview_url_loading"])
    urls = []
    for line in read_logcat().splitlines():
        m = rx.search(line)
        if m:
            urls.append(m.group("url"))
    return urls


# The SDK uses `https://countly_action_event/?cly_x_action_event=...` as a pseudo-URL
# to surface in-widget actions (resize_me, close, etc.) through the WebViewClient.
# Filter these out so "external link followed" reflects only real outbound nav.
_INTERNAL_URL_HOST = "countly_action_event"


def _external_https_urls(urls: list[str]) -> list[str]:
    return [u for u in urls if u.startswith("https://") and _INTERNAL_URL_HOST not in u]


def _extract_external_links_from_logcat() -> list[str]:
    """Recovers external URLs the SDK dispatched via `Intent.ACTION_VIEW` from
    logcat. Those URLs never appear directly in `shouldOverrideUrlLoading` —
    they're embedded as `link=...` query params in the internal action URL,
    AND as `value=...` in the `[CLY]_content_interacted` event segmentation.

    Returns deduplicated URLs from both sources, preserving discovery order.
    """
    text = read_logcat()
    found: list[str] = []
    seen: set[str] = set()
    for pattern_key in ("external_link_from_action", "external_link_from_event"):
        rx = re.compile(LOG_PATTERNS[pattern_key])
        for m in rx.finditer(text):
            url = m.group("url")
            # Defensive: skip the SDK's own internal pseudo-host even if it
            # somehow ends up as a `link=` value.
            if _INTERNAL_URL_HOST in url:
                continue
            if url not in seen:
                seen.add(url)
                found.append(url)
    return found


def _is_external_chrome_url(chrome_url: str) -> bool:
    """Returns True if the Chrome omnibox text represents an external (non-SDK)
    URL the user was redirected to. Chrome's omnibox sometimes strips the
    scheme — `google.com/?q=foo` instead of `https://google.com/?q=foo` — so
    we accept both forms. Excluded: empty, internal SDK pseudo-host, and
    chrome-internal URLs (`chrome://...`, `about:...`).
    """
    if not chrome_url:
        return False
    if _INTERNAL_URL_HOST in chrome_url:
        return False
    if chrome_url.startswith(("chrome://", "about:", "data:", "javascript:")):
        return False
    # Either explicit https://host/... OR scheme-stripped host/... with at
    # least one dot in the host part (rules out localhost-ish strings).
    if chrome_url.startswith(("https://", "http://")):
        return True
    host_part = chrome_url.split("/", 1)[0]
    return "." in host_part


# ============================================================================
# UIAutomator helpers
# ============================================================================

def dump_ui() -> Optional[ET.Element]:
    """Snapshots current UI hierarchy via uiautomator. Returns parsed XML root or None."""
    shell("uiautomator dump /sdcard/ui_dump.xml >/dev/null 2>&1", timeout=15)
    out = shell("cat /sdcard/ui_dump.xml", timeout=10)
    if not out or "<hierarchy" not in out:
        return None
    try:
        return ET.fromstring(out)
    except ET.ParseError:
        return None


def parse_bounds(bounds_str: str) -> tuple[int, int, int, int]:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds_str or "")
    if not m:
        return (0, 0, 0, 0)
    return (int(m.group(1)), int(m.group(2)), int(m.group(3)), int(m.group(4)))


def find_node_by_resource_id(root: ET.Element, rid_suffix: str) -> Optional[ET.Element]:
    target = f"{DEMO_PACKAGE}:id/{rid_suffix}"
    for node in root.iter("node"):
        if node.get("resource-id") == target:
            return node
    return None


def find_nodes_by_text_contains(root: ET.Element, hints: list[str],
                                clickable_only: bool = True) -> list[ET.Element]:
    """Returns nodes whose text or content-desc contains any of the hints (case-insensitive)."""
    lowered = [h.lower() for h in hints]
    results = []
    for node in root.iter("node"):
        if clickable_only and node.get("clickable") != "true":
            continue
        text = (node.get("text") or "").lower()
        desc = (node.get("content-desc") or "").lower()
        if any(h in text or h in desc for h in lowered):
            results.append(node)
    return results


def find_nodes_by_text_contains_loose(root: Optional[ET.Element],
                                      hints: list[str]) -> list[ET.Element]:
    """Strict-then-loose two-pass: first try `clickable_only=True`, then fall back
    to `clickable_only=False` if nothing matched.

    Why: WebView accessibility doesn't always propagate `clickable=true` to
    parent containers — e.g., survey-v2 close uses `<div class="close-button">`
    with the click handler on the div, but UIAutomator only sees the inner
    `<i>` icon glyph node which lacks `clickable=true`. The pixel bounds of the
    icon are inside the div, so a tap at those coords still triggers the div's
    click handler — `tap_node` doesn't need a clickable target, just bounds.
    """
    if root is None:
        return []
    strict = find_nodes_by_text_contains(root, hints, clickable_only=True)
    if strict:
        return strict
    return find_nodes_by_text_contains(root, hints, clickable_only=False)


def tap_node(node: ET.Element) -> None:
    x1, y1, x2, y2 = parse_bounds(node.get("bounds", ""))
    tap((x1 + x2) // 2, (y1 + y2) // 2)


# ============================================================================
# Screen recording
# ============================================================================

def screenrecord_start(remote_path: str) -> subprocess.Popen:
    return subprocess.Popen(
        _adb_args(["shell", "screenrecord", "--bit-rate", "4000000", remote_path]),
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )


def screenrecord_stop(proc: subprocess.Popen, remote_path: str,
                      local_path: Path) -> bool:
    """Sends SIGINT to remote screenrecord, pulls the file. Returns True on success."""
    try:
        shell("pkill -SIGINT screenrecord", timeout=5)
        proc.wait(timeout=10)
    except Exception:
        try:
            proc.kill()
        except Exception:
            pass
    time.sleep(1.5)  # let the device finalize the .mp4 container
    pull = adb("pull", remote_path, str(local_path), timeout=30)
    shell(f"rm -f {remote_path}", timeout=5)
    return local_path.exists() and local_path.stat().st_size > 0


# ============================================================================
# Verdict bookkeeping
# ============================================================================

class _HostNotVisibleAbort(Exception):
    """Sentinel raised when the demo's host activity fails to reach the foreground.
    Caught by the per-test `except` so we still hit `finally` (verdict + video saved)
    but skip the rest of the body (it would all just FAIL/SKIP cascade-style)."""
    pass


class _ContentClosedAbort(Exception):
    """Sentinel raised when the content overlay closes mid-test, before we
    explicitly clicked Close. Caught at the outer test level so we record a
    FAIL on the step that caused it, skip remaining content-dependent steps,
    and proceed to the next variant cleanly. Any work already done before the
    close is preserved in the verdict."""
    pass


def assert_content_alive(verdict: dict, baseline_close_count: int,
                         step_label: str) -> None:
    """Raises _ContentClosedAbort if `[content_close]` fired since the baseline.
    Records a FAIL entry naming the step that the overlay didn't survive.

    `baseline_close_count` is the value of `count_log_matches("content_close")`
    captured at test start (before any expected close click). The final
    explicit close click happens AFTER the last guard call, so this never
    false-fires on the legitimate end-of-test close.
    """
    if count_log_matches("content_close") > baseline_close_count:
        record(verdict, f"content_alive_after_{step_label}", "FAIL",
               f"[content_close] fired during '{step_label}' — overlay didn't survive")
        verdict["errors"].append(
            f"overlay closed during '{step_label}'; remaining steps skipped"
        )
        raise _ContentClosedAbort()


def make_verdict(variant: str, kind: str, device_id: str) -> dict:
    return {
        "variant": variant,
        "kind": kind,  # "content" or "feedback"
        "device_id": device_id,
        "started_at": datetime.now().isoformat(timespec="seconds"),
        "ended_at": None,
        "duration_s": 0.0,
        "checklist": {},
        "content_bounds": None,
        "screen_size": "x".join(str(s) for s in screen_size()),
        "fatal_exceptions": 0,
        "incorrect_context_use_violations": 0,
        "errors": [],
    }


def record(verdict: dict, key: str, status: str, detail: str = "") -> None:
    """Mark a checklist item with PASS / FAIL / SKIP and optional detail."""
    verdict["checklist"][key] = {"status": status, "detail": detail}


# ============================================================================
# ContentZone screen — cached coordinates + self-healing navigation
# ============================================================================
#
# Most of the per-variant cost used to be spent re-killing the demo, re-launching
# from scratch, and re-dumping the same ContentZone layout. The screen layout
# never changes between variants on the same screen — we can dump it once per
# sweep and reuse the field/button coords for every variant.

def overlay_window_present() -> bool:
    """Returns True if the demo has a content overlay window currently attached.

    Detection uses `dumpsys window`: ContentOverlayView creates its window with
    `TYPE_APPLICATION` + `PixelFormat.TRANSLUCENT`, while activities themselves
    use `TYPE_BASE_APPLICATION`. So a window owned by the demo with
    `ty=APPLICATION fmt=TRANSLUCENT` IS the overlay. This works even when
    UIAutomator can't see the overlay's close button (iconic X, transient
    state during attach, etc.).
    """
    try:
        out = shell("dumpsys window windows", timeout=10)
    except Exception:
        return False
    in_demo_window = False
    for line in out.splitlines():
        if f"{DEMO_PACKAGE}/" in line and "Window #" in line:
            in_demo_window = True
            continue
        if in_demo_window:
            if "mAttrs=" in line:
                # Note: ty=APPLICATION (not BASE_APPLICATION) + TRANSLUCENT
                # is the overlay's signature.
                if " ty=APPLICATION " in line and "TRANSLUCENT" in line:
                    return True
                in_demo_window = False
    return False


def dismiss_lingering_overlay(max_attempts: int = 3,
                              aggressive: bool = True) -> bool:
    """Dismiss any content overlay left attached from a previous (killed) run.

    `aggressive` (default True): if all dismiss attempts fail, force_stop the
    demo as a last resort. Used at sweep boundaries (between variants /
    between content & feedback phases) where a stuck overlay would cascade
    into the next variant.

    `aggressive=False`: skip the force_stop fallback. Used mid-test when the
    overlay is the ACTIVE test's own widget — force-stopping it would kill
    the test we're currently running. The caller's main close logic (CDP
    selectors, etc.) is responsible for closing the active overlay.

    Why this exists: `ContentOverlayView` is a `TYPE_APPLICATION` window stacked
    on top of whatever activity hosts it. When the runner is interrupted
    mid-test, the overlay stays attached. The activity beneath shows in
    `dumpsys activity` but UIAutomator dumps reveal the OVERLAY's accessibility
    tree, not the activity's — so any subsequent navigation that depends on
    finding host-activity text fails silently.

    Strategy (in order):
      1. UIAutomator: tap a close node whose text/content-desc matches
         WEBVIEW_HINTS["close"]. Works for content widgets that expose proper
         a11y on their close button.
      2. Blind tap at the two common close locations: top-right corner
         (feedback widgets) and bottom-center (fullscreen content widgets).
         Used when the widget's close X is iconic-only (no a11y label) — common
         on NPS/Rating/Survey widgets.
      3. **Last-resort**: `force_stop` the demo. The SDK has no public API to
         dismiss an overlay externally, so a stuck overlay with no reachable
         close button leaves only this option. Logged loudly so it's visible.

    Back-press is NOT used — the SDK forwards BACK to the host activity (see
    ContentOverlayView.dispatchKeyEvent), so on MainActivity that would exit
    the demo entirely.

    Returns True if any dismiss attempt was made, False if no overlay was
    detected at all.
    """
    if not overlay_window_present():
        return False  # fast path: nothing to do

    sw, sh = screen_size()
    blind_targets = [
        (sw - 80, 80),       # top-right (feedback widgets)
        (sw // 2, sh - 100), # bottom-center (full-screen content widgets)
    ]
    blind_idx = 0

    for attempt in range(max_attempts):
        if not overlay_window_present():
            return True  # dismissed on a prior pass
        root = dump_ui()
        close_nodes = (find_nodes_by_text_contains(root, WEBVIEW_HINTS["close"])
                       if root is not None else [])
        if close_nodes:
            print(f"[+] Lingering overlay detected (attempt {attempt + 1}); tapping Close")
            tap_node(close_nodes[0])
        elif blind_idx < len(blind_targets):
            x, y = blind_targets[blind_idx]
            blind_idx += 1
            print(f"[+] Lingering overlay detected (attempt {attempt + 1}); "
                  f"no close-text in dump, blind tap at ({x},{y})")
            tap(x, y)
        else:
            break  # blind targets exhausted; fall through to force_stop
        time.sleep(2.0)  # let detach + activity restore complete

    # Last-resort recovery for STALE overlays only. `aggressive=False` is
    # used mid-test when the overlay is the active test's own widget —
    # force-stopping would kill the test. In that mode we leave the overlay
    # for the main close logic (CDP / a11y / blind tap) to handle.
    if overlay_window_present():
        if aggressive:
            print("[!] Lingering overlay couldn't be dismissed via tap — "
                  "force-stopping demo as last-resort recovery", file=sys.stderr)
            force_stop()
        else:
            print("[!] Lingering overlay couldn't be dismissed via tap — "
                  "non-aggressive mode, leaving for the test's main close "
                  "logic to handle", file=sys.stderr)
    return True


def setup_content_zone() -> Optional[dict]:
    """Navigate the demo to ContentZone, dump the UI once, and cache pixel
    coords for the device-id field, Change-Device-ID button, Enter-Content-Zone
    button, and Exit-Content-Zone button. Returns the coords dict or None if
    any required element wasn't found.

    Calls `force_stop` ONCE at sweep init to clear any stale demo instances
    left in OS recents from a prior killed runner. Between variants we keep
    the demo warm — only this one-time cleanup at sweep start uses
    force-stop. After force-stop, `launcher_intent` brings up a fresh
    MainActivity and we navigate to ContentZone from there.

    Prints which step failed so silent setup failures self-report instead of
    leaving the operator to guess.
    """
    # One-time recents cleanup — kills any lingering demo process so the
    # OS recents list doesn't accumulate dead instances across runs.
    force_stop()
    wake_and_unlock()
    # Clean up any overlay left over from a prior killed run.
    dismiss_lingering_overlay()
    if not open_demo_activity(CONTENT_ZONE_ACTIVITY):
        print(f"[!] setup_content_zone: open_demo_activity({CONTENT_ZONE_ACTIVITY}) "
              f"returned False; top: {top_activity().strip()[:120]}", file=sys.stderr)
        return None
    root = dump_ui()
    if root is None:
        print("[!] setup_content_zone: dump_ui returned None on ContentZone",
              file=sys.stderr)
        return None
    coords: dict = {}
    for key_, rid in (
        ("field", CONTENT_ZONE_DEVICE_ID_FIELD),
        ("change_btn", CONTENT_ZONE_BTN_CHANGE_DEVICE_ID),
        ("enter_btn", CONTENT_ZONE_BTN_ENTER_ZONE),
        ("exit_btn", CONTENT_ZONE_BTN_EXIT_ZONE),
    ):
        node = find_node_by_resource_id(root, rid)
        if node is None:
            print(f"[!] setup_content_zone: resource '{rid}' not in ContentZone "
                  f"UI dump (mapped to '{key_}')", file=sys.stderr)
            return None
        x1, y1, x2, y2 = parse_bounds(node.get("bounds", ""))
        coords[key_] = ((x1 + x2) // 2, (y1 + y2) // 2)
    return coords


def ensure_on_content_zone(aggressive_dismiss: bool = True) -> bool:
    """Returns True with ContentZone on top — navigates from MainActivity if
    needed. Used at the start of each variant so a previous variant's pokes
    or undismissed overlay don't poison the next.

    `aggressive_dismiss=True` (default): force_stop the demo if a stale
    overlay can't be tapped away. Right for variant-start cleanup, where a
    previous variant's stuck overlay would block the new variant's setup.

    `aggressive_dismiss=False`: leave the overlay alone. Right for mid-test
    use (pre-close phase) where the overlay is the active test's own widget;
    force-stopping would kill the live test. The caller's CDP/a11y close
    logic handles the active overlay.

    Two-phase dismiss: BEFORE navigation (to clear an overlay that may be
    covering MainActivity's "Content Zone" link) and AFTER navigation (to
    catch overlays that re-attach during the activity lifecycle callbacks
    fired by `launcher_intent`'s CLEAR_TASK). The SDK's `ContentOverlayView`
    can re-attach asynchronously when the resumed activity changes — without
    the post-nav dismiss, the overlay covers ContentZone and downstream taps
    land on the wrong window.

    `launcher_intent` uses `FLAG_ACTIVITY_CLEAR_TASK`, so any sibling activity
    left on top from pokes is finished and MainActivity comes up fresh —
    no force_stop required.

    Retries up to 3 times: previous variant's lifecycle (HOME / foreground /
    close) can leave the activity stack in a transient state where the first
    attempt misses; a settle + retry usually recovers.
    """
    def _attempt() -> bool:
        dismiss_lingering_overlay(aggressive=aggressive_dismiss)
        if CONTENT_ZONE_ACTIVITY not in top_activity():
            if not open_demo_activity(CONTENT_ZONE_ACTIVITY):
                return False
        # Race window: overlay can re-attach during the navigation we just did.
        dismiss_lingering_overlay(aggressive=aggressive_dismiss)
        return CONTENT_ZONE_ACTIVITY in top_activity()
    return retry_action(_attempt, attempts=3, settle_s=1.0,
                        label="ensure_on_content_zone")


# ============================================================================
# CDP helpers — drive the widget's WebView DOM directly when accessibility-
# tree dumps come up empty. Connect lazily after `content_loaded` so the
# WebView socket is registered.
# ============================================================================

# CSS selectors for buttons in the two widget framework families. The
# content-builder family (sticky/modal/half_modal/fullscreen) uses real
# `<button class="cly-content-builder-button">` with text labels. The
# survey-v2 family (NPS/rating/survey) uses `<div class="close-button">` for
# close + a mix of `<button>` / `<div class="submit-button">` for actions.
#
# Order matters: more specific selectors first. CDP's `click()` returns True
# on the first match, so we try the strongest signal first.
CONTENT_GO_SELECTORS = [
    # The first button (button0) in the content-builder footer is always the
    # action button when buttons.amount >= 1. Use :first-of-type within the
    # footer to disambiguate from button1 (close).
    ".cly-content-builder-sticky-block__footer button:first-of-type",
    ".cly-content-builder-sticky-block__footer .cly-content-builder-button:first-of-type",
    # Modal/half_modal/fullscreen variants use a different block class but
    # the same first-button-is-action convention.
    ".cly-content-builder-card .cly-content-builder-button:first-of-type",
]
CONTENT_CLOSE_SELECTORS = [
    ".cly-content-builder-sticky-block__footer button:nth-of-type(2)",
    ".cly-content-builder-sticky-block__footer .cly-content-builder-button:nth-of-type(2)",
    ".cly-content-builder-card .cly-content-builder-button:nth-of-type(2)",
]
FEEDBACK_CLOSE_SELECTORS = [
    ".close-button",
    "#close-btn",
]


def cdp_try_connect(device: Optional[str] = None,
                    title_substring: str = "") -> Optional[CDP]:
    """Wraps CDP.connect_to_demo with a try/except so the runner stays alive
    when the WebView debugging socket isn't reachable (e.g., on a release
    build, or before the widget has loaded). Returns None on any failure."""
    try:
        return CDP.connect_to_demo(
            package=DEMO_PACKAGE, device=device,
            title_substring=title_substring or None,
        )
    except Exception as e:
        vlog(f"[cdp] connect failed: {type(e).__name__}: {e}")
        return None


def _cdp_close_quietly(cdp: Optional[CDP]) -> None:
    """Close a CDP handle, swallowing any teardown errors. Stale sockets and
    half-closed WebSockets are common after Chrome roundtrips / activity
    restarts, and we don't want cleanup noise to mask test results."""
    if cdp is None:
        return
    try:
        cdp.close()
    except Exception:
        pass


def _cdp_title_for_feedback(feedback_type: str) -> str:
    """CDP page-title hint used by `connect_to_demo` to disambiguate which
    debuggable WebView to attach to. Rating widgets render under the 'Rating'
    title; NPS / Survey both render as 'Survey'."""
    return "Rating" if feedback_type == "rating" else "Survey"


def _collect_external_urls() -> tuple[list[str], list[str], list[str]]:
    """Returns (direct_urls, extracted_urls, all_urls_dedup) from logcat.

    `direct_urls` come from `shouldOverrideUrlLoading` lines whose host is
    external (rare — usually only when a fixture has a plain `<a href>` not
    routed through the action framework).

    `extracted_urls` are recovered from the SDK's internal action URLs and
    `[CLY]_content_interacted` event segmentation — the common path for
    Intent.ACTION_VIEW dispatches.

    `all_urls_dedup` merges both, preserving discovery order (direct first).
    Either source is sufficient evidence of an external redirect.
    """
    direct = _external_https_urls(_all_webview_urls())
    extracted = _extract_external_links_from_logcat()
    merged = direct + [u for u in extracted if u not in direct]
    return direct, extracted, merged


def cdp_click_first(cdp: CDP, selectors: list[str]) -> Optional[str]:
    """Try each selector in order; return the matched selector when click()
    succeeds, or None if nothing matched. Lets the runner record WHICH
    selector worked for diagnostics."""
    for sel in selectors:
        try:
            if cdp.click(sel):
                return sel
        except CDPError as e:
            vlog(f"[cdp] click({sel!r}) error: {e}")
    return None


# ============================================================================
# Lifecycle scenario — exercises the overlay through the full set of state
# transitions that fullscreen-class widgets must survive: rotation while open,
# BACK press, Chrome roundtrip, second rotation, HOME background, foreground,
# and finally an explicit Close click. Used for `fullscreen` content + all
# feedback widgets (NPS, rating, survey) since those cover the full screen
# and routinely survive the same lifecycle in production.
#
# The flow is guarded by `assert_content_alive` after every step. If the
# overlay closes prematurely (i.e., before our final Close click), the guard
# raises `_ContentClosedAbort`, the outer test handler records that step as
# FAIL, and we move on to the next variant. No cascading SKIPs.
# ============================================================================

def run_lifecycle_scenario(verdict: dict, baseline_close_count: int,
                           cdp: Optional[CDP],
                           go_selectors: list[str],
                           close_selectors: list[str],
                           label: str) -> None:
    """Executes the lifecycle scenario. Records each PASS/FAIL onto `verdict`.
    `cdp` may be None — without it, link-click and close-click steps record
    SKIP since coordinate-based blind taps aren't reliable for fullscreen
    overlays (no obvious visible button position).

    Steps (with their checklist keys):
        rotation_landscape         — rotate(1)
        back_press_kept_overlay    — KEYCODE_BACK; overlay must stay alive
        go_link_to_chrome          — CDP click of go selector → Chrome
        return_kept_overlay        — KEYCODE_BACK from Chrome; reattach
        rotation_back_to_portrait  — rotate(0)
        background_kept_overlay    — KEYCODE_HOME; overlay detaches but stays alive
        foreground_kept_overlay    — launcher_intent reattach
        close_button_works         — CDP click close → content_close
    """
    # 1. Rotate to landscape
    rotation(1)
    land_match = wait_for_log("config_changed", TIMEOUTS["config_change"])
    record(verdict, "rotation_landscape",
           "PASS" if land_match else "FAIL",
           "" if land_match else "no orientation-changed log")
    assert_content_alive(verdict, baseline_close_count, "rotation_landscape")

    # 2. BACK press — exercises the host activity changing under the overlay
    key("KEYCODE_BACK")
    time.sleep(1.0)
    assert_content_alive(verdict, baseline_close_count, "back_press")
    record(verdict, "back_press_kept_overlay", "PASS",
           "overlay survived BACK; top: " + top_activity().strip()[:80])

    # 3. CDP click Go → Chrome opens
    go_sel: Optional[str] = None
    if cdp is not None:
        try:
            go_sel = cdp_click_first(cdp, go_selectors)
        except CDPError as e:
            vlog(f"[{label}] CDP go click error: {e}")
    if go_sel is None:
        record(verdict, "go_link_to_chrome", "FAIL",
               "no Go selector matched (CDP unavailable or DOM mismatch)")
        record(verdict, "go_url_external_https", "FAIL",
               "no Go selector matched; nothing to dispatch")
    else:
        time.sleep(2.0)
        chrome_open = is_chrome_on_top()
        record(verdict, "go_link_to_chrome",
               "PASS" if chrome_open else "FAIL",
               f"CDP click {go_sel}; top: {top_activity().strip()[:80]}")

        # External URL evidence — same two-source check as the standard flow.
        # The Go-click action URL embeds the external URL as `link=...`, and
        # the `[CLY]_content_interacted` event records it as `value=...`. We
        # don't require a direct `https://` URL in `shouldOverrideUrlLoading`
        # because the SDK's linkAction fires Intent.ACTION_VIEW directly.
        _direct, _extracted, external_all = _collect_external_urls()
        if external_all:
            record(verdict, "go_url_external_https", "PASS",
                   f"external URL: {external_all[-1][:100]}")
        else:
            record(verdict, "go_url_external_https", "FAIL",
                   "Go click fired but no external URL recovered "
                   "(no link= / value= match in logcat)")

        # 4. Return from Chrome
        key("KEYCODE_BACK")
        time.sleep(2.0)
    assert_content_alive(verdict, baseline_close_count, "return_from_chrome")
    record(verdict, "return_kept_overlay", "PASS",
           "overlay survived Chrome roundtrip; top: " + top_activity().strip()[:80])

    # 5. Rotate back to portrait
    rotation(0)
    port_match = wait_for_log("config_changed", TIMEOUTS["config_change"])
    record(verdict, "rotation_back_to_portrait",
           "PASS" if port_match else "FAIL",
           "" if port_match else "no orientation-changed log")
    assert_content_alive(verdict, baseline_close_count, "rotation_back_to_portrait")

    # 6. HOME — background the app entirely. SDK calls onActivityStopped(count=0)
    #    → detachFromWindow. The overlay instance must remain alive so it can
    #    reattach when the app is foregrounded again.
    key("KEYCODE_HOME")
    time.sleep(1.5)
    assert_content_alive(verdict, baseline_close_count, "background_app")
    record(verdict, "background_kept_overlay", "PASS",
           "overlay still alive after HOME; top: " + top_activity().strip()[:80])

    # 7. Foreground via launcher (CLEAR_TASK flag pops back to MainActivity).
    foreground_ok = launcher_intent()
    time.sleep(1.5)
    assert_content_alive(verdict, baseline_close_count, "foreground_app")
    record(verdict, "foreground_kept_overlay",
           "PASS" if foreground_ok else "FAIL",
           "overlay reattached after foregrounding" if foreground_ok else
           "launcher_intent failed; can't verify reattach")

    # 8. Click Close — terminal step, content_close is now expected.
    # The CDP socket opened at step 0 is now stale: the lifecycle did Chrome
    # roundtrip + HOME background + foreground, killing the WebView's debug
    # socket along the way. Reconnect against the now-current WebView.
    _cdp_close_quietly(cdp)
    fresh_cdp = cdp_try_connect(
        device=_DEVICE_SERIAL,
        title_substring="Survey" if "feedback" in label else "Content Builder",
    )

    # 3-attempt retry on each close strategy. CDP first; if it fails or the
    # connection couldn't be established, fall back to BACK key.
    def _try_cdp_close():
        if fresh_cdp is None:
            return False
        sel = cdp_click_first(fresh_cdp, close_selectors)
        if sel is None:
            return False
        return wait_for_log("content_close", 5.0) is not None

    def _try_back_press_close():
        key("KEYCODE_BACK")
        return wait_for_log("content_close", 3.0) is not None

    _via = None
    if retry_action(_try_cdp_close, attempts=3, settle_s=0.7, label="lifecycle_close_cdp"):
        _via = "CDP click"
    elif retry_action(_try_back_press_close, attempts=3, settle_s=0.7, label="lifecycle_close_back"):
        _via = "BACK key"

    record(verdict, "close_button_works",
           "PASS" if _via else "FAIL",
           f"closed via {_via}" if _via else
           "CDP reconnect + BACK both exhausted across 3 retries each")

    _cdp_close_quietly(fresh_cdp)


# ============================================================================
# Per-variant content test
# ============================================================================

def run_content_test(variant: str, coords: dict, run_id: str, seq: int,
                     output_dir: Path) -> dict:
    test_name = f"content_{variant}"
    test_dir = output_dir / test_name
    test_dir.mkdir(parents=True, exist_ok=True)

    device_id = f"{variant}_{run_id}_{seq:02d}"
    verdict = make_verdict(variant, "content", device_id)
    started_mono = time.monotonic()

    # Fresh logcat baseline. NO force_stop — we keep the demo warm across
    # variants, only ensuring ContentZone is on top. Cached coords from
    # setup_content_zone() are reused for the field/buttons.
    clear_logcat()
    wake_and_unlock()
    vlog(f"[content/{variant}] device_id={device_id}")

    # Start screen recording (background, max 180s)
    remote_mp4 = f"/sdcard/test_{test_name}.mp4"
    rec = screenrecord_start(remote_mp4)
    # Pre-declare so the finally block can clean up regardless of which step
    # failed. CDP is opt-in (None when WebView debugging isn't reachable).
    cdp: Optional[CDP] = None
    try:
        # 1. Self-heal: ensure ContentZone is on top (previous variant's pokes
        # may have left a sibling activity foregrounded). Hard-resets only when
        # in-app navigation can't recover.
        host_visible = ensure_on_content_zone()
        record(verdict, "host_activity_visible",
               "PASS" if host_visible else "FAIL",
               "" if host_visible else f"{CONTENT_ZONE_ACTIVITY} never reached foreground")
        if not host_visible:
            verdict["errors"].append(
                f"{CONTENT_ZONE_ACTIVITY} did not reach foreground; downstream steps skipped"
            )
            raise _HostNotVisibleAbort()
        vlog(f"[content/{variant}] {CONTENT_ZONE_ACTIVITY} on top (cached coords)")
        record(verdict, "ui_dump_available", "PASS", "using cached ContentZone coords")

        # 2. Reset zone state from the previous variant: tap Exit, then change
        # device id and re-enter. Exit is harmless if no zone is active.
        ex_x, ex_y = coords["exit_btn"]
        tap(ex_x, ex_y)

        # 3. Type new device id
        fx, fy = coords["field"]
        vlog(f"[content/{variant}] type device_id='{device_id}' at field ({fx},{fy})")
        long_press_clear(fx, fy)
        input_text(device_id)
        record(verdict, "device_id_field", "PASS", f"typed at ({fx},{fy})")

        # 4. Apply new id
        cx, cy = coords["change_btn"]
        vlog(f"[content/{variant}] tap 'Change Device ID' at ({cx},{cy})")
        tap(cx, cy)
        setid_match = wait_for_log("device_id_set", TIMEOUTS["device_id_set"])
        record(verdict, "device_id_change_tap",
               "PASS" if setid_match else "WARN",
               "[ModuleDeviceId].setID seen" if setid_match else
               "tap registered but no setID log within timeout")

        # 5. Enter zone
        ex_x2, ex_y2 = coords["enter_btn"]
        vlog(f"[content/{variant}] tap 'Enter Content Zone' at ({ex_x2},{ex_y2})")
        tap(ex_x2, ex_y2)
        record(verdict, "enter_zone_tap", "PASS", f"tapped at ({ex_x2},{ex_y2})")

        # 3. Wait for content overlay attach + page loaded
        attach_match = wait_for_log("content_attached", TIMEOUTS["content_attach"])
        if attach_match:
            w, h = int(attach_match.group("width")), int(attach_match.group("height"))
            verdict["content_bounds"] = {"width": w, "height": h}
            record(verdict, "content_attached", "PASS",
                   f"size: {w}x{h}, host: {attach_match.group('host')}")
        else:
            record(verdict, "content_attached", "FAIL", "no onWindowAttached log")

        load_match = wait_for_log("content_loaded", TIMEOUTS["content_load"])
        record(verdict, "content_loaded",
               "PASS" if load_match else "FAIL",
               "" if load_match else "no [page loaded successfully] log")

        # 3a. Validate the rendered content contains the variant prefix (server-side
        # fixture echoes the prefix as visible text inside the widget). This catches
        # cases where the server routed to the wrong content type for this device id.
        if load_match:
            time.sleep(0.8)  # give WebView a tick to expose accessibility text
            wv_dump = dump_ui()
            prefix_found = (
                wv_dump is not None
                and bool(find_nodes_by_text_contains(
                    wv_dump, [variant], clickable_only=False
                ))
            )
            record(verdict, "content_shows_variant_prefix",
                   "PASS" if prefix_found else "WARN",
                   f"variant prefix '{variant}' "
                   f"{'found' if prefix_found else 'not found'} in WebView text "
                   f"(WebView a11y may be limited on some widgets)")
            if VERBOSE and not prefix_found and wv_dump is not None:
                # Surface a sample of the texts we did see, helps debugging server fixtures.
                sample_texts = []
                for n in wv_dump.iter("node"):
                    t = (n.get("text") or "").strip()
                    if t:
                        sample_texts.append(t)
                vlog(f"[content/{variant}] WebView text sample: {sample_texts[:6]}")

        # Dispatch: fullscreen variants run the lifecycle scenario (rotate /
        # back / Chrome / rotate / HOME / foreground / close), exercising the
        # overlay through the full set of state transitions a fullscreen
        # widget must survive in production. Other variants (sticky/modal/
        # half_modal) keep the existing flow.
        if variant in FULLSCREEN_VARIANTS:
            baseline_close_count = count_log_matches("content_close")
            cdp = cdp_try_connect(device=_DEVICE_SERIAL,
                                  title_substring="Content Builder")
            run_lifecycle_scenario(
                verdict, baseline_close_count, cdp,
                go_selectors=CONTENT_GO_SELECTORS,
                close_selectors=CONTENT_CLOSE_SELECTORS,
                label=f"content/{variant}",
            )
            verdict["fatal_exceptions"] = count_log_matches("fatal_exception")
            verdict["incorrect_context_use_violations"] = count_log_matches("incorrect_context_use")
            record(verdict, "no_fatal_exceptions",
                   "PASS" if verdict["fatal_exceptions"] == 0 else "FAIL",
                   f"{verdict['fatal_exceptions']} FATAL EXCEPTIONS")
            return verdict  # finally block in caller still runs (cleanup, write verdict)

        # 4. Rotate landscape → portrait round-trip. The previous KEYCODE_BACK
        # between rotations was vestigial — it navigated ContentZone →
        # MainActivity, breaking the test by leaving subsequent steps to act
        # on a wrong host. Lifecycle-relevant BACK testing happens in the
        # fullscreen lifecycle scenario; here we just exercise rotation.
        rotation(1)
        record(verdict, "rotation_landscape",
               "PASS" if wait_for_log("config_changed", TIMEOUTS["config_change"]) else "FAIL")
        rotation(0)
        time.sleep(1.0)  # let portrait re-layout settle

        # 5. Passthrough test — coordinates derived from variant anchor + bounds.
        # `_top` variants put content at the screen top, `_bottom` at the bottom;
        # everything else (modal / fullscreen / half_modal_*) is centered or covers
        # the whole screen. Probing dead-center for sticky banners would land OUTSIDE
        # them — that's how this assertion was generating false FAILs before.
        if variant in FULLSCREEN_VARIANTS or verdict["content_bounds"] is None:
            reason = ("fullscreen variant has no outside region"
                      if variant in FULLSCREEN_VARIANTS
                      else "content didn't attach (no bounds captured) — see content_attached")
            record(verdict, "background_tap_passthrough", "SKIP", reason)
            record(verdict, "inside_tap_no_passthrough", "SKIP", reason)
        else:
            sw, sh = screen_size()
            ch = verdict["content_bounds"]["height"]

            if "top" in variant:
                # Content occupies a strip at the top. Inside ≈ middle of that strip.
                inside_x, inside_y = sw // 2, max(ch // 2, 80)
                outside_x, outside_y = sw // 2, sh - sh // 8  # near bottom
            elif "bottom" in variant:
                inside_x, inside_y = sw // 2, sh - max(ch // 2, 80)
                outside_x, outside_y = sw // 2, sh // 8       # near top
            else:
                # modal / half_modal_* / anything else covering most of the screen.
                # Center is reliably inside; corners are reliably outside if anything is.
                inside_x, inside_y = sw // 2, sh // 2
                outside_x, outside_y = sw // 4, sh // 8

            vlog(f"[content/{variant}] passthrough probes: "
                 f"outside=({outside_x},{outside_y}) inside=({inside_x},{inside_y})")

            # Use `host_button_event` (excludes [CLY]_view / [CLY]_orientation
            # / [CLY]_content_*) so rotation-induced auto-events don't false-
            # trigger the passthrough check.
            events_before = count_log_matches("host_button_event")
            tap(outside_x, outside_y)
            time.sleep(0.6)
            outside_event = count_log_matches("host_button_event") > events_before
            record(verdict, "background_tap_passthrough",
                   "PASS" if outside_event else "WARN",
                   "background activity registered tap" if outside_event else
                   "no event from outside-content tap (host may not have a button there)")

            events_before = count_log_matches("host_button_event")
            tap(inside_x, inside_y)
            time.sleep(0.6)
            inside_event = count_log_matches("host_button_event") > events_before
            record(verdict, "inside_tap_no_passthrough",
                   "PASS" if not inside_event else "FAIL",
                   "no background event from inside tap" if not inside_event else
                   "background registered an event from a tap inside content")

        # 9. WebView interactions — three-tier strategy:
        #   Tier 1 (CDP): connect to the WebView's DevTools, query DOM by CSS
        #     selector, fire a real DOM click. Bypasses accessibility entirely
        #     and works regardless of whether the widget exposes a11y nodes.
        #   Tier 2 (a11y): UIAutomator dump + loose-find. Catches widgets where
        #     CDP isn't available (release builds with WebView debugging off).
        #   Tier 3 (blind tap): pixel coords based on variant + content_bounds.
        #     Last-resort fallback for when both CDP and a11y come up empty.
        # Each tier captures the close handle for later — the widget doesn't
        # re-render between Go → Chrome → back, so the close target stays valid.
        cdp = cdp_try_connect(device=_DEVICE_SERIAL, title_substring="Content Builder")
        cdp_used = False  # whether CDP fired the Go-link click successfully
        tap_method = None
        close_via_cdp_selector: Optional[str] = None
        close_nodes: list = []

        if cdp is not None:
            try:
                # Snapshot the buttons CDP can see — useful debug context if a
                # later assertion fails.
                btns = cdp.query_buttons()
                vlog(f"[content/{variant}] CDP sees {len(btns)} buttons")
                go_sel = cdp_click_first(cdp, CONTENT_GO_SELECTORS)
                if go_sel is not None:
                    cdp_used = True
                    tap_method = f"CDP click {go_sel}"
                    # Don't click close yet — we still need to verify Chrome
                    # opens. Just remember the selector for the close phase.
                    close_via_cdp_selector = next(
                        (s for s in CONTENT_CLOSE_SELECTORS
                         if cdp.run_js(f"!!document.querySelector({json.dumps(s)})")),
                        None,
                    )
            except CDPError as e:
                vlog(f"[content/{variant}] CDP error: {e}")

        if not cdp_used:
            # Tier 2: accessibility dump + loose find
            wv_root = dump_ui()
            go_nodes = find_nodes_by_text_contains_loose(wv_root, WEBVIEW_HINTS["go"])
            close_nodes = find_nodes_by_text_contains_loose(wv_root, WEBVIEW_HINTS["close"])
            if go_nodes:
                tap_method = "accessibility tap"
                tap_node(go_nodes[0])
            else:
                # Tier 3: blind tap targeting the LEFT button of the side-by-side
                # bottom row.
                if verdict["content_bounds"]:
                    sw, sh = screen_size()
                    cw = verdict["content_bounds"]["width"]
                    ch = verdict["content_bounds"]["height"]
                    if "top" in variant:
                        blind_y = ch - 60
                    elif "bottom" in variant:
                        blind_y = sh - 60
                    else:
                        blind_y = min(ch, sh) - 200
                    widget_cx = sw // 2
                    blind_x = widget_cx - cw // 4
                    vlog(f"[content/{variant}] go-link blind tap (bottom-left "
                         f"button) at ({blind_x},{blind_y})")
                    tap(blind_x, blind_y)
                    tap_method = f"blind tap at ({blind_x},{blind_y})"
                else:
                    tap_method = "skipped (no content bounds)"
        time.sleep(2.0)

        chrome_open = is_chrome_on_top()
        record(verdict, "go_link_to_chrome",
               "PASS" if chrome_open else "FAIL",
               f"method: {tap_method}; top: {top_activity().strip()[:80]}")

        # External-https check: two evidence sources, either one is sufficient.
        #   (a) `shouldOverrideUrlLoading` URLs filtered to non-internal hosts —
        #       finds direct external navs (rare, only when fixture has plain
        #       `<a href>` that isn't routed via the action framework).
        #   (b) `link=https://...` and `value=https://...` extractions from
        #       internal action URLs / content_interacted event logs — recovers
        #       URLs dispatched via Intent.ACTION_VIEW (the common path).
        # Either path proves the SDK saw and dispatched a real outbound URL.
        all_urls = _all_webview_urls()
        external_direct, external_extracted, external_all = _collect_external_urls()
        if external_all:
            sources = []
            if external_direct:
                sources.append(f"{len(external_direct)} direct nav")
            if external_extracted:
                sources.append(f"{len(external_extracted)} via link=/value=")
            record(verdict, "go_url_external_https", "PASS",
                   f"{', '.join(sources)}; latest: {external_all[-1][:100]}")
        elif all_urls:
            record(verdict, "go_url_external_https", "FAIL",
                   f"saw {len(all_urls)} URL nav(s), no external https in any "
                   f"source; latest: {all_urls[-1][:100]}")
        else:
            record(verdict, "go_url_external_https", "FAIL",
                   "no [shouldOverrideUrlLoading] logs anywhere in test — "
                   "widget didn't navigate (no Go link OR taps missed it)")

        # Chrome address bar verification — looks for an external https URL exposed
        # by the omnibox. We don't require any specific host; presence of a non-internal
        # https URL proves the OS-level redirect happened end-to-end.
        if chrome_open:
            chrome_url = read_chrome_url_bar()
            if chrome_url is None:
                record(verdict, "chrome_url_external_https", "FAIL",
                       "Chrome opened but URL bar not exposed by UIAutomator")
            else:
                is_external = _is_external_chrome_url(chrome_url)
                record(verdict, "chrome_url_external_https",
                       "PASS" if is_external else "FAIL",
                       f"Chrome address bar: {chrome_url}")
            key("KEYCODE_BACK")
            time.sleep(1.5)
        else:
            record(verdict, "chrome_url_external_https", "FAIL",
                   "Chrome wasn't foregrounded — Go-link tap didn't trigger external redirect")

        # 9. Pokes BEFORE close — interact with other demo activities while
        #    the overlay is still up. This exercises cross-activity overlay
        #    survival (the overlay should reattach to each new resumed
        #    activity). For sticky variants the overlay covers a strip and
        #    the poke buttons are reachable; for modal/half_modal the overlay
        #    covers most of the screen and pokes may fail to register taps
        #    on the host buttons — that's expected modal behavior, recorded
        #    as evidence rather than treated as an error.
        baseline_close_count = count_log_matches("content_close")
        events_before = count_log_matches("event_recorded")
        for poke in POKE_ACTIVITIES:
            if not open_demo_activity(poke["activity"]):
                vlog(f"[content/{variant}] poke skipped: {poke['activity']} did not foreground")
                continue
            # Guard: if navigating to the poke activity caused the overlay
            # to close, that's a real bug. Record FAIL and bail.
            assert_content_alive(verdict, baseline_close_count,
                                 f"poke_nav_{poke['activity']}")
            poke_root = dump_ui()
            if poke_root is None:
                vlog(f"[content/{variant}] poke {poke['activity']}: ui dump failed")
                continue
            buttons = find_nodes_by_text_contains(poke_root, poke["button_text_hints"])
            vlog(f"[content/{variant}] poke {poke['activity']}: {len(buttons)} matching buttons")
            for btn in buttons[: poke["max_taps"]]:
                label = btn.get("text") or btn.get("content-desc") or "?"
                vlog(f"[content/{variant}]   tap '{label}'")
                tap_node(btn)
                time.sleep(0.7)
            # Guard: tapping a poke button shouldn't close the overlay either.
            assert_content_alive(verdict, baseline_close_count,
                                 f"poke_taps_{poke['activity']}")
        events_after = count_log_matches("event_recorded")
        record(verdict, "events_recorded",
               "PASS" if events_after > events_before else "WARN",
               f"recorded {events_after - events_before} events")

        # 10. Close LAST — return to ContentZone first so the close button
        #     coords / DOM selector are valid for the visible overlay state.
        #     `aggressive_dismiss=False`: the overlay here is the active
        #     test's own widget (sticky banner from steps above). We don't
        #     want force_stop killing it; the close-strategy block right
        #     after this handles the active overlay via CDP/a11y/blind tap.
        ensure_on_content_zone(aggressive_dismiss=False)
        time.sleep(0.8)

        # The CDP WebSocket opened earlier in this test has been alive across
        # 6+ activity transitions (Chrome roundtrip + 3 poke activities + 2
        # launcher_intents). The WebView's debug socket changes PID on most
        # of those, so the original socket is dead. Close the stale handle
        # and connect fresh against the now-current WebView before clicking.
        _cdp_close_quietly(cdp)
        cdp = cdp_try_connect(device=_DEVICE_SERIAL,
                              title_substring="Content Builder")

        # Same three-tier strategy, each wrapped with a 3-attempt retry. The
        # `retry_action` helper re-invokes click_fn on each attempt; once a
        # close fires the predicate succeeds and we exit early — so a
        # successful first click never spawns redundant clicks.
        def _try_cdp_close():
            if cdp is None or close_via_cdp_selector is None:
                return False
            try:
                cdp.click(close_via_cdp_selector)
            except CDPError:
                return False
            return wait_for_log("content_close", 5.0) is not None

        def _try_a11y_close():
            if not close_nodes:
                return False
            tap_node(close_nodes[0])
            return wait_for_log("content_close", 5.0) is not None

        def _try_blind_tap_close():
            if not verdict["content_bounds"]:
                return False
            sw, sh = screen_size()
            cw = verdict["content_bounds"]["width"]
            ch = verdict["content_bounds"]["height"]
            if "top" in variant:
                close_y = ch - 60
            elif "bottom" in variant:
                close_y = sh - 60
            else:
                close_y = min(ch, sh) - 200
            close_x = sw // 2 + cw // 4
            vlog(f"[content/{variant}] close blind tap (bottom-right button) "
                 f"at ({close_x},{close_y})")
            tap(close_x, close_y)
            return wait_for_log("content_close", 5.0) is not None

        def _try_back_press_close():
            key("KEYCODE_BACK")
            return wait_for_log("content_close", 3.0) is not None

        # Order: CDP → a11y → blind tap → BACK. Each tier gets up to 3 tries;
        # we move to the next tier only after exhausting the current one.
        # `_via` records which strategy succeeded for the verdict detail.
        _via = None
        if retry_action(_try_cdp_close, attempts=3, settle_s=0.7, label="close_cdp"):
            _via = f"CDP click {close_via_cdp_selector}"
        elif retry_action(_try_a11y_close, attempts=3, settle_s=0.7, label="close_a11y"):
            _via = "a11y tap on close node"
        elif retry_action(_try_blind_tap_close, attempts=3, settle_s=0.7, label="close_blind"):
            _via = "blind tap on bottom-right button"
        elif retry_action(_try_back_press_close, attempts=3, settle_s=0.7, label="close_back"):
            _via = "BACK key"

        record(verdict, "close_button_works",
               "PASS" if _via else "FAIL",
               f"closed via {_via}" if _via else
               "all close strategies (CDP / a11y / blind tap / BACK) exhausted")

        # 11. Error counters from the full test window
        verdict["fatal_exceptions"] = count_log_matches("fatal_exception")
        verdict["incorrect_context_use_violations"] = count_log_matches("incorrect_context_use")
        record(verdict, "no_fatal_exceptions",
               "PASS" if verdict["fatal_exceptions"] == 0 else "FAIL",
               f"{verdict['fatal_exceptions']} FATAL EXCEPTIONS")

    except _HostNotVisibleAbort:
        # Already recorded a FAIL for host_activity_visible; just bail to finally.
        pass
    except _ContentClosedAbort:
        # Already recorded a FAIL for the step that caused the close; capture
        # final error counters before going to finally. Lets the next variant
        # start clean.
        verdict["fatal_exceptions"] = count_log_matches("fatal_exception")
        verdict["incorrect_context_use_violations"] = count_log_matches("incorrect_context_use")
    except Exception as e:
        verdict["errors"].append(f"runner exception: {type(e).__name__}: {e}")
    finally:
        # Stop screenrecord, pull file, save logcat
        try:
            local_mp4 = test_dir / "recording.mp4"
            screenrecord_stop(rec, remote_mp4, local_mp4)
        except Exception as e:
            verdict["errors"].append(f"screenrecord stop failed: {e}")

        # Release CDP WebSocket + adb forward so the next test starts with a
        # fresh socket name (the WebView's PID changes per attach, and stale
        # forwards make `list_pages` hit the wrong process).
        _cdp_close_quietly(cdp)
        try:
            remove_forward(device=_DEVICE_SERIAL)
        except Exception:
            pass

        (test_dir / "logcat.txt").write_text(read_logcat())
        verdict["ended_at"] = datetime.now().isoformat(timespec="seconds")
        verdict["duration_s"] = round(time.monotonic() - started_mono, 2)
        (test_dir / "verdict.json").write_text(json.dumps(verdict, indent=2))

    return verdict


# ============================================================================
# Per-feedback-widget test
#
# Mirrors the content test's main checks: present widget, attach/load, rotate,
# tap a Terms/Conditions/Privacy-style link to validate external https flow,
# and verify the X close button works. Skips the variant-prefix check (feedback
# widgets aren't variant-routed) and passthrough probes (the user can't tap
# the host while a feedback widget is up — that's by design).
# ============================================================================


# ============================================================================
# Per-widget feedback scenarios — type-specific flows
# ============================================================================
#
# Each widget has its own DOM structure and required interactions:
#   - survey: pick a radio option → consent → Submit
#   - nps:    pick a 0-10 rating → Next → comment textarea → consent → Submit
#   - rating: pick an emoji → "Add comment" + textarea → "Contact me" + email
#             → consent → Submit
#
# The shared front-half (rotate / BACK / rotate-back / Terms-link / Privacy-
# link / HOME / foreground) is identical across all three. Extract it into
# `_run_feedback_lifecycle_phase` so each scenario only needs to encode its
# unique form-filling logic.
#
# All three end with Submit triggering an auto-close (`content_close` log
# fires within 5s). A second pass with a fresh device_id then re-presents
# the widget and clicks the X close button — verifies the close path
# independently from the submit path.
#
# Rationale for retry-everywhere: the user's directive — every action with
# a verifiable post-condition gets up to 3 attempts via `retry_action`. Form
# clicks (radio/emoji/checkbox) verify by querying selector state via CDP;
# Submit verifies via `[content_close]` log; Terms/Privacy verify by
# `is_chrome_on_top()` AND scanning extracted URLs for the expected hint.

# Random per-test choices keep the suite from always exercising the same
# index — surfaces issues that depend on element ordering.
def _random_nps_rating() -> int:
    return random.randint(0, 10)


def _random_rating_emoji() -> int:
    return random.randint(1, 5)


def _random_survey_radio_index() -> int:
    # Survey templates can have variable option counts; the user described 2
    # ("yes" / "no") but the static template renders generic radio-button-N.
    # Index 0 is always present; 1 covers the second option when visible.
    return random.randint(0, 1)


def _random_lorem() -> str:
    return random.choice(LOREM_TEXT_POOL)


def _click_link_and_verify_chrome(cdp_holder: list, selector: str,
                                   expected_url_hint: str,
                                   widget_label: str, link_label: str) -> tuple[bool, bool, Optional[str]]:
    """Clicks an in-widget link via CDP, verifies Chrome foregrounds, captures
    the actual URL via the omnibox, then KEYCODE_BACK to return.

    Takes the CDP as a single-element list (`cdp_holder`) so we can reassign
    it after the Chrome roundtrip — the WebView's debug socket can become
    stale during the activity-stop/start cycle Chrome triggers, and a fresh
    connection is needed for subsequent CDP calls.

    Returns (chrome_opened, url_matched_hint, omnibox_url). The caller
    records two separate verdict items so a partial pass (Chrome opened with
    wrong URL) is distinguishable from a full pass.
    """
    cdp = cdp_holder[0]
    if cdp is None:
        return False, False, None

    def _attempt() -> bool:
        try:
            return cdp.click(selector)
        except CDPError:
            return False
    clicked = retry_action(_attempt, attempts=3, settle_s=0.5,
                           label=f"{widget_label}_{link_label}_click")
    if not clicked:
        return False, False, None
    time.sleep(2.0)
    chrome_open = is_chrome_on_top()
    omnibox = read_chrome_url_bar() if chrome_open else None
    # Case-insensitive substring match: Chrome's omnibox sometimes preserves
    # the host's original casing (e.g. `privacyPolicy.com`) while we expect
    # the canonical lowercase. Normalize both sides.
    url_matched = bool(omnibox and expected_url_hint.lower() in omnibox.lower())

    # Return to demo. KEYCODE_BACK from Chrome → previous app (the demo).
    key("KEYCODE_BACK")
    time.sleep(1.5)

    # Reconnect CDP — the Chrome roundtrip caused activity stop/start, which
    # can detach the WebView's debug socket. The next CDP call (e.g., Privacy
    # click after Terms click) needs a fresh connection.
    _cdp_close_quietly(cdp)
    cdp_holder[0] = cdp_try_connect(
        device=_DEVICE_SERIAL,
        title_substring=_cdp_title_for_feedback(widget_label),
    )
    return chrome_open, url_matched, omnibox


def _run_feedback_lifecycle_phase(verdict: dict, cdp: Optional[CDP],
                                   baseline_close_count: int,
                                   widget_label: str,
                                   terms_selector: str,
                                   privacy_selector: str,
                                   pre_links_callback=None) -> Optional[CDP]:
    """Shared steps that run on every feedback widget BEFORE its type-specific
    form interactions:
      1. Verify "Widget Automation Test" text is visible (load confirmation).
      2. Rotate to landscape.
      3. KEYCODE_BACK (overlay must survive — host activity changes).
      4. Rotate back to portrait.
      5. Click Terms link → verify Chrome opened with termsandconditions URL.
      6. Click Privacy link → verify Chrome opened with privacypolicy URL.
      7. KEYCODE_HOME (background app).
      8. Foreground via launcher_intent (overlay reattaches).

    Each step records its own verdict item. Premature close at any step
    raises `_ContentClosedAbort` via `assert_content_alive` — the outer
    test handler catches it and proceeds to cleanup.
    """
    # 1. Load confirmation. WARN-not-FAIL when the marker isn't found —
    # server-side widget fixtures get rotated/renamed independently of the
    # demo HTML, so a missing marker is a fixture-state observation, not a
    # SDK bug. `widget_loaded` (logcat-based) is the authoritative load
    # signal; this DOM check is supplementary.
    if cdp is not None:
        present = cdp.text_present_anywhere(WIDGET_AUTOMATION_TEXT)
        record(verdict, "widget_automation_text_visible",
               "PASS" if present else "WARN",
               f"'{WIDGET_AUTOMATION_TEXT}' found in WebView DOM" if present else
               f"'{WIDGET_AUTOMATION_TEXT}' not found — server may have "
               f"renamed widget; widget_loaded already confirmed render")
    else:
        record(verdict, "widget_automation_text_visible", "WARN",
               "CDP unavailable; relying on widget_loaded for load signal")

    # 2. Rotate landscape
    rotation(1)
    land_match = wait_for_log("config_changed", TIMEOUTS["config_change"])
    record(verdict, "rotation_landscape",
           "PASS" if land_match else "FAIL",
           "" if land_match else "no orientation-changed log")
    assert_content_alive(verdict, baseline_close_count, "rotation_landscape")

    # 3. BACK press — overlay must survive even though the host activity
    #    changes (BACK on Feedback activity navigates to MainActivity).
    key("KEYCODE_BACK")
    time.sleep(1.0)
    assert_content_alive(verdict, baseline_close_count, "back_press")
    record(verdict, "back_press_kept_overlay", "PASS",
           "overlay survived BACK; top: " + top_activity().strip()[:80])

    # 4. Rotate back to portrait
    rotation(0)
    port_match = wait_for_log("config_changed", TIMEOUTS["config_change"])
    record(verdict, "rotation_back_to_portrait",
           "PASS" if port_match else "FAIL",
           "" if port_match else "no orientation-changed log")
    assert_content_alive(verdict, baseline_close_count, "rotation_back_to_portrait")

    # CDP holder: `_click_link_and_verify_chrome` reassigns cdp_holder[0]
    # after each Chrome roundtrip with a freshly-reconnected CDP, since the
    # WebView's debug socket can detach during the activity-stop the Chrome
    # foreground triggers.
    cdp_holder = [cdp]

    # 4b. Pre-links callback — used by NPS to navigate from page 1 (rating
    #     buttons) to page 2 (where Terms/Privacy links and the comment
    #     textarea live). Survey and rating expose links on page 1, so they
    #     skip this hook.
    if pre_links_callback is not None and cdp_holder[0] is not None:
        try:
            pre_links_callback(cdp_holder[0], verdict)
        except Exception as e:
            verdict["errors"].append(f"pre_links_callback failed: {e}")
        time.sleep(1.0)
        assert_content_alive(verdict, baseline_close_count, "pre_links_step")

    # 5. Terms link — should open Chrome with `termsandconditions` URL.
    if cdp_holder[0] is not None:
        chrome_ok, url_ok, omnibox = _click_link_and_verify_chrome(
            cdp_holder, terms_selector, "termsandconditions",
            widget_label, "terms")
        record(verdict, "terms_link_to_chrome",
               "PASS" if chrome_ok else "FAIL",
               f"Chrome on top after click; omnibox: {omnibox}" if chrome_ok else
               "Chrome did not foreground after Terms click")
        record(verdict, "terms_url_external_https",
               "PASS" if url_ok else "FAIL",
               f"omnibox contains 'termsandconditions': {omnibox}" if url_ok else
               f"omnibox missing termsandconditions: {omnibox}")
    else:
        record(verdict, "terms_link_to_chrome", "FAIL", "CDP unavailable")
        record(verdict, "terms_url_external_https", "FAIL", "CDP unavailable")
    assert_content_alive(verdict, baseline_close_count, "terms_link")

    # 6. Privacy link — same pattern with `privacypolicy` URL. Uses the
    #    reconnected CDP from cdp_holder[0].
    if cdp_holder[0] is not None:
        chrome_ok, url_ok, omnibox = _click_link_and_verify_chrome(
            cdp_holder, privacy_selector, "privacypolicy",
            widget_label, "privacy")
        record(verdict, "privacy_link_to_chrome",
               "PASS" if chrome_ok else "FAIL",
               f"Chrome on top after click; omnibox: {omnibox}" if chrome_ok else
               "Chrome did not foreground after Privacy click")
        record(verdict, "privacy_url_external_https",
               "PASS" if url_ok else "FAIL",
               f"omnibox contains 'privacypolicy': {omnibox}" if url_ok else
               f"omnibox missing privacypolicy: {omnibox}")
    else:
        record(verdict, "privacy_link_to_chrome", "FAIL", "CDP unavailable after Terms roundtrip")
        record(verdict, "privacy_url_external_https", "FAIL", "CDP unavailable after Terms roundtrip")
    assert_content_alive(verdict, baseline_close_count, "privacy_link")

    # 7. HOME — background. SDK detaches the overlay; instance stays alive.
    key("KEYCODE_HOME")
    time.sleep(1.5)
    assert_content_alive(verdict, baseline_close_count, "background_app")
    record(verdict, "background_kept_overlay", "PASS",
           "overlay still alive after HOME; top: " + top_activity().strip()[:80])

    # 8. Foreground via launcher (CLEAR_TASK pops the stack to a fresh
    #    MainActivity). The overlay should reattach to whatever activity
    #    Android resumes.
    foreground_ok = retry_action(launcher_intent, attempts=3, settle_s=1.0,
                                 label="foreground_app")
    time.sleep(1.5)
    assert_content_alive(verdict, baseline_close_count, "foreground_app")
    record(verdict, "foreground_kept_overlay",
           "PASS" if foreground_ok else "FAIL",
           "overlay reattached after foregrounding" if foreground_ok else
           "launcher_intent failed across 3 retries")

    # The launcher_intent + foreground sequence likely killed the WebView's
    # debug socket (the demo activity stack rebuilt). Reconnect once more so
    # the form-filling phase has a live CDP to work with.
    _cdp_close_quietly(cdp_holder[0])
    return cdp_try_connect(
        device=_DEVICE_SERIAL,
        title_substring=_cdp_title_for_feedback(widget_label),
    )


def _wait_and_click_submit(cdp: CDP, selector: str, label: str) -> bool:
    """Poll until the Submit button is enabled (loses `.disabled` class), then
    click. Some templates use `<div>` for submit so `:not(.disabled)` won't
    suffice — we explicitly query enabled-state via JS."""
    def _attempt() -> bool:
        # Check enabled state via JS — handles both real <button> (with
        # `disabled` attribute) and div-buttons (with `.disabled` class).
        is_enabled_js = (
            "(() => {"
            f"  const el = document.querySelector({json.dumps(selector)});"
            "  if (!el) return false;"
            "  if (el.disabled) return false;"
            "  if (el.classList && el.classList.contains('disabled')) return false;"
            "  return true;"
            "})()"
        )
        try:
            if not bool(cdp.run_js(is_enabled_js)):
                return False
            return cdp.click(selector)
        except CDPError:
            return False
    return retry_action(_attempt, attempts=4, settle_s=0.6, label=f"{label}_submit_click")


def _run_x_close_second_pass(verdict: dict, base_device_id: str,
                              feedback_type: str,
                              close_selector: str) -> None:
    """Second pass: change to a fresh device_id, present the widget again,
    click X close, verify content_close fires. Tests the X path independently
    from the form-Submit path. Records as `x_close_works`."""
    second_id = f"{base_device_id}_xpass_{secrets.token_hex(2)}"
    vlog(f"[feedback/{feedback_type}] X-close pass with new device_id={second_id}")

    # Change device_id via the SDK API directly using a JS-level workaround
    # is too invasive; just re-navigate to ContentZone, change device id
    # there, return to Feedback. The demo's Feedback activity has its own
    # buttons but no device-id field — ContentZone is the only place to set.
    # NOTE: this is best-effort; if device_id change fails, we still try the
    # X path and record what we observe.
    if not ensure_on_content_zone():
        record(verdict, "x_close_works", "FAIL",
               "couldn't return to ContentZone for device_id change")
        return

    # Skip device_id change for now — clicking present again with same
    # device may show a duplicate widget which is fine for X testing.
    # (Server may rate-limit the same widget for the same device_id, but
    # the X close path doesn't depend on a unique widget instance.)
    if not retry_action(lambda: open_demo_activity(FEEDBACK_ACTIVITY),
                         attempts=3, settle_s=1.0,
                         label="x_pass_open_feedback"):
        record(verdict, "x_close_works", "FAIL",
               "couldn't reach Feedback activity for X-close pass")
        return

    root = dump_ui()
    candidates = (find_nodes_by_text_contains(root, [f"present {feedback_type}"])
                  if root is not None else [])
    if not candidates:
        record(verdict, "x_close_works", "FAIL",
               f"PRESENT {feedback_type.upper()} button not found for X-close pass")
        return
    tap_node(candidates[0])

    if not wait_for_log("content_attached", TIMEOUTS["content_attach"]):
        record(verdict, "x_close_works", "FAIL",
               "widget didn't re-attach for X-close pass")
        return
    wait_for_log("content_loaded", TIMEOUTS["content_load"])
    time.sleep(1.0)

    # Reconnect CDP fresh — the original socket is dead.
    cdp = cdp_try_connect(device=_DEVICE_SERIAL,
                          title_substring=_cdp_title_for_feedback(feedback_type))
    if cdp is None:
        # Fall back: use existing close_selector via UI dump? a11y can't see
        # the icon-only `<div class="close-button">`, so most likely fall to
        # back-press.
        key("KEYCODE_BACK")
        close_match = wait_for_log("content_close", 10.0)
        record(verdict, "x_close_works",
               "PASS" if close_match else "FAIL",
               "CDP unavailable; back-press " +
               ("closed" if close_match else "didn't close"))
        return

    def _try_x_close() -> bool:
        try:
            if not cdp.click(close_selector):
                return False
        except CDPError:
            return False
        return wait_for_log("content_close", 5.0) is not None
    closed = retry_action(_try_x_close, attempts=3, settle_s=0.7,
                          label=f"{feedback_type}_x_close")
    record(verdict, "x_close_works",
           "PASS" if closed else "FAIL",
           f"CDP click {close_selector}" if closed else
           f"X-click via {close_selector} did not produce content_close")
    _cdp_close_quietly(cdp)


def run_feedback_survey(verdict: dict, cdp: Optional[CDP],
                         baseline_close_count: int) -> None:
    """Survey scenario: shared lifecycle phase → click radio option → consent
    → Submit → expect auto-close."""
    cdp = _run_feedback_lifecycle_phase(
        verdict, cdp, baseline_close_count,
        widget_label="survey",
        terms_selector=SURVEY_V2_TERMS_LINK,
        privacy_selector=SURVEY_V2_PRIVACY_LINK,
    )

    if cdp is None:
        record(verdict, "submit_button_works", "FAIL",
               "CDP unavailable after lifecycle phase")
        return

    # Click a radio option (random index, 0 or 1; falls back to 0 if 1
    # doesn't exist in this survey).
    idx = _random_survey_radio_index()
    radio_sel_n = f'{SURVEY_RADIO_OPTION}[data-test-id="survey-popup-radio-button-{idx}"]'
    radio_sel_first = SURVEY_RADIO_OPTION
    radio_clicked = retry_action(
        lambda: cdp.click(radio_sel_n) or cdp.click(radio_sel_first),
        attempts=3, settle_s=0.4, label="survey_radio")
    record(verdict, "form_radio_selected",
           "PASS" if radio_clicked else "FAIL",
           f"selected radio index {idx} (or fallback 0)")

    # Tick consent
    consent_clicked = retry_action(
        lambda: cdp.click(SURVEY_CONSENT_CHECKBOX),
        attempts=3, settle_s=0.3, label="survey_consent")
    record(verdict, "form_consent_checked",
           "PASS" if consent_clicked else "FAIL", "")

    # Submit (becomes enabled after radio + consent). Wait for content_close.
    submitted = _wait_and_click_submit(cdp, SURVEY_SUBMIT_BUTTON, "survey")
    if submitted:
        close_match = wait_for_log("content_close", 10.0)
        record(verdict, "submit_button_works",
               "PASS" if close_match else "FAIL",
               "Submit clicked; content_close fired" if close_match else
               "Submit clicked but no content_close within 10s")
    else:
        record(verdict, "submit_button_works", "FAIL",
               "Submit button never enabled / click never landed")


def run_feedback_nps(verdict: dict, cdp: Optional[CDP],
                      baseline_close_count: int) -> None:
    """NPS scenario: page 1 (rating) → Next → page 2 (where Terms/Privacy
    links live) → Terms → Privacy → HOME / foreground → comment → consent
    → Submit → expect auto-close.

    Survey and rating expose Terms/Privacy on page 1, so they run those
    checks inside `_run_feedback_lifecycle_phase`'s default flow. NPS hides
    them on page 2 — we use the `pre_links_callback` hook to advance pages
    before the lifecycle helper attempts the link clicks.
    """
    def _advance_to_page_2(c: CDP, v: dict) -> None:
        # Page 1: random rating button + Next.
        rating = _random_nps_rating()
        rating_sel = NPS_RATING_BUTTON_FMT.format(n=rating)
        rated = retry_action(lambda: c.click(rating_sel),
                             attempts=3, settle_s=0.4, label="nps_rating")
        record(v, "form_rating_selected",
               "PASS" if rated else "FAIL",
               f"clicked rating {rating}" if rated else
               f"failed to click {rating_sel}")

        next_clicked = _wait_and_click_submit(c, NPS_NEXT_BUTTON, "nps_next")
        record(v, "form_next_clicked",
               "PASS" if next_clicked else "FAIL",
               "Next button clicked" if next_clicked else
               "Next never enabled / click missed")

    cdp = _run_feedback_lifecycle_phase(
        verdict, cdp, baseline_close_count,
        widget_label="nps",
        terms_selector=SURVEY_V2_TERMS_LINK,
        privacy_selector=SURVEY_V2_PRIVACY_LINK,
        pre_links_callback=_advance_to_page_2,
    )

    if cdp is None:
        record(verdict, "submit_button_works", "FAIL",
               "CDP unavailable after lifecycle phase")
        return

    # Page 2 form-fill: textarea + consent + Submit.
    comment_text = _random_lorem()
    typed = retry_action(
        lambda: cdp.set_value(NPS_COMMENT_TEXTAREA, comment_text),
        attempts=3, settle_s=0.3, label="nps_comment_type")
    record(verdict, "form_comment_typed",
           "PASS" if typed else "FAIL",
           f"comment: '{comment_text[:40]}...'" if typed else
           "couldn't set textarea value")

    consent_clicked = retry_action(
        lambda: cdp.click(NPS_CONSENT_CHECKBOX),
        attempts=3, settle_s=0.3, label="nps_consent")
    record(verdict, "form_consent_checked",
           "PASS" if consent_clicked else "FAIL", "")

    # Count-based close detection: snapshot before submit, poll for the
    # count to increase. NPS auto-close fires only after the SDK posts the
    # response to the server (network round-trip), so the close may arrive
    # >10s after Submit click on slow networks. 20s gives headroom without
    # bloating the test on healthy networks (it returns as soon as the
    # count increments).
    pre_submit_close_count = count_log_matches("content_close")
    submitted = _wait_and_click_submit(cdp, NPS_SUBMIT_BUTTON, "nps_submit")
    if submitted:
        # NPS submit auto-close goes through `cly_widget_command=1&close=1`
        # which the SDK only emits AFTER posting the survey response and
        # processing the server reply. On warm fixture caches that's <5s;
        # on first-time / network-bound responses it can be 20-30s.
        deadline = time.monotonic() + 30.0
        closed = False
        while time.monotonic() < deadline:
            if count_log_matches("content_close") > pre_submit_close_count:
                closed = True
                break
            time.sleep(0.5)
        record(verdict, "submit_button_works",
               "PASS" if closed else "FAIL",
               "Submit clicked; content_close fired" if closed else
               "Submit clicked but no content_close within 30s")
    else:
        record(verdict, "submit_button_works", "FAIL",
               "Submit button never enabled / click never landed")


def run_feedback_rating(verdict: dict, cdp: Optional[CDP],
                         baseline_close_count: int) -> None:
    """Rating scenario: shared lifecycle phase → emoji rating → Add comment +
    text → Contact me + email → consent → Submit → expect auto-close."""
    cdp = _run_feedback_lifecycle_phase(
        verdict, cdp, baseline_close_count,
        widget_label="rating",
        terms_selector=RATING_TERMS_LINK,
        privacy_selector=RATING_PRIVACY_LINK,
    )

    if cdp is None:
        record(verdict, "submit_button_works", "FAIL",
               "CDP unavailable after lifecycle phase")
        return

    # 1. Click random emoji rating (1-5)
    emoji_n = _random_rating_emoji()
    emoji_sel = RATING_EMOJI_FMT.format(n=emoji_n)
    rated = retry_action(lambda: cdp.click(emoji_sel),
                         attempts=3, settle_s=0.4, label="rating_emoji")
    record(verdict, "form_rating_selected",
           "PASS" if rated else "FAIL",
           f"clicked emoji {emoji_n}" if rated else
           f"failed to click {emoji_sel}")

    # 2. Tick "Add comment" + type into textarea
    add_comment = retry_action(
        lambda: cdp.click(RATING_ADD_COMMENT_CHECKBOX),
        attempts=3, settle_s=0.3, label="rating_add_comment")
    record(verdict, "form_add_comment_checked",
           "PASS" if add_comment else "FAIL", "")
    comment_text = _random_lorem()
    typed_comment = retry_action(
        lambda: cdp.set_value(RATING_COMMENT_TEXTAREA, comment_text),
        attempts=3, settle_s=0.3, label="rating_comment_type")
    record(verdict, "form_comment_typed",
           "PASS" if typed_comment else "FAIL",
           f"comment: '{comment_text[:40]}...'")

    # 3. Tick "Contact me via e-mail" + type email
    contact_me = retry_action(
        lambda: cdp.click(RATING_EMAIL_CHECKBOX),
        attempts=3, settle_s=0.3, label="rating_contact_me")
    record(verdict, "form_contact_me_checked",
           "PASS" if contact_me else "FAIL", "")
    typed_email = retry_action(
        lambda: cdp.set_value(RATING_EMAIL_INPUT, LOREM_EMAIL),
        attempts=3, settle_s=0.3, label="rating_email_type")
    record(verdict, "form_email_typed",
           "PASS" if typed_email else "FAIL",
           f"email: '{LOREM_EMAIL}'")

    # 4. Tick consent
    consent_clicked = retry_action(
        lambda: cdp.click(RATING_CONSENT_CHECKBOX),
        attempts=3, settle_s=0.3, label="rating_consent")
    record(verdict, "form_consent_checked",
           "PASS" if consent_clicked else "FAIL", "")

    # 5. Submit (rating uses #cf-submit-button, not survey-v2's .submit-button.next)
    submitted = _wait_and_click_submit(cdp, RATING_SUBMIT_BUTTON, "rating_submit")
    if submitted:
        close_match = wait_for_log("content_close", 10.0)
        record(verdict, "submit_button_works",
               "PASS" if close_match else "FAIL",
               "Submit clicked; content_close fired" if close_match else
               "Submit clicked but no content_close within 10s")
    else:
        record(verdict, "submit_button_works", "FAIL",
               "Submit button never enabled / click never landed")


def run_feedback_test(feedback_type: str, run_id: str, seq: int,
                      output_dir: Path) -> dict:
    test_name = f"feedback_{feedback_type}"
    test_dir = output_dir / test_name
    test_dir.mkdir(parents=True, exist_ok=True)

    device_id = f"feedback_{feedback_type}_{run_id}_{seq:02d}"
    verdict = make_verdict(feedback_type, "feedback", device_id)
    started_mono = time.monotonic()

    clear_logcat()
    wake_and_unlock()
    # No force_stop — keep app warm; open_demo_activity navigates via launcher.
    # Two-phase dismiss: pre-nav clears anything left from the content phase;
    # post-nav (further down) catches the race where the overlay re-attaches
    # via the activity lifecycle while we navigate to Feedback.
    dismiss_lingering_overlay()
    vlog(f"[feedback/{feedback_type}] device_id={device_id}")
    remote_mp4 = f"/sdcard/test_{test_name}.mp4"
    rec = screenrecord_start(remote_mp4)
    cdp: Optional[CDP] = None
    try:
        if not open_demo_activity(FEEDBACK_ACTIVITY):
            verdict["errors"].append(
                f"am start {FEEDBACK_ACTIVITY} did not bring activity to foreground"
            )
            record(verdict, "widget_present", "FAIL", "host activity never visible")
            return verdict
        # Post-nav dismiss: SDK lifecycle callbacks fire on the new resumed
        # activity, which can re-attach a previous test's content overlay
        # asynchronously. Catch that race before the dump for the PRESENT button.
        dismiss_lingering_overlay()
        vlog(f"[feedback/{feedback_type}] {FEEDBACK_ACTIVITY} on top")

        root = dump_ui()
        if root is None:
            verdict["errors"].append("UI dump failed at Feedback activity")
            return verdict

        # Pick the right button to present this widget type. The demo has multiple
        # buttons mentioning each widget type (e.g. "rating" appears in
        # "ask_for_star_rating", "Send Manual Rating", "present rating", etc.).
        # Only `present <type>` actually shows the modern widget — prefer it.
        # Fall back to any substring match if the demo gets renamed.
        preferred = find_nodes_by_text_contains(root, [f"present {feedback_type}"])
        fallback = find_nodes_by_text_contains(root, [feedback_type])
        candidates = preferred or fallback
        if not candidates:
            record(verdict, "widget_present", "FAIL",
                   f"no clickable button labeled '{feedback_type}' in feedback activity")
        else:
            chosen = candidates[0]
            label = chosen.get("text") or chosen.get("content-desc") or "?"
            origin = "present-match" if preferred else "fallback substring"
            vlog(f"[feedback/{feedback_type}] tap '{label}' ({origin})")
            tap_node(chosen)
            record(verdict, "widget_present", "PASS",
                   f"tapped '{label}' ({origin})")

            attach = wait_for_log("content_attached", TIMEOUTS["content_attach"])
            if attach:
                w, h = int(attach.group("width")), int(attach.group("height"))
                verdict["content_bounds"] = {"width": w, "height": h}
                record(verdict, "widget_attached", "PASS",
                       f"size: {w}x{h}, host: {attach.group('host')}")
            else:
                record(verdict, "widget_attached", "FAIL", "no onWindowAttached log")

            loaded = wait_for_log("content_loaded", TIMEOUTS["content_load"])
            record(verdict, "widget_loaded",
                   "PASS" if loaded else "FAIL",
                   "" if loaded else "no [page loaded successfully] log")

            # Per-widget scenario dispatch. Each scenario runs the shared
            # lifecycle phase (rotate / BACK / Terms / Privacy / HOME /
            # foreground) then its type-specific form interactions, ending
            # with Submit → auto-close. After Submit closes the widget, we
            # run a second pass with a new device_id to test the X close
            # button independently.
            time.sleep(1.0)
            baseline_close_count = count_log_matches("content_close")
            cdp = cdp_try_connect(
                device=_DEVICE_SERIAL,
                title_substring=_cdp_title_for_feedback(feedback_type),
            )

            scenario_runner = {
                "survey": run_feedback_survey,
                "nps": run_feedback_nps,
                "rating": run_feedback_rating,
            }.get(feedback_type)
            if scenario_runner is None:
                record(verdict, "scenario_dispatch", "FAIL",
                       f"unknown feedback_type: {feedback_type}")
            else:
                scenario_runner(verdict, cdp, baseline_close_count)

            # Close the first CDP connection — the widget should be gone now
            # (Submit closes it). The X-close pass opens its own fresh CDP.
            _cdp_close_quietly(cdp)
            cdp = None

            # X-close second pass — re-present the widget, click X, verify.
            x_close_selector = (RATING_CLOSE_SELECTOR
                                if feedback_type == "rating"
                                else SURVEY_V2_CLOSE_SELECTOR)
            _run_x_close_second_pass(
                verdict, base_device_id=device_id,
                feedback_type=feedback_type,
                close_selector=x_close_selector,
            )

        verdict["fatal_exceptions"] = count_log_matches("fatal_exception")
        record(verdict, "no_fatal_exceptions",
               "PASS" if verdict["fatal_exceptions"] == 0 else "FAIL",
               f"{verdict['fatal_exceptions']} FATAL EXCEPTIONS")

    except _ContentClosedAbort:
        # Step that caused the close already recorded FAIL; capture final
        # error counters and proceed to finally for cleanup.
        verdict["fatal_exceptions"] = count_log_matches("fatal_exception")
        verdict["incorrect_context_use_violations"] = count_log_matches("incorrect_context_use")
    except Exception as e:
        verdict["errors"].append(f"runner exception: {type(e).__name__}: {e}")
    finally:
        try:
            local_mp4 = test_dir / "recording.mp4"
            screenrecord_stop(rec, remote_mp4, local_mp4)
        except Exception as e:
            verdict["errors"].append(f"screenrecord stop failed: {e}")

        _cdp_close_quietly(cdp)
        try:
            remove_forward(device=_DEVICE_SERIAL)
        except Exception:
            pass

        (test_dir / "logcat.txt").write_text(read_logcat())
        verdict["ended_at"] = datetime.now().isoformat(timespec="seconds")
        verdict["duration_s"] = round(time.monotonic() - started_mono, 2)
        (test_dir / "verdict.json").write_text(json.dumps(verdict, indent=2))

    return verdict


# ============================================================================
# Reporting
# ============================================================================

def write_summary(output_dir: Path, results: list[dict]) -> None:
    lines = [
        f"# Content & Feedback test run",
        f"",
        f"- Run dir: `{output_dir}`",
        f"- Started: {results[0]['started_at'] if results else 'n/a'}",
        f"- Total tests: {len(results)}",
        f"",
        f"## Per-variant verdict",
        f"",
        f"| Test | Duration | Checklist (PASS/FAIL/SKIP) | Bounds | FATAL | Errors |",
        f"|---|---|---|---|---|---|",
    ]
    for r in results:
        passes = sum(1 for v in r["checklist"].values() if v["status"] == "PASS")
        fails = sum(1 for v in r["checklist"].values() if v["status"] == "FAIL")
        skips = sum(1 for v in r["checklist"].values() if v["status"] == "SKIP")
        bounds = (f"{r['content_bounds']['width']}x{r['content_bounds']['height']}"
                  if r["content_bounds"] else "—")
        err_count = len(r["errors"])
        name = f"{r['kind']}_{r['variant']}"
        lines.append(
            f"| `{name}` | {r['duration_s']}s | "
            f"{passes}P / {fails}F / {skips}S | {bounds} | "
            f"{r['fatal_exceptions']} | {err_count} |"
        )
    lines.append("")
    lines.append("Per-test detail in each `<test>/verdict.json`. Video: `<test>/recording.mp4`.")
    (output_dir / "summary.md").write_text("\n".join(lines))


# ============================================================================
# Main
# ============================================================================

def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--device", help="adb device serial (e.g., emulator-5554)")
    p.add_argument(
        "--only",
        help="comma-separated subset of variants/widgets to run "
             "(e.g., 'modal,nps')",
    )
    p.add_argument("--no-content", action="store_true", help="skip content tests")
    p.add_argument("--no-feedback", action="store_true", help="skip feedback tests")
    p.add_argument(
        "-v", "--verbose", action="store_true",
        help="print per-step diagnostics (taps, foregrounding, dump results)",
    )
    p.add_argument(
        "--output-dir",
        type=Path,
        default=Path(__file__).parent / "test_output",
        help="parent dir for run artifacts (default: .github/scripts/test_output)",
    )
    return p.parse_args()


def main() -> int:
    global _DEVICE_SERIAL, VERBOSE
    args = parse_args()
    _DEVICE_SERIAL = args.device
    VERBOSE = args.verbose

    # Smoke check: a device must be reachable.
    devices = adb("devices").stdout
    if "device" not in devices.replace("List of devices", ""):
        print("[!] No adb device available. Plug in / start an emulator first.",
              file=sys.stderr)
        return 1

    only = set(filter(None, (args.only or "").split(",")))
    run_id = secrets.token_hex(2)
    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    output_dir: Path = args.output_dir / f"{timestamp}_{run_id}"
    output_dir.mkdir(parents=True, exist_ok=True)
    print(f"[+] Run dir: {output_dir}")
    print(f"[+] Run id:  {run_id}")

    results: list[dict] = []

    # Always emit a summary on exit — even if a test crashes the runner mid-sweep,
    # whatever results were collected before the crash should still be reportable.
    try:
        if not args.no_content:
            # One-time: cold launch + nav to ContentZone + cache button coords.
            # The 6 variants reuse this layout — saves 5 cold launches and 5
            # full UI dumps per sweep (~60s of dead time).
            print(f"[+] Setting up ContentZone (one-time)...")
            coords = setup_content_zone()
            if coords is None:
                print("[!] ContentZone setup failed — skipping content variants",
                      file=sys.stderr)
            else:
                print(f"[+] ContentZone coords cached: {coords}")
                for i, variant in enumerate(CONTENT_VARIANTS, start=1):
                    if only and variant not in only:
                        continue
                    print(f"[+] content/{variant} ...")
                    results.append(run_content_test(variant, coords, run_id, i, output_dir))
                    print(f"    done in {results[-1]['duration_s']}s")

                # End-of-content cleanup: navigate back to ContentZone and tap
                # Exit Content Zone. Without this the SDK's zoneTimerInterval
                # auto-fetch keeps firing for the last device id, and a late
                # content arrival attaches an overlay onto whichever activity
                # the next phase navigates to — blocking feedback tests from
                # finding their host UI.
                #
                # Verify via logcat that `exitContentZoneInternal` actually
                # fired. If it didn't, the tap missed the button (overlay
                # covering ContentZone, or stale cached coord). Retry up to 3x
                # with aggressive overlay cleanup between attempts.
                print("[+] Exiting content zone before feedback phase...")
                exit_confirmed = False
                for attempt in range(3):
                    if not ensure_on_content_zone():
                        print(f"    [exit attempt {attempt + 1}] couldn't reach ContentZone")
                        continue
                    clear_logcat()  # baseline so wait_for_log only sees this attempt
                    tap(*coords["exit_btn"])
                    if wait_for_log("exit_content_zone", 3.0):
                        print(f"    exitContentZoneInternal fired on attempt {attempt + 1}")
                        exit_confirmed = True
                        break
                    print(f"    [exit attempt {attempt + 1}] no log after tap; retrying")
                    dismiss_lingering_overlay()
                if not exit_confirmed:
                    print("[!] exitContentZone never confirmed — feedback may inherit "
                          "lingering content fetches", file=sys.stderr)
                time.sleep(1.0)
                dismiss_lingering_overlay()

        if not args.no_feedback:
            for i, ft in enumerate(FEEDBACK_TYPES, start=1):
                if only and ft not in only:
                    continue
                print(f"[+] feedback/{ft} ...")
                results.append(run_feedback_test(ft, run_id, i, output_dir))
                print(f"    done in {results[-1]['duration_s']}s")
    finally:
        if results:
            try:
                write_summary(output_dir, results)
                print(f"[+] Summary: {output_dir / 'summary.md'}")
            except Exception as e:
                print(f"[!] Failed to write summary.md: {e}", file=sys.stderr)
        else:
            print("[!] No tests selected — check --only / --no-* flags")

        # Clean up demo task entries from OS recents at sweep end. force_stop
        # kills the process AND clears its task affinity entries on modern
        # Android (API 30+), so the recents list doesn't accumulate one entry
        # per launcher_intent invocation across sweeps.
        try:
            force_stop()
            remove_forward(device=_DEVICE_SERIAL)
        except Exception as e:
            print(f"[!] Cleanup warning: {e}", file=sys.stderr)

    return 0


if __name__ == "__main__":
    sys.exit(main())
