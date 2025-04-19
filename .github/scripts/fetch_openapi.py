#!/usr/bin/env python3
"""
Grabs OpenAPI specs from every running micro‑service.yaml and:
  * writes one `<service.yaml>.yaml` per service.yaml
  * writes one `combined.yaml` that merges paths + components
"""
from pathlib import Path
from urllib.parse import urlparse
import copy, requests, yaml

##########################################################################
# 1) Tell the script where each service.yaml lives
##########################################################################
SERVICES = {
    "gateway": "http://localhost:8080",
    "api": "http://localhost:8080/api",
    "blog-parser": "http://localhost:8080/blog-parser",
    "blogs": "http://localhost:8080/blogs",
    "email-parser": "http://localhost:8080/email-parser",
    "event-parser": "http://localhost:8080/event-parser",
    "files": "http://localhost:8080/files",
    "social-media": "http://localhost:8080/social-media",
    "telemetry": "http://localhost:8080/telemetry"
}


##########################################################################
# 2) Helpers
##########################################################################
def extract_prefix(spec: dict) -> str:
    """
    Return '/email-parser' part from the first servers[].url.
    If no server path is set, return '' (no prefix).
    """
    for srv in spec.get("servers", []):
        url = srv.get("url", "")
        if not url:            # empty string → continue
            continue
        path = urlparse(url).path.rstrip("/")
        if path:               # '' if the URL ends with domain:port
            return path
    return ""

def merge_components(target: dict, src: dict, service: str) -> None:
    """
    Union‑merge components.* maps (schemas, parameters, responses, …).

    * If a component name is new  → copy it in.
    * If the name already exists:
        – if the definition is **identical**            → silently keep one copy
        – if the definition differs between services   → raise ValueError
    """
    for sect, items in src.get("components", {}).items():
        tgt_items = target.setdefault("components", {}).setdefault(sect, {})
        for name, value in items.items():
            tgt_items[name] = value

##########################################################################
# 3) Download → write individual YAMLs → merge
##########################################################################
out_dir = Path("openapi")
out_dir.mkdir(exist_ok=True)

combined: dict = {
    "openapi": "3.1.0",
    "info":    {"title": "Combined API", "version": "v1"},
    "paths":   {},                 # filled below
    "components": {},
    # keep just one server that points to gateway root
    "servers": [{"url": "/"}],
}

for name, root in SERVICES.items():
    print(f"→ Fetching {name} … ", end="", flush=True)
    r = requests.get(f"{root}/v3/api-docs", timeout=30)
    r.raise_for_status()
    spec = r.json()
    print("ok")

    # ---------- 3a) write the individual file ---------------------------
    with open(out_dir / f"{name}.yaml", "w") as f:
        yaml.safe_dump(spec, f, sort_keys=False)

    # ---------- 3b) merge ------------------------------------------------
    prefix = extract_prefix(spec)          # '' or like '/email-parser'
    for path, item in spec.get("paths", {}).items():
        full = (prefix + path) if prefix else path
        if not full.startswith("/"):
            full = "/" + full              # guarantee leading slash

        if full in combined["paths"]:
            raise ValueError(f"Path collision: {full}")
        combined["paths"][full] = copy.deepcopy(item)

    merge_components(combined, spec, name)

# 4) write the combined file
with open(out_dir / "combined.yaml", "w") as f:
    yaml.safe_dump(combined, f, sort_keys=False)

print(f"✅  Wrote {len(combined['paths'])} paths to {out_dir/'combined.yaml'}")