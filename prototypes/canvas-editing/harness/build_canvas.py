#!/usr/bin/env python3
"""Write canvas-live/project/canvas.json from the latest canvas index and the live boards.

python3 build_canvas.py <latest canvas.json>
Prints the files to remove from the canvas (the old static captures) as JSON on the last line.
"""
import json, re, sys, os
from screens import PAGES, VIEWS
S = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
c = json.load(open(sys.argv[1]))
meta = json.load(open(os.path.join(S, "canvas-live/boards.json")))

COL = {"desktop dark": 0, "phone dark": 1520, "desktop light": 1990, "phone light": 3510}
NOTE_X = 4100
ROW_GAP = 400

PROPOSALS = {
    "committees": "Proposed here: committees get their own tab, listing every committee running now; a committee is edited on its own page with a live preview instead of in a dialog.",
    "association": "Proposed here: the board and its members are edited on their own pages with a live preview instead of in dialogs.",
    "casual": "Proposed here: one game edit page for casual and competition, with both previews one below the other, a separate competition intro and separate Games and Esports channels.",
    "competition": "Proposed here: the same shared game edit page, and season and team editing built from the island fields laid out like the event form.",
    "events": "The event form is the reference the other edit pages follow.",
}
INTRO = ("Live pages: the site as built, running in each board against sample data (the dev seed, with today set to "
         "Monday 21 September). Scroll, drag the reels, open the menus, follow links and fill in forms; nothing is saved. "
         "Every page proposes one change: Committees has its own tab in the navbar. {extra}")

old = c["boards"]
keep_pages = {"account", "backup"}
removed = [k for k, v in old.items() if v.get("page") not in keep_pages]
boards = {k: v for k, v in old.items() if v.get("page") in keep_pages}
notes = {k: v for k, v in c["notes"].items() if v.get("page") in keep_pages}

HOME = {"Main": "desktop dark", "Mobile": "phone dark", "Light": "desktop light", "MobileLight": "phone light"}

def view_of(name):
    stem = name[:-len(".dc.html")]
    if stem in HOME: return "Main", HOME[stem]
    for suffix, view in (("PhoneLight", "phone light"), ("MobileLight", "phone light"), ("Phone", "phone dark"), ("Mobile", "phone dark"), ("Light", "desktop light")):
        if stem.endswith(suffix) and stem != suffix:
            return stem[: -len(suffix)], view
    if stem == "Light": return "Main", "desktop light"
    if stem == "Mobile": return "Main", "phone dark"
    return stem, "desktop dark"

def relayout(page):
    """Rows by screen, the four views left to right, grouped under the title notes they sat under;
    stickies go to the right of their group, in reading order."""
    mine = sorted([k for k, v in boards.items() if v["page"] == page], key=lambda k: (boards[k]["y"], boards[k]["x"]))
    page_notes = sorted([k for k, v in notes.items() if v["page"] == page], key=lambda k: (notes[k]["y"], notes[k]["x"]))
    titles = [k for k in page_notes if notes[k].get("kind") == "title1"]
    group_of = lambda y: max([t for t in titles if notes[t]["y"] <= y + 1] or titles[:1] or [None], key=lambda t: notes[t]["y"] if t else 0)
    groups = {t: [] for t in titles}
    groups.setdefault(None, [])
    for k in mine: groups[group_of(boards[k]["y"])].append(k)
    sticky_groups = {t: [] for t in groups}
    for k in page_notes:
        if notes[k].get("kind") != "title1": sticky_groups[group_of(notes[k]["y"])].append(k)
    y = 0
    for t in [None] + titles:
        if t is None and not groups[None] and not sticky_groups[None]: continue
        if t: notes[t].update(x=0, y=y); y += 300
        rows, order = {}, []
        for k in groups[t]:
            base, view = view_of(k)
            if base not in rows or view in rows[base]:
                base = base if base not in rows else base + "#" + str(len(order))
                rows[base] = {}; order.append(base)
            rows[base][view] = k
        ny = y
        for k in sticky_groups[t]:
            notes[k].update(x=NOTE_X, y=ny)
            ny += int(notes[k].get("w", 400) * 4 / 3) + 80
        for base in order:
            for view, k in rows[base].items():
                boards[k].update(x=COL[view], y=y)
            y += max(boards[k]["h"] for k in rows[base].values()) + ROW_GAP
        y = max(y, ny) + 200

relayout("account")
relayout("backup")

for pid, pname, _ in PAGES:
    mine = {k: v for k, v in meta.items() if v["page"] == pid}
    notes[f"{pid}-page-title"] = {"kind": "title1", "page": pid, "text": pname, "maxW": 3900, "x": 0, "y": -900}
    notes[f"{pid}-page-intro"] = {"color": "blue", "page": pid, "w": 1100, "x": 0, "y": -700, "text": INTRO.format(extra=PROPOSALS.get(pid, "")).strip()}
    stems = []
    for k, v in mine.items():
        if v["stem"] not in stems: stems.append(v["stem"])
    y = 0
    for stem in stems:
        row = {k: v for k, v in mine.items() if v["stem"] == stem}
        label = next(iter(row.values()))["label"]
        notes[f"{pid}-row-{stem}"] = {"kind": "title1", "page": pid, "text": label, "maxW": 3900, "x": 0, "y": y - 260}
        for k, v in row.items():
            boards[k] = {"x": COL[v["view"]], "y": y, "w": v["w"], "h": v["h"], "page": pid, "title": v["title"], "is_interactive": True}
        y += max(v["h"] for v in row.values()) + ROW_GAP + 160

c["pages"] = [{"id": pid, "name": pname} for pid, pname, _ in PAGES] + [p for p in c["pages"] if p["id"] in keep_pages]
c["boards"] = boards
c["notes"] = notes
c["order"] = [k for k in c["order"] if k in boards] + [k for k in boards if k not in c["order"]]
c["launch"] = {"view": "canvas", "page": "home"}
json.dump(c, open(os.path.join(S, "canvas-live/project/canvas.json"), "w"), ensure_ascii=False, indent=1)
print(json.dumps(sorted(set(removed) - set(boards))))
