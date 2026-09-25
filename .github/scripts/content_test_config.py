"""Configuration for the Countly Android SDK content/feedback widget UI test runner.

Edit variants, package names, log patterns, and "poke" activity inventory here as
the test suite grows.
"""

# ---------------------------------------------------------------------------
# Demo app identity
# ---------------------------------------------------------------------------

DEMO_PACKAGE = "ly.count.android.demo"
DEMO_LAUNCH_ACTIVITY = "MainActivity"
CONTENT_ZONE_ACTIVITY = "ActivityExampleContentZone"
FEEDBACK_ACTIVITY = "ActivityExampleFeedback"

# Most demo activities are NOT android:exported="true" — `adb shell am start` from
# uid 2000 (shell) hits a SecurityException on them. Workaround: launch MainActivity
# via the LAUNCHER intent (always exported), then tap the matching link to navigate.
# Map: target activity class → text shown on its MainActivity entry.
ACTIVITY_NAVIGATION = {
    "ActivityExampleContentZone": "Content Zone",
    "ActivityExampleFeedback": "Feedback",         # MainActivity label is "Feedback & Surveys" (& becomes & after XML decode)
    "ActivityExampleCustomEvents": "Custom Events",
    "ActivityExampleViewTracking": "View Tracking",
    "ActivityExampleUserDetails": "User Properties",
}

# ContentZone activity element IDs (from app/src/main/res/layout/activity_example_content_zone.xml)
CONTENT_ZONE_DEVICE_ID_FIELD = "editTextDeviceIdContentZone"
CONTENT_ZONE_BTN_CHANGE_DEVICE_ID = "button80"  # "Change Device ID"
CONTENT_ZONE_BTN_ENTER_ZONE = "button74"        # "Enter Content Zone"
CONTENT_ZONE_BTN_EXIT_ZONE = "button75"         # "Exit Content Zone"

# ---------------------------------------------------------------------------
# Test variants
# ---------------------------------------------------------------------------

# Server routes content-overlay type by device ID prefix. Update when new
# content positions are added on the server side.
CONTENT_VARIANTS = [
    "sticky_top",
    "sticky_bottom",
    "modal",
    "half_modal_top",
    "half_modal_bottom",
    "fullscreen",
]

# Variants whose overlay covers the full screen — either as a true fullscreen
# widget or as a centered dialog with a clickable backdrop. They share two
# properties that govern test routing:
#   1. No "outside" region — passthrough probes have nowhere to land.
#   2. The Vue framework treats backdrop taps as button1 (Close), so any
#      navigation-attempted tap on a host activity link will instead dismiss
#      the widget. Pokes-after-Go don't work — pokes run inside the lifecycle
#      scenario's HOME/foreground sequence instead.
FULLSCREEN_VARIANTS = {
    "fullscreen",
    "modal",
    "half_modal_top",
    "half_modal_bottom",
}

FEEDBACK_TYPES = ["nps", "rating", "survey"]

# ---------------------------------------------------------------------------
# Feedback widget DOM selectors
# ---------------------------------------------------------------------------
#
# All three widgets share the load-confirmation marker text — the demo's
# fixtures embed "Widget Automation Test" into the rendered HTML so the test
# runner can confirm the right widget actually loaded (vs e.g. an error page
# or the wrong template variant).
#
# NPS and Survey use the `survey-widget-v2` framework. Rating uses an older
# Vue-based "ratings-popup" framework — DOM is structurally different
# (`#close-btn` instead of `.close-button`, `#cf-submit-button` instead of
# `.submit-button.next`).
#
# All Terms / Privacy links end with `cly_x_int=1`, which the SDK's URL
# listener routes via `Intent.ACTION_VIEW` → Chrome opens.

# Substring that's stable across all 3 templates (survey + rating use
# "Widget Automation Test", NPS uses "Widgets Automation Test"). Matching
# the common stem "Automation Test" handles both forms without fragile
# per-widget markers.
WIDGET_AUTOMATION_TEXT = "Automation Test"

# survey-v2 framework (NPS, Survey)
# CSS4 case-insensitive `[i]` flag on attribute selectors: matches both
# `privacypolicy.com` and `privacyPolicy.com` (server-side fixture URLs
# can get rotated with different casing).
SURVEY_V2_CLOSE_SELECTOR = ".close-button"
SURVEY_V2_TERMS_LINK = 'a[href*="termsandconditions" i]'
SURVEY_V2_PRIVACY_LINK = 'a[href*="privacypolicy" i]'
# NOTE on survey-v2 framework: each widget has TWO consent containers (one
# for ratings page, one for comments page) AND TWO `.submit-button.next`
# elements in the DOM at the same time. The inactive one is hidden via CSS
# but still in the DOM. Generic class/id selectors return the wrong
# (hidden, inactive) element. We use page-scoped data-test-ids — they're
# unique and stable.

