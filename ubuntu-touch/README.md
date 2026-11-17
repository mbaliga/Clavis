# ubuntu-touch: the Clavis reference build as a click

**This is a reframe, not a port.** Clavis (Kotlin Multiplatform) has no Ubuntu Touch target: a click cannot run a
JVM/AWT app and Compose Multiplatform has no Native Linux target (`docs/PORTING.md` section 4). What this directory
packages is the **frozen ten-language web build** (`pipeline/pack_web.py` on the committed packs, the same page
`release.yml` publishes as `clavis-<v>-web.html`) inside a QML `WebView`. It is labelled "Clavis (reference build)" in
the manifest title, the launcher entry and the release notes. It is never "Clavis on Ubuntu Touch", and it has none of
the native app's features (17 languages, council of voices, downloads, Android/iOS/desktop storage).

Authority: owner request 2026-11-17 (build for Ubuntu Touch on GitHub Actions), which overrides the plan's proposed
no-port line. D-31 Option A (relax D-1 for exactly this artefact) is treated as taken for this build; the owner should
record it in `docs/DECISIONS.md` (see `docs/STATUS.md` section 9).

Evidence classes: **BUILT-HERE**, **CI-LANE-WRITTEN**, **CODE-ONLY**, **NEEDS-DEVICE-VALIDATION**.

```
qml/Main.qml            Window + Morph.Web WebView on ../www/index.html; refuses non-file navigation.   CODE-ONLY
manifest.json.in        click manifest (arch "all", framework ubuntu-sdk-20.04)
clavis.apparmor         policy group `webview` only, no `networking`
clavis.desktop.in       launcher entry
CMakeLists.txt          install layout (Clickable `builder: pure-qml-cmake`); APP_ID / maintainer are OWNER-FILL
clickable.yaml          Clickable 8.x project file
assets/icon.png         the desktop icon (256 px), reused
tools/stage_www.py      packs -> www/index.html (standard library only)
tools/check_click_layout.py   layout + "no networking, no Google Fonts" guard (staged dir or a built .click)
tests/                  unittest suite for the tools; webengine_boot.qml + run_webengine_boot.sh
www/                    generated, git-ignored
```

## The one change to the page

The template's head links three Google Fonts resources (`preconnect` x2 and a stylesheet). `stage_www.py` removes
exactly those three lines and nothing else (`template.html` is untouched), so the click makes **no request of its own**
(binding rule: no phoning home). Consequence: the page falls back to its local font stack; it will not look
identical to the online web build. A test asserts the output equals the packed page minus those lines.

## Build

```
python3 ubuntu-touch/tools/stage_www.py                 # needs only python3 and the committed packs (about 2 s)
pip install clickable-ut==8.10.0 && cd ubuntu-touch && clickable build      # needs Docker
python3 ubuntu-touch/tools/check_click_layout.py --click ubuntu-touch/build/all/app/*.click
```

CI: `.github/workflows/ubuntu-touch.yml` does the same on push/PR (paths-filtered), and on a `ut-v*` tag attaches the
click to a **draft** release. No secrets are involved; OpenStore re-signs on upload.

## Decisions a device will force (docs/DEVICE_CHECKLIST_UT.md)

- Whether `localStorage` persists across restart and Lomiri suspend (the learner state lives only there). Verified
  only on desktop QtWebEngine on `file://`.
- Whether `speechSynthesis` exposes any voice. The page already hides the speak button when no voice exists.
- Citation links (`target=_blank`) are refused: opening them would need the `url_dispatcher` policy group, which is
  not requested. Say so in the listing, or add the group deliberately.
- The page's JSON export is a Blob download. Whether Morph.Web hands it to the user (and where) is unknown; if not,
  add the `content_exchange` group and a handler, or keep the click read-only.
