#!/usr/bin/env bash
# Selects the newest Xcode 26.x installed on the runner (the owner's toolchain ruling, plan OQ-38, names an explicitly
# selected Xcode 26.x). Fails loudly, listing what IS installed, instead of silently building with the image default.
set -euo pipefail
latest=$(ls -d /Applications/Xcode_26*.app 2>/dev/null | sort -V | tail -n 1 || true)
if [ -z "$latest" ]; then
  echo "::error::no /Applications/Xcode_26*.app on this runner image; installed:"
  ls -d /Applications/Xcode*.app || true
  exit 1
fi
sudo xcode-select -s "$latest/Contents/Developer"
echo "Selected $latest"
xcodebuild -version
xcrun --sdk iphonesimulator --show-sdk-version
