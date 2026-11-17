# Linux device checklist (owner pass, step LX4)

What the authoring container did (x86_64, Xvfb, software rendering, no GPU, no audio device, no Wayland, no real
desktop session) is recorded in `STATUS.md` section 10. Every row below that is not marked otherwise is
**NEEDS-DEVICE-VALIDATION**. Mark each row with the date, the machine, the distro and the artefact (tarball, `.deb`,
Flatpak). Targets named by the plan: Steam Deck (Desktop Mode, then Gaming Mode), the RedMagic under Termux:X11, and
an ordinary Plasma and GNOME desktop for HiDPI.

## 0. Get the artefact

| # | Step |
|---|---|
| 0.1 | Run **Actions > Desktop Linux > Run workflow** (or push to a public `main`). Read the first red step: the arm64 job, the Xvfb launch step and `flatpak-builder` have never run on a hosted runner. |
| 0.2 | Download `clavis-linux-x86_64` or `clavis-linux-arm64` (tarball, `.deb`, jar, a launch screenshot) and, for Flatpak, `clavis-flatpak-<arch>` (`io.github.mbaliga.Clavis-<arch>.flatpak`). |
| 0.3 | Verify with `SHA256SUMS.txt` when the files come from a tagged Release (`release.yml`). |

## 1. Install and start

| # | Check | Result |
|---|---|---|
| 1.1 | Tarball: `tar -xzf clavis-<v>-linux-<arch>.tar.gz && packaging/linux/install.sh <tarball>` (as your own user). A menu entry appears; `~/.local/bin/clavis` starts it. (`install.sh` itself, run on the x86_64 tarball in the container: BUILT-HERE; the desktop-menu entry appearing: unverified.) | |
| 1.2 | The icon shows in the menu and on the window (the `.desktop` file points at `lib/Clavis.png`). | |
| 1.3 | `.deb`: `sudo apt install ./clavis_<v>_<arch>.deb`, then `clavis`. Uninstall leaves the learner data. | |
| 1.4 | Flatpak: `flatpak install --user ./io.github.mbaliga.Clavis-<arch>.flatpak`, then `flatpak run io.github.mbaliga.Clavis`. | |
| 1.5 | `install.sh --uninstall` removes only what it installed (BUILT-HERE on a stub and on the real tarball). | |

## 2. Rendering and input

| # | Check | Result |
|---|---|---|
| 2.1 | First paint and frame rate on the Cosmos and Paths canvases. Skiko logs "Cannot create Linux GL context" and falls back to software where there is no GL; on the Deck and a normal desktop it should use GL. Note which. | |
| 2.2 | HiDPI: 1x, 1.5x, 2x on Plasma and on GNOME (window size, text sharpness, comic pages). | |
| 2.3 | Wayland session (XWayland) and X11 session both start. A native Wayland path is not claimed. | |
| 2.4 | Steam Deck Desktop Mode: touch and trackpad input, on-screen keyboard over the cloze input. Gaming Mode (add as a non-Steam game under gamescope): does it launch and take input? | |
| 2.5 | RedMagic under Termux:X11 (software GL, no Wayland, no Vulkan): does the app-image start, and how slow are the canvases? | |
| 2.6 | `clavis` started through the `~/.local/bin` symlink finds its runtime (the jpackage launcher resolves its own directory; only the direct path was run in the container). | |

## 3. Speech, audio and files

| # | Check | Result |
|---|---|---|
| 3.1 | Recorded word audio plays after the one-tap download (javax.sound; Ogg only through the vorbisspi decoder). Flatpak: needs the PulseAudio socket the manifest grants. | |
| 3.2 | TTS: on the host the desktop layer uses Piper, `spd-say` or `espeak-ng`, whichever exists. Inside the Flatpak none of them is present (nothing is bundled: espeak-ng is GPL, D-8). Confirm the Speak control is hidden or degrades honestly rather than failing. | |
| 3.3 | Import and Export file dialogs (AWT `FileDialog`): they open, can reach `~`, and the exported JSON re-imports. Flatpak: no `--filesystem` is granted, so check whether the dialog is usable (a portal is not wired); this may need a `--filesystem=xdg-documents` decision recorded in PRIVACY.md. | |
| 3.4 | Learner state lands where `PRIVACY.md` says: `~/.local/share/Clavis/` (tarball, `.deb`) and `~/.var/app/io.github.mbaliga.Clavis/data/Clavis/` (Flatpak). Quit and relaunch: state is kept. | |

## 4. Network honesty

| # | Check | Result |
|---|---|---|
| 4.1 | From a fresh start, watch the network (`ss -tp`, or the Flatpak with `--unshare=network` as a control): there is no connection until you tap Play/Download. | |
| 4.2 | With `flatpak run --unshare=network io.github.mbaliga.Clavis`, everything but the download works. | |

## 5. Owner decisions this pass feeds

Flatpak app id (NAMES.md row first, plan rule R11; the manifest proposes `io.github.mbaliga.Clavis`), Flathub submission
(needs a public repository and id verification), whether to keep the `--share=network` permission, and whether the
Flatpak needs a document portal for Import/Export.