# Survey: single page, has one consent + one submit. Page-scoped IDs.
SURVEY_SUBMIT_BUTTON = '[data-test-id="survey-drawer-survey-page-next-button"]'
SURVEY_CONSENT_CHECKBOX = '[data-test-id="survey-survey-sub-page-agree-to-terms-conditions-checkbox"]'
SURVEY_RADIO_OPTION = ".radio-item"

# NPS: page 1 (rating) → Next → page 2 (comment + consent + Submit). Page 2
# elements use the `nps-survey-sub-page-*` test-id prefix.
NPS_NEXT_BUTTON = '[data-test-id="nps-drawer-survey-page-next-button"]'
NPS_SUBMIT_BUTTON = '[data-test-id="nps-survey-sub-page-submit-button"]'
NPS_CONSENT_CHECKBOX = '[data-test-id="nps-survey-sub-page-agree-to-terms-conditions-checkbox"]'
NPS_RATING_BUTTON_FMT = '.rating-button[data-rating="{n}"]'
NPS_COMMENT_TEXTAREA = '[data-test-id="nps-popup-comment-textarea"]'

# Rating widget (separate framework, not survey-v2)
RATING_CLOSE_SELECTOR = "#close-btn"
RATING_EMOJI_FMT = '.rating-emotion[data-score="{n}"]'  # 1..5
RATING_ADD_COMMENT_CHECKBOX = "#countly-feedback-show-comment"
RATING_COMMENT_TEXTAREA = "#countly-feedback-comment-textarea"
RATING_EMAIL_CHECKBOX = "#countly-feedback-show-email"
RATING_EMAIL_INPUT = "#countly-feedback-contact-me-email"
RATING_CONSENT_CHECKBOX = '.consent input[type="checkbox"]'
RATING_TERMS_LINK = 'a[href*="termsandconditions" i]'
RATING_PRIVACY_LINK = 'a[href*="privacypolicy" i]'
RATING_SUBMIT_BUTTON = "#cf-submit-button"

# Lorem-ipsum-style sample text for free-form fields. Uses a small pool so
# different runs produce different inputs — exercises the SDK's input-event
# listeners with varying string content rather than the same value every time.
LOREM_TEXT_POOL = [
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit",
    "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua",
    "Ut enim ad minim veniam, quis nostrud exercitation",
    "Duis aute irure dolor in reprehenderit in voluptate velit",
    "Excepteur sint occaecat cupidatat non proident, sunt in culpa",
]
LOREM_EMAIL = "automation+lorem@example.test"

# ---------------------------------------------------------------------------
# Random "poke" inventory — activities the runner navigates to during a content
# session to exercise event recording and underlying UI activity. Update when
# the demo grows new event-capable activities.
# ---------------------------------------------------------------------------

# Hints are exact substrings of `android:text` from the demo layout XML —
# precise enough to not collide with random buttons in other activities.
POKE_ACTIVITIES = [
    {
        "activity": "ActivityExampleCustomEvents",
        "button_text_hints": [
            "Record Custom Event",
            "Record Event",
            "Event with Segmentation",
            "Event with Count + Sum",
            "Trigger Sending Events",
        ],
        "max_taps": 3,
    },
    {
        "activity": "ActivityExampleViewTracking",
        "button_text_hints": [
            "record view A",
            "record view B",
            "record view C",
        ],
        "max_taps": 2,
    },
    {
        "activity": "ActivityExampleUserDetails",
        "button_text_hints": [
            "Set Standard Properties",
            "Set Custom Properties",
            "Save to Server",
        ],
        "max_taps": 2,
    },
]

# ---------------------------------------------------------------------------
# Logcat assertion regex patterns. Keep one pattern per checklist item so a
# log-message rename only requires one config edit.
# ---------------------------------------------------------------------------

