# Ubuntu Touch device checklist (owner pass, step UT1)

Everything below is **NEEDS-DEVICE-VALIDATION**: no row has been seen working on a Lomiri device. The artefact is the
**reference build** (frozen ten-language web page in a WebView), not the native app. Mark each row with the date, the
device, the Ubuntu Touch version (24.04-1.x or 2.x) and the click version.

## 0. Before anything runs

| # | Step | Who |
|---|---|---|
| 0.1 | Run **Actions > Ubuntu Touch (reference build click) > Run workflow**. Read the first red step: the Clickable step has never run (needs Docker pulling the ubuntu-sdk-20.04 image). Download the `clavis-reference-click` artefact. | owner |
| 0.2 | Install the click on the device (`clickable install` or the click file via adb/ssh). Assumption UA02 (a framework `ubuntu-sdk-20.04` click installs on 24.04-2.x) is unverified. | owner |
| 0.3 | Fill the OWNER-FILL items in `ubuntu-touch/CMakeLists.txt` (app id, maintainer) and add the NAMES.md row first (program rule R11). | owner |

## 1. Does the shell run

| # | Check | Result |
|---|---|---|
| 1.1 | The app opens from the launcher and `Morph.Web` loads the 21 MB local file (time to first paint, any blank screen) | |
| 1.2 | `import Morph.Web 0.1` + `import QtWebEngine 1.7` resolve; if QML errors appear in `~/.cache/upstart/*.log` or `clickable logs`, note them | |
| 1.3 | Layout at the device's width, rotation, on-screen keyboard over the cloze input | |
| 1.4 | The welcome screen shows "WELCOME / Nine languages. One graph." as it does on desktop | |

## 2. State and storage

| # | Check | Result |
|---|---|---|
| 2.1 | Answer a few cards, close the app, relaunch: is the state still there (`localStorage` of the WebView profile)? | |
| 2.2 | Same after the device suspends the app in the background and after a reboot | |
| 2.3 | Clear the app's data from system settings: state is gone (expected; the in-page export is the only copy, PRIVACY.md) | |
| 2.4 | The in-page JSON export: does it produce a user-visible file, and where? (Blob download handling in Morph.Web is unknown.) | |

## 3. Sound and links

| # | Check | Result |
|---|---|---|
| 3.1 | `speechSynthesis.getVoices()` count per language; is the speak button hidden when there is no voice | |
| 3.2 | Citation links do nothing (navigation refused) and the app does not crash; no network request is made (`networking` is not granted) | |

## 4. Policy

| # | Check | Result |
|---|---|---|
| 4.1 | `clickable review` / click-review output has no errors for the `webview`-only profile | |
| 4.2 | AppArmor denials in `dmesg`/`journalctl` during a session | |

## 5. Listing (owner-only)

OpenStore listing after name clearance (STATUS.md section 4). The listing must say "reference build, ten languages, not
the native app", and the N=1 statement (D-18). Nothing is submitted by this repo.
