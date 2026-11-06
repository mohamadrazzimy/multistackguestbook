import json
import re
from datetime import datetime, timezone
from pathlib import Path
from threading import Lock
from flask import Flask, jsonify, request

app = Flask(__name__, static_folder="public", static_url_path="")
app.config["MAX_CONTENT_LENGTH"] = 16 * 1024
file = Path("/data/entries.jsonl")
file.parent.mkdir(parents=True, exist_ok=True)
gate = Lock()

@app.get("/")
def home():
    return app.send_static_file("index.html")

@app.get("/api/entries")
def entries():
    with gate:
        rows = [json.loads(line) for line in file.read_text(encoding="utf-8").splitlines()] \
            if file.exists() else []
    public = [{"comment": row["comment"], "createdAt": row["createdAt"]}
              for row in rows[-100:][::-1]]
    response = jsonify(public)
    response.headers["Cache-Control"] = "no-store"
    return response

@app.post("/api/entries")
def add():
    if request.mimetype != "application/x-www-form-urlencoded":
        return jsonify(error="Use a URL-encoded form."), 415
    email = request.form.get("email", "").strip()
    comment = request.form.get("comment", "").strip()
    if (len(email) > 254 or not re.fullmatch(r"[\x21-\x7e]+", email)
            or not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email)
            or not comment or len(comment.encode("utf-8")) > 2000):
        return jsonify(error="Use a valid email and a comment of 1-2000 UTF-8 bytes."), 400
    row = {"email": email, "comment": comment,
           "createdAt": datetime.now(timezone.utc).isoformat()}
    with gate:
        with file.open("a", encoding="utf-8") as stream:
            stream.write(json.dumps(row, ensure_ascii=False) + "\n")
    return jsonify(ok=True), 201

@app.errorhandler(413)
def too_large(error):
    return jsonify(error="Request too large."), 413

@app.errorhandler(OSError)
def storage_error(error):
    app.logger.exception("Storage unavailable")
    return jsonify(error="Storage unavailable."), 500
