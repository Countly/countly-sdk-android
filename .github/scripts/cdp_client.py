"""Minimal Chrome DevTools Protocol (CDP) client for the Countly content/feedback
widget WebView.

Why this exists
---------------
UIAutomator only sees the WebView's accessibility-tree projection of the DOM,
which strips HTML class names, IDs, `data-*` attributes, and most CSS state.
That makes locating buttons like `<div class="close-button">` (no text, icon
font glyph) impossible through accessibility alone.

The Chrome WebView ships with the DevTools Protocol enabled per process. When
the demo app calls `WebView.setWebContentsDebuggingEnabled(true)`, each
WebView's process exposes a debuggable Unix domain socket
`webview_devtools_remote_<pid>` in the abstract namespace (the leading `@`).
We use `adb forward tcp:<port> localabstract:<socket-name>` to bridge that to
localhost, then speak HTTP+WebSocket to it the same way Chrome's
`chrome://inspect` page does.

Stdlib only — no `websockets` / `websocket-client` dependency. The WebSocket
framing is a hand-rolled RFC 6455 client that supports the subset we need:
text frames, masking from client, single-shot send/recv (no fragmentation),
Sec-WebSocket-Key handshake. ~150 lines.

What you can do with it
-----------------------
- Find any DOM element by CSS selector, including class/ID/aria selectors that
  UIAutomator can't see.
- Trigger clicks via `.click()` instead of synthesising taps at coordinates —
  fires the actual DOM click event, no pixel math, no DPI translation.
- Read live DOM state: button text, href values, computed bounds.

Example
-------
    cdp = CDP.connect_to_demo()
    state = cdp.run_js(\"\"\"
        JSON.stringify({
            close: !!document.querySelector('.close-button'),
            buttons: Array.from(document.querySelectorAll('button')).map(b => ({
                text: b.innerText.trim(), href: b.getAttribute('data-href') || null,
            })),
        })
    \"\"\")
    cdp.click('.close-button')
"""

import base64
import hashlib
import json
import secrets
import socket
import ssl
import struct
import subprocess
import time
import urllib.request
from typing import Optional
from urllib.parse import urlparse


CDP_LOCAL_PORT = 9222


# ---------------------------------------------------------------------------
# adb plumbing — find the WebView socket and bridge it to localhost
# ---------------------------------------------------------------------------

def _adb(args: list[str], device: Optional[str] = None,
         timeout: float = 10) -> subprocess.CompletedProcess:
    cmd = ["adb"]
    if device:
        cmd += ["-s", device]
    cmd += args
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)


def find_webview_socket(package: str, device: Optional[str] = None) -> Optional[str]:
    """Returns the abstract-namespace socket name (without the leading '@') for
    the WebView devtools server in the named package's process, or None if
    no debuggable WebView is currently running.

    Looks at /proc/net/unix for entries like
        000... 00010000 0001 01 12345 @webview_devtools_remote_12345

    The PID at the end of the name corresponds to the process hosting the
    WebView (usually the renderer subprocess on modern Android). We just want
    a socket whose name starts with `webview_devtools_remote_` — the PID is
    auto-routed to whichever WebView is currently alive.
    """
    proc = _adb(["shell", "cat /proc/net/unix"], device=device)
    if proc.returncode != 0:
        return None
    for line in proc.stdout.splitlines():
        # Last whitespace-separated token is the path. Abstract sockets begin
        # with '@'; we want the literal name without it.
        parts = line.split()
        if not parts:
            continue
        path = parts[-1]
        if path.startswith("@webview_devtools_remote"):
            return path[1:]  # strip leading @
    return None


def setup_forward(socket_name: str, local_port: int = CDP_LOCAL_PORT,
                  device: Optional[str] = None) -> bool:
    """Sets up `adb forward tcp:<local_port> localabstract:<socket_name>`.
    Returns True on success."""
    proc = _adb(
        ["forward", f"tcp:{local_port}", f"localabstract:{socket_name}"],
        device=device,
    )
    return proc.returncode == 0


def remove_forward(local_port: int = CDP_LOCAL_PORT,
                   device: Optional[str] = None) -> None:
    _adb(["forward", "--remove", f"tcp:{local_port}"], device=device)


# ---------------------------------------------------------------------------
# CDP page enumeration over HTTP
# ---------------------------------------------------------------------------

def list_pages(local_port: int = CDP_LOCAL_PORT) -> list[dict]:
    """GET http://localhost:<port>/json — returns the array of debuggable
    pages. Each entry has at least `id`, `title`, `url`, `webSocketDebuggerUrl`.
    """
    url = f"http://localhost:{local_port}/json"
    with urllib.request.urlopen(url, timeout=5) as r:
        return json.loads(r.read())


