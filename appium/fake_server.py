"""Stdlib fake of the Dublin Bus /v1 API for Appium smoke tests. Unknown paths return 404."""
import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlsplit

FIXTURES = Path(__file__).parent / "fixtures"


def _load(name):
    path = FIXTURES / name
    return json.loads(path.read_text()) if path.exists() else None


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        url = urlsplit(self.path)
        parts = [unquote(p) for p in url.path.strip("/").split("/")]
        body = self.route(parts, parse_qs(url.query))
        if body is None:
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        data = json.dumps(body).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def route(self, parts, query):
        if parts == ["v1", "status"]:
            return _load("status.json")
        stops = _load("stops.json")
        if parts == ["v1", "stops"]:
            q = query.get("query", [""])[0].lower()
            return [s for s in stops if q in s["name"].lower() or q in s["code"]]
        if len(parts) == 3 and parts[:2] == ["v1", "stops"]:
            return next((s for s in stops if parts[2] in (s["id"], s["code"])), None)
        if len(parts) == 4 and parts[:2] == ["v1", "stops"] and parts[3] == "arrivals":
            return _load(f"arrivals_{parts[2]}.json")
        return None

    def log_message(self, *args):
        pass


def start(port: int = 8080, host: str = "0.0.0.0") -> ThreadingHTTPServer:
    server = ThreadingHTTPServer((host, port), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


if __name__ == "__main__":
    start()
    threading.Event().wait()
