# packaging/linux

- `install.sh` installs a `createDistributable` app-image tarball under `~/.local` (or `$PREFIX`); `--uninstall` removes it.
  The app-image is `Clavis/` with launcher `bin/Clavis` and icon `lib/Clavis.png` (jpackage names it after `packageName`);
  the installer finds the launcher under `bin/` and links it as `~/.local/bin/clavis`.
- `test-install.sh` tests it offline against a stub app-image, or against a real tarball passed as its argument.
  It must run as a normal user (install.sh refuses root). CI runs it in `ci.yml`, `desktop-linux.yml` and `release.yml`.
- `smoke-launch.sh <app-image-dir> [seconds] [screenshot.png]` starts the app-image under Xvfb and fails if it exits,
  or logs a missing class or native library. Software rendering only (Skiko falls back from GL).
- `flatpak/` holds the Flatpak manifest, `.desktop`, AppStream metainfo, `validate.sh` (desktop-file-validate,
  appstreamcli, and a pin on the sandbox permissions) and `render-manifest.sh` (turns the release-URL manifest into a
  local-tarball build manifest). The app id `io.github.mbaliga.Clavis` is a proposal awaiting the NAMES.md row.

Whether the app-image starts on a real desktop (Steam Deck, Termux:X11) is device-verified only:
`docs/DEVICE_CHECKLIST_LINUX.md`, `docs/PORTING.md` 6.3.
