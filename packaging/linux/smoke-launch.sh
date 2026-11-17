#!/usr/bin/env bash
# Launch a Clavis app-image under a virtual X server and assert the process is still alive after a delay.
#   smoke-launch.sh <app-image-dir> [seconds=20] [screenshot.png]
# Needs Xvfb (xvfb-run is not used: it hides the child's exit status). Software rendering only: Skiko logs
# "Cannot create Linux GL context" and falls back, which is expected on a runner with no GPU and is NOT a failure.
# Proves the app-image starts and keeps running; says nothing about a real GPU, Wayland, audio or TTS.
set -euo pipefail
img="$(readlink -f "$1")"; secs="${2:-20}"; shot="${3:-}"
launcher="$(find "$img/bin" -maxdepth 1 -type f -perm -u+x -print -quit)"
[ -n "$launcher" ] || { echo "no launcher under $img/bin" >&2; exit 1; }
command -v Xvfb >/dev/null || { echo "Xvfb not installed" >&2; exit 1; }
home="$(mktemp -d)"; disp=":$((90 + RANDOM % 50))"
cleanup() { [ -z "${app:-}" ] || kill "$app" 2>/dev/null || true; [ -z "${xv:-}" ] || kill "$xv" 2>/dev/null || true; rm -rf "$home"; }
trap cleanup EXIT
Xvfb "$disp" -screen 0 1280x800x24 >/dev/null 2>&1 & xv=$!
sleep 2
HOME="$home" DISPLAY="$disp" "$launcher" > "$home/app.log" 2>&1 & app=$!
sleep "$secs"
if ! kill -0 "$app" 2>/dev/null; then
  echo "FAIL: Clavis exited within ${secs}s"; tail -30 "$home/app.log"; exit 1
fi
if grep -q 'NoClassDefFoundError\|ClassNotFoundException\|UnsatisfiedLinkError' "$home/app.log"; then
  echo "FAIL: missing class or native library"; tail -30 "$home/app.log"; exit 1
fi
if [ -n "$shot" ] && command -v import >/dev/null; then DISPLAY="$disp" import -window root "$shot" || true; fi
echo "OK: still running after ${secs}s ($(basename "$launcher"))"
