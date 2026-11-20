# Content & Feedback widget UI test runner

Automated UI test for the demo app's content overlay (sticky / modal /
half-modal / fullscreen) and feedback widgets (NPS / rating / survey).
Records a video per variant, drives the app via `adb` + UIAutomator, and
writes a per-variant verdict + summary.

## What it does

For each content variant in `content_test_config.CONTENT_VARIANTS`:

1. Starts `adb screenrecord` in the background.
2. Force-stops the demo and launches `ActivityExampleContentZone`.
3. Sets the device ID to `<variant>_<runId>_<seq>` (e.g. `modal_a3f9_03`) so
   the server returns the matching content type. Adjust prefixes in the
   config file if your server routing changes.
4. Taps **Change Device ID**, then **Enter Content Zone**.
5. Waits for `[ContentOverlayView] page loaded successfully` in logcat
   (max `TIMEOUTS["content_load"]` seconds).
6. Rotates landscape → presses back → rotates portrait.
7. Navigates through `POKE_ACTIVITIES` (CustomEvents, ViewTracking,
   UserDetails by default), tapping clickable buttons matched by
   `button_text_hints` to record events.
8. Tap-passthrough probes (skipped on `fullscreen`):
   - taps a coordinate outside content bounds, asserts background activity
     received an event;
   - taps inside, asserts no background event was emitted.
9. Best-effort WebView interactions: looks for "Go" / "X" nodes in the
   accessibility dump, taps them, asserts Chrome opened / overlay closed.
10. Counts FATAL EXCEPTIONS and `IncorrectContextUseViolation` lines in the
    full test logcat.
11. Stops `screenrecord`, pulls the MP4, writes `verdict.json`.

Feedback widgets follow a similar but shorter flow — operations inside
the WebView are intentionally minimal until you specify them.

## Requirements

- `adb` on `PATH`.
- A connected emulator or device with the demo app installable
  (`./gradlew :app:installDebug` first).
- Python 3.9+ (stdlib only, no `pip install`).

## Run

```sh
# All variants + all feedback widgets
python3 .github/scripts/content_test_runner.py

# A subset
python3 .github/scripts/content_test_runner.py --only modal,sticky_up,nps

# Skip a category
python3 .github/scripts/content_test_runner.py --no-feedback

# Target a specific device
python3 .github/scripts/content_test_runner.py --device emulator-5556
```

## Output

Artifacts land under `.github/scripts/test_output/<timestamp>_<runId>/`:

```
2026-05-02_18-30-00_a3f9/
├── summary.md
├── content_modal/
│   ├── recording.mp4
│   ├── logcat.txt
│   └── verdict.json
├── content_sticky_up/
│   └── ...
├── feedback_nps/
│   └── ...
└── ...
```

Open `summary.md` for a per-test PASS/FAIL/SKIP table. For a failing
variant, watch the corresponding `recording.mp4` and inspect
`logcat.txt`.

## Tuning

Edit `.github/scripts/content_test_config.py` to:

- **Add new variants**: append to `CONTENT_VARIANTS`. Mark fullscreen-like
  variants in `FULLSCREEN_VARIANTS` to skip passthrough probes.
- **Change log assertions**: every PASS/FAIL hinges on a regex in
  `LOG_PATTERNS`. If the SDK renames a log message, update that one entry.
- **Adjust timeouts**: `TIMEOUTS` controls per-step waits. Increase if
  running on a slow device or VM.
- **Change "poke" inventory**: `POKE_ACTIVITIES` lists which demo
  activities the runner navigates to during a content session and which
  button text hints it taps. Add or remove entries as the demo grows.

## Limitations and known gaps

- **WebView interactions are best-effort.** UIAutomator can see WebView
  accessibility nodes on most modern Android versions, but some widgets
  may not expose their X / Go elements. Those tests fall back to SKIP
  rather than FAIL.
- **`adb screenrecord` is capped at 180 seconds per file.** Each test is
  designed to fit comfortably under that. If you add long pauses, expect
  the recording to truncate.
- **Tap-passthrough is heuristic.** "Background activity registered tap"
  is inferred from any new event-record log line during the probe window —
  it can produce WARN if the host activity at that screen position
  doesn't have a tappable element. Place a known button at the probed
  coordinates if you need a stronger assertion.
- **Tests are sequential, not parallel.** Running on multiple devices at
  once would need a refactor of `_DEVICE_SERIAL` from a module-global to
  per-test argument.

## Adding new feedback operations

When you want the runner to do more inside a feedback widget (fill
fields, submit answers, etc.), edit `run_feedback_test` in
`content_test_runner.py`. The accessibility dump (`dump_ui()`) and
`find_nodes_by_text_contains` should let you locate most form
controls. Open a per-widget code branch keyed on `feedback_type` if the
operations differ between NPS / rating / survey.
