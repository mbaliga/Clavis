#!/usr/bin/env bash
# Boots the staged page in desktop QtWebEngine under Xvfb and asserts it rendered the welcome screen, localStorage
# works on file://, and the JS console reported no error. Needs: xvfb qmlscene qml-module-qtwebengine.
# Evidence class of a pass: BUILT-HERE/CI stand-in only. It is not Morph.Web and not a Lomiri device.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
page="${1:-$here/../www/index.html}"
[ -f "$page" ] || { echo "no staged page: $page (run tools/stage_www.py)" >&2; exit 2; }
page="$(cd "$(dirname "$page")" && pwd)/$(basename "$page")"
tmpq="$(mktemp --suffix=.qml)"; trap 'rm -f "$tmpq"' EXIT
sed "s|@PAGE@|$page|" "$here/webengine_boot.qml" > "$tmpq"
out="$(QT_QUICK_BACKEND=software QTWEBENGINE_CHROMIUM_FLAGS="--no-sandbox --disable-gpu --disable-dev-shm-usage" \
  timeout 120 xvfb-run -a qmlscene "$tmpq" 2>&1 || true)"
echo "$out" | grep -E 'RESULT|JSCONSOLE|TIMEOUT' | cut -c1-300
echo "$out" | grep -q 'RESULT' || { echo "FAIL: page never reported" >&2; exit 1; }
echo "$out" | grep -q 'WELCOME' || { echo "FAIL: welcome screen not rendered" >&2; exit 1; }
echo "$out" | grep -q '"localStorage":"v"' || { echo "FAIL: localStorage on file:// not working" >&2; exit 1; }
! echo "$out" | grep -E 'JSCONSOLE (2|1) ' >/dev/null || { echo "FAIL: JS console warning/error" >&2; exit 1; }
echo "webengine boot ok"
