#!/usr/bin/env bash
# Per-user install of a Clavis desktop app-image under ~/.local. No root, no network, no package manager.
#
# Input: the tarball made from `./gradlew :composeApp:createDistributable` (the `clavis/` app-image directory,
# i.e. bin/clavis + lib/ + the bundled JDK 17 runtime), either named on the command line or already unpacked next
# to this script. Building that tarball is docs/PORTING.md 6.3 LX2; this script does not build anything.
#
#   packaging/linux/install.sh clavis-<v>-linux-arm64.tar.gz     install from a tarball
#   packaging/linux/install.sh --uninstall                       remove what this script installed
#
# Environment: PREFIX (default ~/.local) is where files go. Nothing is written outside it.
# Status: exercised against the real x86_64 app-image built in the authoring container (docs/STATUS.md section 10); not run on a real Linux desktop; whether the app-image starts on SteamOS or under
# Termux:X11 is device-verified only (docs/PORTING.md 6.3).
set -euo pipefail

PREFIX="${PREFIX:-$HOME/.local}"
APP_DIR="$PREFIX/opt/clavis"
BIN_LINK="$PREFIX/bin/clavis"
DESKTOP_FILE="$PREFIX/share/applications/clavis.desktop"

die() { echo "install.sh: $*" >&2; exit 1; }

if [ "${1:-}" = "--uninstall" ]; then
  rm -rf "$APP_DIR"
  rm -f "$BIN_LINK" "$DESKTOP_FILE"
  echo "Removed Clavis from $PREFIX. Learner data (your XDG data directory) was not touched."
  exit 0
fi

[ "$(id -u)" -ne 0 ] || die "run as your own user, not root: this installs under $PREFIX"

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
work=""
cleanup() { [ -z "$work" ] || rm -rf "$work"; }
trap cleanup EXIT

if [ $# -ge 1 ]; then
  [ -f "$1" ] || die "no such tarball: $1"
  work="$(mktemp -d)"
  tar -xzf "$1" -C "$work"
  src="$(find "$work" -maxdepth 3 -type f -path '*/bin/*' -perm -u+x -print -quit)"
  [ -n "$src" ] || die "$1 has no executable bin/<launcher>; expected a createDistributable app-image"
  src="$(dirname "$(dirname "$src")")"
elif [ -n "$(find "$here/bin" -maxdepth 1 -type f -perm -u+x -print -quit 2>/dev/null)" ]; then
  src="$here"
elif d="$(find "$here" -maxdepth 3 -type f -path '*/bin/*' -perm -u+x -print -quit)" && [ -n "$d" ]; then
  src="$(dirname "$(dirname "$d")")"
else
  die "pass the tarball path, or run this script from the unpacked app-image"
fi

# jpackage names the launcher after the app-image (packageName "Clavis" gives bin/Clavis and lib/Clavis.png, found
# when the app-image was built here); the first executable under bin/ is the launcher whatever the case.
launcher="$(find "$src/bin" -maxdepth 1 -type f -perm -u+x -print -quit)"
[ -n "$launcher" ] || die "no executable launcher under $src/bin"
launcher="$(basename "$launcher")"

rm -rf "$APP_DIR"
mkdir -p "$APP_DIR" "$PREFIX/bin" "$PREFIX/share/applications"
cp -a "$src/." "$APP_DIR/"
ln -sf "$APP_DIR/bin/$launcher" "$BIN_LINK"

icon="clavis"
png="$(find "$APP_DIR/lib" -maxdepth 1 -type f -iname 'clavis.png' -print -quit)"
[ -z "$png" ] || icon="$png"
cat > "$DESKTOP_FILE" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Clavis
Comment=Nine languages, one lexicon: offline-first language learning from parallel comics.
Exec=$APP_DIR/bin/$launcher
Icon=$icon
Terminal=false
Categories=Education;
DESKTOP

echo "Installed Clavis to $APP_DIR (launcher: $BIN_LINK)."
case ":$PATH:" in *":$PREFIX/bin:"*) ;; *) echo "Note: $PREFIX/bin is not on your PATH; use the menu entry or the full path." ;; esac