LOG_PATTERNS = {
    # Content overlay lifecycle
    "content_attached": (
        r"\[ContentOverlayView\] onWindowAttached, attached to "
        r"\[(?P<host>\w+)\], orientation: \[(?P<orientation>\d+)\], "
        r"size: \[(?P<width>\d+)x(?P<height>\d+)\]"
    ),
    "content_loaded": r"\[ContentOverlayView\] page loaded successfully",
    "content_detach": r"\[ContentOverlayView\] detachFromWindow",
    "content_close": r"\[ContentOverlayView\] close, closing content overlay",
    "content_reattached": r"\[ContentOverlayView\] onWindowAttached",  # generic
    "config_changed": r"\[ContentOverlayView\] onConfigurationChanged, orientation changed",

    # Window-params positioning (sticky/half-modal/fullscreen disambiguation)
    "window_y_adjust": (
        r"\[ContentOverlayView\] createWindowParams, adjusting y from "
        r"\[(?P<from_y>\d+)\] to \[(?P<to_y>\d+)\]"
    ),

    # SDK lifecycle
    # Matches both `[DeviceId] Calling 'setID'` (the public-API wrapper, current format)
    # and `[ModuleDeviceId] ... setID` (the inner module, in case logging consolidates).
    "device_id_set": r"\[(?:Module)?DeviceId\].*[Ss]etID",
    "begin_session_200": r"begin_session.*code:\[200\]",
    "event_recorded": r"\[ModuleEvents\] recordEventInternal",

    # Passthrough probes care only about events triggered by HOST-app taps —
    # i.e., demo activity buttons calling `Countly.events().recordEvent(...)`.
    # Excludes SDK-internal events whose keys start with `[CLY]_` (like
    # `[CLY]_view`, `[CLY]_orientation`, `[CLY]_content_*`), which fire from
    # activity lifecycle, rotation, or widget interactions — none of those
    # are caused by a passthrough tap landing on a host button.
    "host_button_event": r"\[ModuleEvents\] recordEventInternal, key:\[(?!\[CLY\]_)",
    # Confirms the demo's Exit Content Zone button actually triggered the SDK
    # API call. Used to verify between-phase cleanup landed.
    "exit_content_zone": r"\[ModuleContent\] exitContentZoneInternal",

    # CountlyWebViewClient URL navigation — captures the URL each time a link
    # inside the overlay's WebView is followed (internal command URLs *and* external
    # redirects). Used to assert the URL contains the test prefix.
    "webview_url_loading": (
        r"\[CountlyWebViewClient\] shouldOverrideUrlLoading, url: \[(?P<url>[^\]]+)\]"
    ),

    # Extract the EXTERNAL URL the SDK is about to dispatch via Intent.ACTION_VIEW.
    # The Vue widget's "Go" button click goes through `?action=link&link=<external>`
    # which is captured as an internal `countly_action_event` URL — but the `link=`
    # parameter is the actual outbound URL. The runner can't see this URL in plain
    # `shouldOverrideUrlLoading` because the SDK fires the Intent directly without
    # routing the external URL through the WebView client.
    "external_link_from_action": (
        r"[?&]link=(?P<url>https?://[^&\]\s\"]+)"
    ),

    # Same external URL also appears in `[CLY]_content_interacted` event
    # segmentation — `value=https://...` (or JSON-encoded `"value":"https://..."`).
    # Belt-and-suspenders backup: if the action URL is missed (e.g., logcat
    # truncated), the event log still has it.
    "external_link_from_event": (
        r'(?:value=|"value":")(?P<url>https?://[^"&\]\s,}]+)'
    ),

    # Errors that should never appear during a clean run.
    # Exclude `UiAutomation` — uiautomator's dump tool occasionally hits
    # `RuntimeException: Bad file descriptor` mid-accessibility-refresh; that's
    # a tooling artifact, not an SDK/app crash, and doesn't affect the user
    # experience we're testing.
    "fatal_exception": r"FATAL EXCEPTION: (?!UiAutomation\b)",
    "anr": r"ANR in",
    "incorrect_context_use": r"IncorrectContextUseViolation",
}

# ---------------------------------------------------------------------------
# Timeouts (seconds)
# ---------------------------------------------------------------------------

TIMEOUTS = {
    # Content attach can take up to one zoneTimerInterval (30s by default, set in
    # ModuleContent) when the server's first fetch returns "No content block found!"
    # — typically happens when the device_id was just set via setID and the server
    # hasn't bound fixtures to it yet. The SDK's automatic retry resolves it; we
    # just need to wait long enough.
    "content_attach": 45.0,      # max wait for [onWindowAttached] after entering zone
    "content_load":   60.0,      # max wait for [page loaded successfully]
    "config_change":  5.0,       # max wait for orientation change to be processed
    "device_id_set":  5.0,       # max wait for setID to be applied
    "event_recorded": 8.0,       # max wait for an event to register in logcat
    "chrome_open":    5.0,       # max wait for Chrome to come to foreground
}

# ---------------------------------------------------------------------------
# Chrome detection
# ---------------------------------------------------------------------------

# Chrome can be packaged under several IDs depending on device/build.
CHROME_PACKAGE_HINTS = [
    "com.android.chrome",
    "com.google.android.apps.chrome",
    "org.chromium.chrome",
]

# Chrome's omnibox (URL bar) resource-id varies a bit by version. UIAutomator
# can read it because the omnibox is a native EditText (not a WebView), so it
# IS exposed in the accessibility tree. Try these in order.
CHROME_URL_BAR_IDS = [
    "com.android.chrome:id/url_bar",
    "com.android.chrome:id/location_bar",
    "com.google.android.apps.chrome:id/url_bar",
    "org.chromium.chrome:id/url_bar",
]

# ---------------------------------------------------------------------------
# WebView in-content button text/desc hints (for finding "X" close, "Go" link, etc.)
# WebView accessibility nodes are matched by these strings (case-insensitive substring).
# ---------------------------------------------------------------------------

WEBVIEW_HINTS = {
    "close": ["Close", "X", "×", "✕", "Dismiss", "Cancel"],
    "go": ["Go", "Open", "Visit", "Click here"],
}
