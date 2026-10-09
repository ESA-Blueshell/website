#!/usr/bin/env bash
# Package the already-built Linux binary (build/bin/blueshell-pinger) into a self-contained AppImage.
# Run from services/pinger-app after `wails build -platform linux/<arch>`, with these on the system:
# wget, patchelf, imagemagick, python3, dpkg-dev, fontconfig, fonts-dejavu-core, libfuse2, and the
# libwebkit2gtk-4.0-dev package (for the webkit helper processes).
#
# Why this is not a plain `appimagetool AppDir`: WebKitGTK is not present on every distro (its absence
# is the whole reason the app ships its own), so the AppImage must carry webkit and its dependency
# closure. Two things make that hard, and this script handles both:
#   1. The GL/EGL/GBM/DRM driver stack must come from the HOST (it has to match the host GPU driver),
#      so it is deliberately NOT bundled -- bundling it aborts EGL with "EGL_BAD_PARAMETER".
#   2. webkit spawns helper processes from a path compiled into the library and ignores
#      WEBKIT_EXEC_PATH, so that path is rewritten (patch-webkit.py) to a fixed dir the AppRun fills
#      with symlinks to the bundled helpers. See wailsapp/wails#6213.
set -euo pipefail

ASSET="${1:?pass the output AppImage filename}"
HERE="$(cd "$(dirname "$0")" && pwd)"
case "$(uname -m)" in
  x86_64)  LD_ARCH=x86_64 ;;
  aarch64) LD_ARCH=aarch64 ;;
  *) echo "unsupported arch $(uname -m)"; exit 2 ;;
esac
MA="$(dpkg-architecture -qDEB_HOST_MULTIARCH)"

rm -rf AppDir
mkdir -p AppDir/usr/bin
cp build/bin/blueshell-pinger AppDir/usr/bin/
# linuxdeploy rejects non-standard icon sizes; 512x512 is the largest it accepts.
convert build/appicon.png -resize 512x512 blueshell-pinger.png
cat > blueshell-pinger.desktop <<'EOF'
[Desktop Entry]
Name=Blueshell Pinger
Exec=blueshell-pinger
Icon=blueshell-pinger
Type=Application
Categories=Utility;
EOF

# Build tooling. The gtk plugin is shell code run here, so it is pinned to an immutable commit. The
# AppImage runtimes (linuxdeploy, appimagetool) publish only a rolling "continuous" build upstream, so
# they cannot be pinned to a version; they run on the trusted release runner, never on a contributor's
# machine.
GTK_PLUGIN_COMMIT=7a3fbc31a9e5075073ff8790f26effbac5f84453
wget -q "https://github.com/linuxdeploy/linuxdeploy/releases/download/continuous/linuxdeploy-${LD_ARCH}.AppImage"
wget -q "https://raw.githubusercontent.com/linuxdeploy/linuxdeploy-plugin-gtk/${GTK_PLUGIN_COMMIT}/linuxdeploy-plugin-gtk.sh"
chmod +x "linuxdeploy-${LD_ARCH}.AppImage" linuxdeploy-plugin-gtk.sh

# Populate AppDir with the binary's libs plus the GTK runtime (themes, pixbuf loaders, gio modules).
export DEPLOY_GTK_VERSION=3
"./linuxdeploy-${LD_ARCH}.AppImage" --appimage-extract-and-run \
  --appdir AppDir -e AppDir/usr/bin/blueshell-pinger -d blueshell-pinger.desktop -i blueshell-pinger.png --plugin gtk

