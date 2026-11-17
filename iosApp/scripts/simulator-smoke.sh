#!/usr/bin/env bash
# Usage: simulator-smoke.sh <path/to/Clavis.app> <bundle id> <screenshot dir>
# Installs and launches the app on the first available iPhone and iPad simulators, waits 20 s, asserts the app's process
# is still running (a launch crash or a crash in the first frames exits it within seconds), and takes a screenshot.
# What this proves: the app links, installs, starts and survives its first 20 s on those two simulators. What it does
# not: that anything on screen is right (the screenshots are for a human), or anything about a real device.
set -euo pipefail
app="$1"; bundle="$2"; shots="$3"
mkdir -p "$shots"
[ -d "$app" ] || { echo "::error::no app bundle at $app"; exit 1; }
status=0
for family in iPhone iPad; do
  udid=$(xcrun simctl list devices available -j | python3 -c '
import json, sys
fam = sys.argv[1]
devs = json.load(sys.stdin)["devices"]
best = None
for runtime, items in sorted(devs.items()):
    if "iOS" not in runtime and "iPadOS" not in runtime:
        continue
    for d in items:
        if d.get("isAvailable", True) and d["name"].startswith(fam):
            best = d["udid"]
print(best or "")
' "$family")
  if [ -z "$udid" ]; then echo "::error::no available $family simulator on this runner"; status=1; continue; fi
  echo "== $family simulator $udid"
  xcrun simctl boot "$udid" 2>/dev/null || true
  xcrun simctl bootstatus "$udid" -b
  xcrun simctl install "$udid" "$app"
  pid=$(xcrun simctl launch "$udid" "$bundle" | sed -n 's/.*: *\([0-9][0-9]*\).*/\1/p')
  echo "launched pid=${pid:-?}"
  sleep 20
  if xcrun simctl spawn "$udid" launchctl list | awk -v b="UIKitApplication:$bundle" 'index($3, b) == 1 { found = 1; if ($1 != "-") alive = 1 } END { exit !(found && alive) }'; then
    echo "::notice::$family: $bundle is still running after 20 s"
  else
    echo "::error::$family: $bundle is not running 20 s after launch (crashed or exited)"
    status=1
  fi
  xcrun simctl io "$udid" screenshot "$shots/$family.png" || true
  xcrun simctl shutdown "$udid" || true
done
exit $status
