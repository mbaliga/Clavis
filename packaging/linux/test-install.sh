#!/usr/bin/env bash
# Offline test of install.sh. Builds a fake createDistributable-shaped app-image, tars it, installs it under a
# temporary PREFIX and checks the files, the launcher link, the .desktop Exec/Icon lines and that --uninstall
# removes only what was installed. Needs no Gradle, no network, no root.
#
#   packaging/linux/test-install.sh                      uses a generated stub app-image
#   packaging/linux/test-install.sh <tarball.tar.gz>     runs the same checks against a real tarball
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }
need() { [ -e "$1" ] || [ -L "$1" ] || fail "missing $1"; }

if [ $# -ge 1 ]; then
  [ -f "$1" ] || fail "no such tarball: $1"
  tarball="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
else
  # Same shape jpackage produces (measured: Clavis/bin/Clavis, Clavis/lib/Clavis.png).
  img="$tmp/build/Clavis"
  mkdir -p "$img/bin" "$img/lib"
  printf '#!/bin/sh\necho clavis-stub\n' > "$img/bin/Clavis"
  chmod +x "$img/bin/Clavis"
  printf 'png' > "$img/lib/Clavis.png"
  tarball="$tmp/clavis-test-linux-test.tar.gz"
  tar -czf "$tarball" -C "$tmp/build" Clavis
fi

prefix="$tmp/prefix"
mkdir -p "$prefix/share/other" "$prefix/bin"
echo keep > "$prefix/share/other/file"
echo keep > "$prefix/bin/othertool"

# The script refuses root by design; skip the install half only if the test itself runs as root.
if [ "$(id -u)" -eq 0 ]; then
  echo "SKIP: running as root; install.sh refuses root by design. Run as a normal user."
  exit 0
fi

PREFIX="$prefix" bash "$here/install.sh" "$tarball" > "$tmp/install.log"

app="$prefix/opt/clavis"
need "$app/bin/Clavis"
[ -x "$app/bin/Clavis" ] || fail "bin/Clavis not executable"
need "$prefix/bin/clavis"
[ "$(readlink "$prefix/bin/clavis")" = "$app/bin/Clavis" ] || fail "launcher link target wrong"
desk="$prefix/share/applications/clavis.desktop"
need "$desk"
grep -qx "Exec=$app/bin/Clavis" "$desk" || fail ".desktop Exec line wrong"
if [ -f "$app/lib/Clavis.png" ]; then
  grep -qx "Icon=$app/lib/Clavis.png" "$desk" || fail ".desktop Icon line wrong"
else
  grep -qx "Icon=clavis" "$desk" || fail ".desktop fallback Icon line wrong"
fi
grep -qx "Type=Application" "$desk" || fail ".desktop Type missing"

# Re-install is idempotent.
PREFIX="$prefix" bash "$here/install.sh" "$tarball" > /dev/null
need "$app/bin/Clavis"

PREFIX="$prefix" bash "$here/install.sh" --uninstall > /dev/null
[ ! -e "$app" ] || fail "app dir not removed"
[ ! -e "$prefix/bin/clavis" ] && [ ! -L "$prefix/bin/clavis" ] || fail "launcher not removed"
[ ! -e "$desk" ] || fail ".desktop not removed"
[ "$(cat "$prefix/share/other/file")" = keep ] || fail "uninstall touched an unrelated file"
[ "$(cat "$prefix/bin/othertool")" = keep ] || fail "uninstall touched an unrelated launcher"

echo "OK: install.sh install, reinstall and uninstall"