# ---------------------------------------------------------------------------
# Minimal WebSocket client (RFC 6455 text frames, client-side masking)
# ---------------------------------------------------------------------------

# Per RFC 6455 §1.3, the server's Sec-WebSocket-Accept must equal
#   base64(sha1(client_key + GUID))
_WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"


class _WSError(RuntimeError):
    pass


class WSClient:
    """Synchronous WebSocket client implementing the subset CDP needs:
    text frames, no fragmentation, masked-from-client. Roughly 80 lines.

    Usage:
        ws = WSClient.connect("ws://localhost:9222/devtools/page/<id>")
        ws.send('{"id":1,"method":"Runtime.evaluate","params":{...}}')
        reply = ws.recv()
        ws.close()
    """

    def __init__(self, sock: socket.socket):
        self._sock = sock
        self._recv_buf = b""

    @classmethod
    def connect(cls, ws_url: str, timeout: float = 5.0) -> "WSClient":
        u = urlparse(ws_url)
        if u.scheme not in ("ws", "wss"):
            raise _WSError(f"not a ws:// URL: {ws_url}")
        host = u.hostname or "localhost"
        port = u.port or (443 if u.scheme == "wss" else 80)
        path = u.path or "/"
        if u.query:
            path = f"{path}?{u.query}"

        sock = socket.create_connection((host, port), timeout=timeout)
        if u.scheme == "wss":
            sock = ssl.create_default_context().wrap_socket(sock, server_hostname=host)

        # Random 16-byte key, base64-encoded, sent as Sec-WebSocket-Key.
        client_key = base64.b64encode(secrets.token_bytes(16)).decode("ascii")
        request = (
            f"GET {path} HTTP/1.1\r\n"
            f"Host: {host}:{port}\r\n"
            f"Upgrade: websocket\r\n"
            f"Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {client_key}\r\n"
            f"Sec-WebSocket-Version: 13\r\n"
            f"\r\n"
        )
        sock.sendall(request.encode("ascii"))

        # Read until end-of-headers.
        buf = b""
        while b"\r\n\r\n" not in buf:
            chunk = sock.recv(4096)
            if not chunk:
                raise _WSError("server closed before handshake completed")
            buf += chunk
        head, _, leftover = buf.partition(b"\r\n\r\n")
        head_text = head.decode("latin-1")
        if " 101 " not in head_text.split("\r\n", 1)[0]:
            raise _WSError(f"non-101 status: {head_text.splitlines()[0]}")

        # Verify Sec-WebSocket-Accept.
        expected = base64.b64encode(
            hashlib.sha1((client_key + _WS_GUID).encode("ascii")).digest()
        ).decode("ascii")
        for line in head_text.split("\r\n"):
            if line.lower().startswith("sec-websocket-accept:"):
                got = line.split(":", 1)[1].strip()
                if got != expected:
                    raise _WSError(f"bad Sec-WebSocket-Accept: {got!r} != {expected!r}")
                break
        else:
            raise _WSError("no Sec-WebSocket-Accept header in response")

        client = cls(sock)
        client._recv_buf = leftover  # any post-header bytes belong to the WS stream
        return client

    def send(self, text: str) -> None:
        """Send a single text frame, fin=1, masked (clients MUST mask)."""
        payload = text.encode("utf-8")
        header = bytearray()
        header.append(0x81)  # fin=1, opcode=1 (text)
        ln = len(payload)
        if ln < 126:
            header.append(0x80 | ln)
        elif ln < 65536:
            header.append(0x80 | 126)
            header += struct.pack(">H", ln)
        else:
            header.append(0x80 | 127)
            header += struct.pack(">Q", ln)
        mask = secrets.token_bytes(4)
        header += mask
        masked = bytearray(payload)
        for i in range(len(masked)):
            masked[i] ^= mask[i % 4]
        self._sock.sendall(bytes(header) + bytes(masked))

    def _read_exact(self, n: int) -> bytes:
        data = bytearray(self._recv_buf[:n])
        self._recv_buf = self._recv_buf[n:]
        while len(data) < n:
            chunk = self._sock.recv(n - len(data))
            if not chunk:
                raise _WSError("connection closed mid-frame")
            data += chunk
        return bytes(data)

    def recv(self, timeout: float = 10.0) -> str:
        """Read one frame (assumes fin=1, no fragmentation, server frames are
        unmasked per RFC 6455 §5.3). Returns the decoded text payload."""
        self._sock.settimeout(timeout)
        b1, b2 = self._read_exact(2)
        opcode = b1 & 0x0F
        if opcode == 0x8:  # close
            raise _WSError("server sent close frame")
        if opcode == 0x9:  # ping → pong; not expected from CDP, but tolerate
            payload_len = b2 & 0x7F
            payload = self._read_exact(payload_len) if payload_len else b""
            self._sock.sendall(b"\x8a" + bytes([0x80 | payload_len]) +
                               secrets.token_bytes(4) + payload)
            return self.recv(timeout)
        if opcode != 0x1:
            raise _WSError(f"unexpected opcode 0x{opcode:x}")
        masked = bool(b2 & 0x80)
        payload_len = b2 & 0x7F
        if payload_len == 126:
            payload_len = struct.unpack(">H", self._read_exact(2))[0]
        elif payload_len == 127:
            payload_len = struct.unpack(">Q", self._read_exact(8))[0]
        if masked:
            mask = self._read_exact(4)
        payload = self._read_exact(payload_len)
        if masked:
            payload = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
        return payload.decode("utf-8")

    def close(self) -> None:
        try:
            # Send close frame (opcode 8, empty payload, masked).
            self._sock.sendall(b"\x88\x80" + secrets.token_bytes(4))
        except Exception:
            pass
        try:
            self._sock.close()
        except Exception:
            pass