# Force-bundle the rest of the webkit/GTK closure (fontconfig, freetype, harfbuzz, ...) for leaner
# distros -- but keep glibc + the GPU/driver stack from the host (see the header).
keep_from_host='^(ld-linux|ld-musl|libc|libm|libdl|libpthread|librt|libresolv|libnsl|libutil|libBrokenLocale|libanl|libmvec)\.'
gpu_from_host='^(libEGL|libGLX|libGLdispatch|libGL|libOpenGL|libgbm|libdrm|libglapi|libgallium|libvulkan|libVkLayer|libva|libwayland-egl|libxcb-dri2|libxcb-dri3|libxcb-glx|libxcb-present|libxshmfence|libnvidia|libcuda)'
ldconfig
resolve_soname() { ldconfig -p | awk -v s="$1" '$1==s{print $NF; exit}'; }
for pass in 1 2 3 4 5 6 7 8; do
  # Bundled libs carry rpath $ORIGIN, so ldd reports their unbundled deps as "<soname> => not found";
  # capture both the resolved paths and the not-found sonames.
  needed="$( { ldd AppDir/usr/bin/blueshell-pinger; for so in AppDir/usr/lib/*.so*; do ldd "$so"; done; } 2>/dev/null \
    | awk '/=> \//{print $3} /not found/{print $1}' | sort -u )"
  added=0
  while IFS= read -r item; do
    [ -n "$item" ] || continue
    case "$item" in
      /*) src="$item"; base="$(basename "$item")" ;;
      *)  base="$item"; src="$(resolve_soname "$item")" ;;
    esac
    echo "$base" | grep -qE "$keep_from_host" && continue
    echo "$base" | grep -qE "$gpu_from_host" && continue
    [ -e "AppDir/usr/lib/$base" ] && continue
    [ -n "$src" ] && [ -e "$src" ] || { echo "  UNRESOLVED $item"; continue; }
    cp -L "$src" "AppDir/usr/lib/$base" && added=$((added+1))
  done <<< "$needed"
  echo "force-bundle pass $pass: added $added"
  [ "$added" -eq 0 ] && break
done

# Force-bundled libs were copied raw (no rpath), so a dep of such a lib is not found at $ORIGIN.
# Give every bundled lib rpath $ORIGIN so each finds its siblings in usr/lib.
for so in AppDir/usr/lib/*.so*; do
  [ -L "$so" ] && continue
  patchelf --set-rpath '$ORIGIN' "$so" 2>/dev/null || true
done

# Bundle the webkit helper processes + injected bundle, rpath them back to usr/lib, then rewrite the
# paths compiled into libwebkit2gtk to a fixed dir (the AppRun fills it with symlinks at launch).
rm -rf AppDir/usr/lib/webkit2gtk-4.0
cp -r "/usr/lib/$MA/webkit2gtk-4.0" AppDir/usr/lib/webkit2gtk-4.0
for f in AppDir/usr/lib/webkit2gtk-4.0/WebKit* AppDir/usr/lib/webkit2gtk-4.0/MiniBrowser; do
  [ -f "$f" ] && patchelf --set-rpath '$ORIGIN/..' "$f" 2>/dev/null || true
done
[ -f AppDir/usr/lib/webkit2gtk-4.0/injected-bundle/libwebkit2gtkinjectedbundle.so ] && \
  patchelf --set-rpath '$ORIGIN/../..' AppDir/usr/lib/webkit2gtk-4.0/injected-bundle/libwebkit2gtkinjectedbundle.so 2>/dev/null || true
python3 "$HERE/patch-webkit.py" AppDir/usr/lib/libwebkit2gtk-4.0.so.37 "$MA"

# fontconfig config + a base font, so text renders and the "Cannot load default config" warning goes.
mkdir -p AppDir/usr/share/fonts/truetype/dejavu
cp /usr/share/fonts/truetype/dejavu/*.ttf AppDir/usr/share/fonts/truetype/dejavu/

# linuxdeploy's AppRun sources only the gtk hook, so add the webkit-helper symlinks, software-render
# fallbacks and a runtime fontconfig file before the exec line.
python3 - <<'PYEOF'
p = "AppDir/AppRun"
s = open(p).read()
inject = r'''
# Blueshell: webkit's accelerated compositor needs EGL/GPU and aborts ("EGL_BAD_PARAMETER") on
# headless sessions, VMs and older GPUs. Disable it (and the dmabuf renderer) so the window comes up
# everywhere; the pinger UI is static and does not need GPU compositing.
export WEBKIT_DISABLE_COMPOSITING_MODE=1
export WEBKIT_DISABLE_DMABUF_RENDERER=1
# libwebkit2gtk's helper/injected-bundle paths were patched to this fixed dir; fill it with symlinks
# to the bundled helpers. webkit EXECUTES from here, and the path is world-writable (/tmp), so fail
# closed: refuse a pre-existing dir we do not own (it could point webkit at a planted binary), and
# create ours atomically with mkdir (no -p), which errors rather than reusing a racing attacker's dir.
# The dir name is fixed because it is compiled into libwebkit (patch-webkit.py); it cannot carry the
# uid. So guard by ownership instead.
_wk="/tmp/bspinger-wk2gtk40"
if [ -e "$_wk" ]; then
  if [ -O "$_wk" ] && [ ! -L "$_wk" ]; then rm -rf "$_wk"; else
    echo "pinger: $_wk exists and is not ours; refusing to start" >&2; exit 1
  fi
fi
( umask 077 && mkdir "$_wk" && mkdir "$_wk/injected-bundle" ) || { echo "pinger: cannot create $_wk" >&2; exit 1; }
ln -sf "$this_dir"/usr/lib/webkit2gtk-4.0/WebKit* "$_wk"/ 2>/dev/null || true
ln -sf "$this_dir"/usr/lib/webkit2gtk-4.0/MiniBrowser "$_wk"/ 2>/dev/null || true
ln -sf "$this_dir"/usr/lib/webkit2gtk-4.0/injected-bundle/*.so "$_wk"/injected-bundle/ 2>/dev/null || true
_fc="$(mktemp -d "${TMPDIR:-/tmp}/bspinger-fc.XXXXXX")"
cat > "$_fc/fonts.conf" <<FCEOF
<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "fonts.dtd">
<fontconfig>
  <dir>$this_dir/usr/share/fonts</dir>
  <cachedir>$_fc/cache</cachedir>
  <include ignore_missing="yes">/etc/fonts/fonts.conf</include>
</fontconfig>
FCEOF
export FONTCONFIG_FILE="$_fc/fonts.conf"
'''
marker = 'exec "$this_dir"/AppRun.wrapped "$@"'
assert marker in s, "AppRun exec line not found"
open(p, "w").write(s.replace(marker, inject + "\n" + marker))
PYEOF

wget -q "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-${LD_ARCH}.AppImage"
chmod +x "appimagetool-${LD_ARCH}.AppImage"
ARCH="${LD_ARCH}" "./appimagetool-${LD_ARCH}.AppImage" --appimage-extract-and-run AppDir "$ASSET"
