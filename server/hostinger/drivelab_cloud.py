#!/usr/bin/env python3
"""Private DriveLab Cloud ingestion API. Bind to loopback behind HTTPS reverse proxy."""
import hashlib
import hmac
import json
import os
import re
import tempfile
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(os.environ.get("DRIVELAB_CLOUD_ROOT", "/var/lib/drivelab-cloud")).resolve()
TOKEN = os.environ.get("DRIVELAB_CLOUD_TOKEN", "")
MAX_BYTES = 8 * 1024 * 1024
KINDS = {"informes", "screenshots", "cluster", "optimizador", "viajes", "energia", "errores", "versiones"}
SAFE = re.compile(r"^[a-zA-Z0-9][a-zA-Z0-9._-]{0,119}$")
EXTS = {"txt", "json", "csv", "png", "jpg", "jpeg", "webp", "log"}

def store(kind, name, payload, digest):
    if kind not in KINDS or not SAFE.fullmatch(name) or name in {".", ".."}:
        raise ValueError("invalid category or filename")
    if name.rsplit(".", 1)[-1].lower() not in EXTS:
        raise ValueError("unsupported extension")
    if not payload or len(payload) > MAX_BYTES:
        raise ValueError("invalid size")
    actual = hashlib.sha256(payload).hexdigest()
    if not hmac.compare_digest(actual, digest):
        raise ValueError("sha256 mismatch")
    now = datetime.now(timezone.utc)
    folder = ROOT / kind / now.strftime("%Y") / now.strftime("%m")
    folder.mkdir(parents=True, exist_ok=True, mode=0o700)
    # Content-addressed immutable files: retry is idempotent, no overwrite.
    target = folder / (actual + "_" + name)
    if target.exists():
        if hashlib.sha256(target.read_bytes()).hexdigest() != actual:
            raise RuntimeError("existing file integrity mismatch")
        return target, False
    fd, temp = tempfile.mkstemp(prefix=".upload-", dir=str(folder))
    try:
        with os.fdopen(fd, "wb") as out:
            out.write(payload)
            out.flush()
            os.fsync(out.fileno())
        os.chmod(temp, 0o600)
        try:
            os.link(temp, target)  # Atomic create, never replace.
            created = True
        except FileExistsError:
            created = False
        return target, created
    finally:
        os.unlink(temp)

class Handler(BaseHTTPRequestHandler):
    def respond(self, code, obj):
        body = json.dumps(obj, separators=(",", ":")).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            return self.respond(200, {"ok": True, "service": "drivelab-cloud"})
        return self.respond(404, {"ok": False})

    def do_POST(self):
        if self.path != "/v1/upload":
            return self.respond(404, {"ok": False})
        if not TOKEN or len(TOKEN) < 32:
            return self.respond(503, {"ok": False, "error": "not configured"})
        supplied = self.headers.get("Authorization", "")
        if not hmac.compare_digest(supplied, "Bearer " + TOKEN):
            return self.respond(401, {"ok": False})
        try:
            size = int(self.headers.get("Content-Length", "-1"))
            if size < 1 or size > MAX_BYTES:
                return self.respond(413, {"ok": False})
            kind = self.headers.get("X-DriveLab-Category", "")
            name = self.headers.get("X-DriveLab-Filename", "")
            digest = self.headers.get("X-Content-SHA256", "")
            if not re.fullmatch(r"[0-9a-f]{64}", digest):
                raise ValueError("invalid digest")
            payload = self.rfile.read(size)
            if len(payload) != size:
                raise ValueError("truncated body")
            target, created = store(kind, name, payload, digest)
            self.respond(201 if created else 200, {"ok": True, "sha256": digest, "stored": created,
                "path": str(target.relative_to(ROOT))})
        except ValueError as exc:
            self.respond(400, {"ok": False, "error": str(exc)})
        except Exception:
            self.respond(500, {"ok": False, "error": "internal error"})

if __name__ == "__main__":
    if not TOKEN or len(TOKEN) < 32:
        raise SystemExit("DRIVELAB_CLOUD_TOKEN must be >=32 characters")
    ThreadingHTTPServer(("127.0.0.1", int(os.environ.get("DRIVELAB_CLOUD_PORT", "8787"))), Handler).serve_forever()
