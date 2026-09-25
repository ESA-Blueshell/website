#!/usr/bin/env python3
"""Build the live boards: python3 gen_boards.py [--no-build] [--only Stem,Stem]

Builds the harness, measures every board's natural height in the running app, and writes
canvas-live/project/: one Live<Stem><View>.dc.html per screen and view, the live bundle under
live/, and boards.json describing them for the canvas index.
"""
import argparse, json, os, shutil, socket, subprocess, sys, time
from screens import PAGES, EDITING, VIEWS
S = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
H = os.path.join(S, "canvas-harness")
FE = os.path.join(S, "wt-games/services/frontend")
SITE = os.path.join(S, "render/site-main")
OUT = os.path.join(S, "canvas-live/project")
PORT = 8770
SKIP = (".pdn", ".eot")

a = argparse.ArgumentParser(); a.add_argument("--no-build", action="store_true"); a.add_argument("--only", default="")
o = a.parse_args()
only = set(filter(None, o.only.split(",")))

def build():
    b = os.path.join(SITE, "_build")
    r = subprocess.run(["npx", "vite", "build", "--config", os.path.join(H, "vite.config.mjs")], cwd=FE, env={**os.environ, "HARNESS_OUT": b}, capture_output=True, text=True)
    if r.returncode: print(r.stdout[-3000:], r.stderr[-3000:]); sys.exit(1)
    live = os.path.join(SITE, "live")
    shutil.rmtree(live, ignore_errors=True); os.makedirs(os.path.join(live, "public"))
    code = open(os.path.join(b, "blueshell-live.js")).read()
    # The canvas copy keeps the placeholders for the upload step; the local one points beside itself.
    open(os.path.join(S, "canvas-live/blueshell-live.template.js"), "w").write(code)
    open(os.path.join(live, "blueshell-live.js"), "w").write(code.replace("__LIVE_ASSET__/", "live/"))
    shutil.copytree(os.path.join(b, "live-assets"), os.path.join(live, "live-assets"))
    if os.path.isdir(os.path.join(b, "img")): shutil.copytree(os.path.join(b, "img"), os.path.join(live, "public/img"))
    shutil.copy(os.path.join(S, "render/site/support.js"), SITE)

def board_html(title, route, viewer, theme, steps, width, height):
    fixed = f" height: {height}px; overflow-x: hidden; overflow-y: auto;" if height else ""
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>{title}</title>
<script src="./support.js"></script>
<script src="./live/blueshell-live.js"></script>
</head>
<body>
<x-dc>
<helmet>
<style>
body{{margin:0}}
[data-blueshell-board]{{scrollbar-width:none}}
[data-blueshell-board]::-webkit-scrollbar{{display:none}}
</style>
</helmet>
<div data-blueshell-board="" data-route="{route}" data-viewer="{viewer}" data-theme="{theme}" data-steps='{json.dumps(steps)}' style="width: {width}px;{fixed}"></div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":{width},"height":{height or 900}}}}}'>
class Component extends DCLogic {{
  renderVals() {{
    return {{}};
  }}
}}
</script>
</body>
</html>
"""

screens = []
for pid, name, rows in PAGES:
    for row in rows + EDITING.get(pid, []):
        if only and row[0] not in only: continue
        screens.append((pid, name) + row)

if not o.no_build: build()
os.makedirs(os.path.join(SITE, "m"), exist_ok=True)
jobs = []
for pid, pname, stem, label, route, viewer, steps in screens:
    for suffix, width, theme, _ in VIEWS:
        f = f"m/{stem}{suffix}.dc.html"
        open(os.path.join(SITE, f), "w").write(board_html(label, route, viewer, theme, steps, width, 0).replace("./support.js", "../support.js").replace("./live/", "../live/"))
        jobs.append({"file": f, "width": width})

def up():
    with socket.socket() as s: return s.connect_ex(("127.0.0.1", PORT)) == 0
if not up():
    subprocess.Popen([sys.executable, os.path.join(S, "render/serve2.py"), SITE, str(PORT)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
    while not up(): time.sleep(0.1)

js = """
const {chromium} = require(%s)
const jobs = %s
;(async () => {
  const b = await chromium.launch(); const out = {}
  const run = async job => {
    const p = await b.newPage({viewport: {width: job.width, height: job.width > 600 ? 900 : 844}})
    const errs = []; p.on('pageerror', e => errs.push(e.message))
    await p.goto('http://127.0.0.1:%d/' + job.file, {waitUntil: 'load'}); await p.waitForTimeout(6500)
    out[job.file] = {height: await p.evaluate(() => document.querySelector('#dc-root [data-blueshell-board]')?.scrollHeight ?? 0), errors: errs.slice(0, 3)}
    await p.close()
  }
  const queue = [...jobs]
  await Promise.all(Array.from({length: 6}, async () => { while (queue.length) await run(queue.shift()) }))
  console.log(JSON.stringify(out)); await b.close()
})()
""" % (json.dumps(os.path.join(FE, "node_modules/playwright")), json.dumps(jobs), PORT)
r = subprocess.run(["node", "-e", js], capture_output=True, text=True)
measured = json.loads(r.stdout.strip().splitlines()[-1])

os.makedirs(OUT, exist_ok=True)
shutil.rmtree(os.path.join(OUT, "live"), ignore_errors=True)
meta_path = os.path.join(S, "canvas-live/boards.json")
meta = json.load(open(meta_path)) if os.path.exists(meta_path) and only else {}
for pid, pname, stem, label, route, viewer, steps in screens:
    for suffix, width, theme, view in VIEWS:
        m = measured[f"m/{stem}{suffix}.dc.html"]
        height = max(m["height"], 844 if width < 600 else 900)
        height = min(8000, (height + 9) // 10 * 10)
        file = f"Live{stem}{suffix}.dc.html"
        title = f"{label}, {view}"
        open(os.path.join(OUT, file), "w").write(board_html(title, route, viewer, theme, steps, width, height))
        meta[file] = {"page": pid, "pageName": pname, "stem": stem, "label": label, "view": view, "w": width, "h": height, "title": title, "errors": m["errors"]}
json.dump(meta, open(meta_path, "w"), indent=1)
bad = {k: v["errors"] for k, v in meta.items() if v["errors"]}
print(len(meta), "boards;", "errors:", json.dumps(bad)[:2000])
