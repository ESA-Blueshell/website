#!/usr/bin/env python3
# Rewrite libwebkit2gtk's compiled-in helper and injected-bundle paths from absolute to RELATIVE, so
# webkit spawns its helpers and loads the injected bundle from the (read-only) AppImage mount rather
# than a fixed system path. WebKitGTK ignores WEBKIT_EXEC_PATH in distro builds, so patching the
# library is the only cross-distro fix; a relative path (resolved against the cwd the AppRun sets to
# the mount root) avoids any writable temp dir, so there is no temp-file hijack or denial of service.
# Done in place, same length, NUL-padded.
import sys

so_path, multiarch = sys.argv[1], sys.argv[2]

# Longest first: the injected-bundle string contains the dir string as a prefix.
replacements = [
    (f"/usr/lib/{multiarch}/webkit2gtk-4.0/injected-bundle/", "usr/lib/webkit2gtk-4.0/injected-bundle/"),
    (f"/usr/lib/{multiarch}/webkit2gtk-4.0",                  "usr/lib/webkit2gtk-4.0"),
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
