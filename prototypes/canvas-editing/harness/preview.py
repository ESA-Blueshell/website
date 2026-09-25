#!/usr/bin/env python3
"""Build the harness into its own site folder and screenshot one route as a canvas board would draw it.

python3 preview.py NAME PORT ROUTE [--viewer board] [--theme light] [--width 390] [--steps JSON] [--no-build] [--out file.png]
Prints the board's natural height, page errors and the screenshot path.
"""
import argparse, json, os, shutil, socket, subprocess, sys, time
S = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
H = os.path.join(S, "canvas-harness")
FE = os.path.join(S, "wt-games/services/frontend")
a = argparse.ArgumentParser()
a.add_argument("name"); a.add_argument("port", type=int); a.add_argument("route")
a.add_argument("--viewer", default="visitor"); a.add_argument("--theme", default="dark"); a.add_argument("--width", type=int, default=1440)
a.add_argument("--steps", default="[]"); a.add_argument("--no-build", action="store_true"); a.add_argument("--out")
a.add_argument("--wait", type=int, default=5000)
o = a.parse_args()
site = os.path.join(S, "render", f"site-{o.name}")
build = os.path.join(site, "_build")
if not o.no_build:
    r = subprocess.run(["npx", "vite", "build", "--config", os.path.join(H, "vite.config.mjs")], cwd=FE, env={**os.environ, "HARNESS_OUT": build}, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout[-4000:], r.stderr[-4000:]); sys.exit(1)
    live = os.path.join(site, "live")
    shutil.rmtree(live, ignore_errors=True); os.makedirs(os.path.join(live, "public"))
    # The bundle names its files with a placeholder the canvas step swaps for uploads; here they sit beside it.
    open(os.path.join(live, "blueshell-live.js"), "w").write(open(os.path.join(build, "blueshell-live.js")).read().replace("__LIVE_ASSET__/", "live/"))
    shutil.copytree(os.path.join(build, "live-assets"), os.path.join(live, "live-assets"))
    if os.path.isdir(os.path.join(build, "img")): shutil.copytree(os.path.join(build, "img"), os.path.join(live, "public", "img"))
    shutil.copy(os.path.join(S, "render/site/support.js"), site)
board = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Preview</title>
<script src="./support.js"></script>
<script src="./live/blueshell-live.js"></script>
</head>
<body>
<x-dc>
<helmet>
<style>
body{{margin:0}}
</style>
</helmet>
<div data-blueshell-board="" data-route="{o.route}" data-viewer="{o.viewer}" data-theme="{o.theme}" data-steps='{o.steps}' style="width: {o.width}px;"></div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":{o.width},"height":900}}}}'>
class Component extends DCLogic {{
  renderVals() {{
    return {{}};
  }}
}}
</script>
</body>
</html>
"""
open(os.path.join(site, "Board.dc.html"), "w").write(board)
def up():
    with socket.socket() as s:
        return s.connect_ex(("127.0.0.1", o.port)) == 0
if not up():
    subprocess.Popen([sys.executable, os.path.join(S, "render/serve2.py"), site, str(o.port)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
    for _ in range(50):
        if up(): break
        time.sleep(0.1)
out = o.out or os.path.join(site, f"shot-{o.route.strip('/').replace('/', '_') or 'home'}-{o.theme}-{o.width}.png")
js = f"""
const {{chromium}} = require({json.dumps(os.path.join(FE, 'node_modules/playwright'))})
;(async () => {{
  const b = await chromium.launch(); const p = await b.newPage({{viewport: {{width: {o.width}, height: {900 if o.width > 600 else 844}}}}})
  const errs = []; p.on('pageerror', e => errs.push('pageerror: ' + e.message))
  p.on('console', m => {{ if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errs.push('console: ' + m.text()) }})
  await p.goto('http://127.0.0.1:{o.port}/Board.dc.html', {{waitUntil: 'load'}}); await p.waitForTimeout({o.wait})
  const h = await p.evaluate(() => document.querySelector('#dc-root [data-blueshell-board]')?.scrollHeight ?? 0)
  await p.screenshot({{path: {json.dumps(out)}, fullPage: true}})
  console.log(JSON.stringify({{height: h, errors: errs.slice(0, 10)}}))
  await b.close()
}})()
"""
r = subprocess.run(["node", "-e", js], capture_output=True, text=True)
print(r.stdout.strip() or r.stderr[-2000:])
print(out)
