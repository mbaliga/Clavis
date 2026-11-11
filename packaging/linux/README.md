# packaging/linux

- `install.sh` installs a `createDistributable` app-image tarball under `~/.local` (or `$PREFIX`); `--uninstall` removes it.
- `test-install.sh` tests it offline against a stub app-image, or against a real tarball passed as its argument.
  It must run as a normal user (install.sh refuses root). CI runs it in `ci.yml`; `desktop-linux.yml` runs it on the real arm64 tarball.

Whether the app-image starts on a real desktop (Steam Deck, Termux:X11) is device-verified only: docs/PORTING.md 6.3.
