#!/usr/bin/env python3
# Rewrite libwebkit2gtk's compiled-in helper and injected-bundle paths to a fixed, writable location
# the AppRun fills with symlinks. WebKitGTK ignores WEBKIT_EXEC_PATH in distro builds, so the only
# cross-distro fix is to change the path baked into the library. Done in place, same length, NUL-padded.
import sys

so_path, multiarch = sys.argv[1], sys.argv[2]
FIXED_DIR = "/tmp/bspinger-wk2gtk40"

# Longest first: the injected-bundle string contains the dir string as a prefix.
replacements = [
    (f"/usr/lib/{multiarch}/webkit2gtk-4.0/injected-bundle/", f"{FIXED_DIR}/injected-bundle/"),
    (f"/usr/lib/{multiarch}/webkit2gtk-4.0",                  FIXED_DIR),
]

data = open(so_path, "rb").read()
for old, new in replacements:
    ob, nb = old.encode(), new.encode()
    if len(nb) > len(ob):
        sys.exit(f"replacement longer than original: {new!r} > {old!r}")
    padded = nb + b"\0" * (len(ob) - len(nb))
    n = data.count(ob)
    if n == 0:
        sys.exit(f"string not found in {so_path}: {old!r}")
    data = data.replace(ob, padded)
    print(f"patched {old!r} -> {new!r} ({n} occurrence(s))")
open(so_path, "wb").write(data)
