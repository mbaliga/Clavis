#!/usr/bin/env bash
# Prints `marketing=<x.y.z>` and `build=<n>` for the iOS build, derived from the repository's single version source
# (gradle/version.gradle.kts: -Pclavis.version > CLAVIS_VERSION env > ClavisVersion.NAME). The Apple marketing version
# must be three dot-separated integers, so a pre-release suffix is dropped (0.2.0-rc.1 -> 0.2.0) and the build number
# (CFBundleVersion, must increase per upload for one marketing version) carries the Android-scheme versionCode, which
# already encodes the pre-release number.
set -euo pipefail
cd "$(dirname "$0")/../.."
out=$(./gradlew -q :composeApp:clavisVersion --no-daemon --console=plain 2>/dev/null)
name=$(printf '%s\n' "$out" | sed -n 's/.*versionName[^0-9]*\([0-9][0-9A-Za-z.\-]*\).*/\1/p' | head -n 1)
code=$(printf '%s\n' "$out" | sed -n 's/.*versionCode[^0-9]*\([0-9][0-9]*\).*/\1/p' | head -n 1)
[ -n "$name" ] && [ -n "$code" ] || { echo "could not parse :composeApp:clavisVersion output:" >&2; printf '%s\n' "$out" >&2; exit 1; }
echo "marketing=${name%%-*}"
echo "build=$code"