# ---------------------------------------------------------------------------
# CDP wrapper — request/response with monotonically-increasing message IDs
# ---------------------------------------------------------------------------

class CDPError(RuntimeError):
    pass


class CDP:
    """Thin wrapper around a CDP WebSocket: send-and-await-matching-id.

    CDP is bidirectional (server can send unsolicited events for things like
    `Page.frameNavigated`), so we have to handle the case where `recv` returns
    an event message before our reply. We just discard everything that doesn't
    match our outgoing id.
    """

    def __init__(self, ws: WSClient):
        self._ws = ws
        self._next_id = 1

    @classmethod
    def connect_to_demo(cls, package: str = "ly.count.android.demo",
                        device: Optional[str] = None,
                        title_substring: Optional[str] = None,
                        local_port: int = CDP_LOCAL_PORT,
                        retries: int = 3) -> Optional["CDP"]:
        """Locate the demo's WebView, set up adb forward, list pages, pick the
        relevant one, and return a connected CDP. Returns None if no debuggable
        WebView is found (e.g., the widget hasn't loaded yet, or
        `setWebContentsDebuggingEnabled(true)` isn't on).
        """
        for _ in range(retries):
            sock_name = find_webview_socket(package, device=device)
            if sock_name:
                break
            time.sleep(0.4)
        else:
            return None
        if not setup_forward(sock_name, local_port=local_port, device=device):
            return None
        try:
            pages = list_pages(local_port=local_port)
        except Exception:
            return None
        # If a title hint is provided, prefer it; otherwise take the first
        # page that's not an extension/devtools page.
        chosen = None
        for p in pages:
            if p.get("type") not in ("page", None):
                continue
            if title_substring and title_substring.lower() not in p.get("title", "").lower():
                continue
            chosen = p
            break
        if chosen is None and pages:
            chosen = pages[0]
        if not chosen:
            return None
        ws_url = chosen.get("webSocketDebuggerUrl")
        if not ws_url:
            return None
        ws = WSClient.connect(ws_url)
        return cls(ws)

    def _send(self, method: str, params: Optional[dict] = None) -> dict:
        """Send one CDP command, return the matching response object."""
        msg_id = self._next_id
        self._next_id += 1
        msg = {"id": msg_id, "method": method}
        if params:
            msg["params"] = params
        self._ws.send(json.dumps(msg))
        # Drain events until we get our response.
        while True:
            reply = json.loads(self._ws.recv())
            if reply.get("id") == msg_id:
                if "error" in reply:
                    err = reply["error"]
                    raise CDPError(f"{method}: {err.get('message')} ({err.get('code')})")
                return reply.get("result", {})
            # else: an event/other-id reply; ignore.

    def run_js(self, expression: str) -> object:
        """Run a JS expression in the page's main frame. Returns the JS value
        marshaled by Runtime.evaluate. Use `JSON.stringify(...)` in the
        expression and json.loads on the result for structured data.

        Despite the name, this only runs JS in a *remote* browser — the
        Python interpreter never sees the expression as code, only sends it
        as a string to the WebView's V8.
        """
        result = self._send("Runtime.evaluate", {
            "expression": expression,
            "returnByValue": True,
            "awaitPromise": False,
            # Treat the eval as initiated by a user gesture. Some browser
            # behaviors (popup blockers, target="_blank" navigation, certain
            # event handlers) only fire when the engine believes the user
            # initiated the action — without this, programmatic `.click()`
            # on an `<a target="_blank">` after the first call can be
            # silently suppressed.
            "userGesture": True,
        })
        if "exceptionDetails" in result:
            raise CDPError(f"JS exception: {result['exceptionDetails'].get('text')}")
        ret = result.get("result", {})
        return ret.get("value")

    def click(self, css_selector: str) -> bool:
        """Trigger a click on the first matching element. Returns True if an
        element was found and clicked, False otherwise."""
        js = (
            "(() => {"
            f"  const el = document.querySelector({json.dumps(css_selector)});"
            "  if (!el) return false;"
            "  el.click();"
            "  return true;"
            "})()"
        )
        return bool(self.run_js(js))

    def set_value(self, css_selector: str, value: str) -> bool:
        """Set the `value` of an `<input>` or `<textarea>` and dispatch input/
        change events so any framework listeners (Vue's v-model) update.
        Returns True if the element was found and value set."""
        js = (
            "(() => {"
            f"  const el = document.querySelector({json.dumps(css_selector)});"
            "  if (!el) return false;"
            f"  el.value = {json.dumps(value)};"
            "  el.dispatchEvent(new Event('input', {bubbles: true}));"
            "  el.dispatchEvent(new Event('change', {bubbles: true}));"
            "  return true;"
            "})()"
        )
        return bool(self.run_js(js))

    def set_checkbox(self, css_selector: str, checked: bool = True) -> bool:
        """Set a checkbox's checked state and dispatch change/click as needed
        so v-model and onClick handlers fire. Returns True on success.

        Note: just setting `el.checked = true` doesn't trigger Vue's
        change handler. Dispatching a `click` is the most reliable way
        because it's exactly what a real user click does."""
        js = (
            "(() => {"
            f"  const el = document.querySelector({json.dumps(css_selector)});"
            "  if (!el) return false;"
            f"  if (el.checked !== {str(checked).lower()}) el.click();"
            "  return true;"
            "})()"
        )
        return bool(self.run_js(js))

    def has_text(self, css_selector: str, expected_substring: str) -> bool:
        """Returns True if the matched element's textContent contains the
        substring (case-sensitive). Used as a load-confirmation marker —
        e.g., the 'Widget Automation Test' string in feedback templates."""
        js = (
            "(() => {"
            f"  const el = document.querySelector({json.dumps(css_selector)});"
            "  if (!el) return false;"
            f"  return (el.textContent || '').indexOf({json.dumps(expected_substring)}) !== -1;"
            "})()"
        )
        return bool(self.run_js(js))

    def text_present_anywhere(self, expected_substring: str) -> bool:
        """Returns True if `expected_substring` appears in document.body's
        textContent. Cheaper than full DOM walk; finds dynamically-inserted
        markers no matter where in the tree they live."""
        js = (
            "(() => {"
            "  return (document.body && document.body.textContent || '')"
            f"    .indexOf({json.dumps(expected_substring)}) !== -1;"
            "})()"
        )
        return bool(self.run_js(js))

    def selector_exists(self, css_selector: str) -> bool:
        """Returns True if a CSS selector matches at least one element."""
        js = (
            "(() => {"
            f"  return !!document.querySelector({json.dumps(css_selector)});"
            "})()"
        )
        return bool(self.run_js(js))

    def query_buttons(self) -> list[dict]:
        """Returns a list of all visible <button>, <a>, and elements with
        role='button' on the page. Each entry has `text`, `tag`, `href`, and
        `rect` (CSS bounds). Useful when you don't know the selector ahead of
        time and want to enumerate clickable elements."""
        js = """
            JSON.stringify(
                Array.from(document.querySelectorAll(
                    'button, a, [role="button"], .close-button, .submit-button, .send-button'
                )).map(e => {
                    const r = e.getBoundingClientRect();
                    return {
                        tag: e.tagName,
                        text: (e.innerText || e.textContent || '').trim().slice(0, 80),
                        href: e.getAttribute('href') || e.getAttribute('data-href') || null,
                        cls: (e.className && typeof e.className === 'string') ? e.className.slice(0, 80) : '',
                        rect: { x: r.x, y: r.y, w: r.width, h: r.height },
                        visible: r.width > 0 && r.height > 0,
                    };
                })
            )
        """
        raw = self.run_js(js)
        try:
            return json.loads(raw) if isinstance(raw, str) else []
        except Exception:
            return []

    def close(self) -> None:
        try:
            self._ws.close()
        except Exception:
            pass
