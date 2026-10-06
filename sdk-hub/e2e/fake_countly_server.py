"""Stand-in for a Countly server for hub end-to-end runs.

Answers every request with {"result":"Success"} over HTTP/1.1 keep-alive and prints one line per
request: the client's TCP source address (one per TCP connection), the method, path, app_key and the
event keys. The running count of distinct source addresses is the number of TCP connections the hub
opened.
"""
import json
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

LOCK = threading.Lock()
CONNECTIONS = set()
PER_APP_KEY = {}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def handle_any(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else b""
        parts = urlsplit(self.path)
        params = parse_qs(parts.query)
        if body and self.headers.get("Content-Type", "").startswith("application/x-www-form-urlencoded"):
            for key, values in parse_qs(body.decode("utf-8", "replace")).items():
                params.setdefault(key, []).extend(values)
        app_key = params.get("app_key", ["-"])[-1]
        names = []
        if "events" in params:
            try:
                names = [event.get("key") for event in json.loads(params["events"][-1])]
            except ValueError:
                names = ["<unparsable>"]
        source = "%s:%d" % self.client_address
        with LOCK:
            CONNECTIONS.add(source)
            PER_APP_KEY[app_key] = PER_APP_KEY.get(app_key, 0) + 1
            connections = len(CONNECTIONS)
        print("%s conn=%s %s %s app_key=%s events=%s tcp_connections_so_far=%d"
              % (time.strftime("%H:%M:%S"), source, self.command, parts.path, app_key, names, connections), flush=True)
        payload = b'{"result":"Success"}'
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(payload)

    do_GET = handle_any
    do_POST = handle_any
    do_HEAD = handle_any

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    print("fake Countly server on 127.0.0.1:%d" % port, flush=True)
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
