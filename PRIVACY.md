# Privacy

Clavis collects no data. There is no account, no server, no sync, no analytics, no crash reporting, no advertising and no third-party SDK that phones home. This page says what the app stores on your device, where, and the three situations in which bytes can leave it — all of them started by you.

## What the app stores

One JSON document per profile, `clavis-state.json`, containing your learning state: enabled languages and their productive/receptive setting, cards and their spaced-repetition schedule, the review log, baseline-test results, Paths history, settings, and the notebook of words you looked up or imported. Downloaded content packs and voices, when that feature ships, are stored next to it as plain files. Nothing is encrypted at rest beyond what the operating system provides for per-app storage; nothing in the document identifies you unless you type it in.

## Where it lives

| Platform | Location |
|---|---|
| Android | the app's private storage (`Context.filesDir`, i.e. `/data/data/io.github.mbaliga.clavis/files/`), readable only by Clavis |
| Linux | `$XDG_DATA_HOME/clavis/` (default `~/.local/share/clavis/`) |
| macOS | `~/Library/Application Support/Clavis/` |
| Windows | `%APPDATA%\Clavis\` |

The desktop paths are the ones the packaged app resolves; the debug/desktop entry points name the directory they use when started from a terminal.

## What can leave the device, and only when you act

1. **Export.** You → Export writes your state document to a file you choose. Where that file goes afterwards is up to you; it contains everything in the section above.
2. **Feedback.** The feedback flow, when present, opens your system share sheet with text you see and edit first; Clavis does not send it anywhere itself (docs/DECISIONS.md D-11).
3. **Optional downloads.** When you start a download of an optional content pack, a Piper voice or a word recording, the app fetches public files over HTTPS from the source named in that dialog. Such a request carries what any HTTPS download carries (your IP address and a user agent) and nothing else; Clavis keeps only the downloaded file. Until that feature ships the Android build has no `INTERNET` permission at all.

## Android backup

The current build leaves Android's default `allowBackup` behaviour on. If you have Android's device backup enabled, the system — not Clavis — may include the app's files in your device backup according to your Android settings; turning off app backup in Android excludes Clavis. An explicit exclusion of the learner-state document from cloud backup is on the pre-release checklist ([docs/RELEASE.md](docs/RELEASE.md)).

## Deleting your data

Uninstalling the Android app removes its private storage. On desktop, delete the directory listed above, or use Settings → Reset everything inside the app (it asks `Erase all local progress?` first). Export beforehand if you want to keep a copy.

## Children

Clavis is not designed for children or classrooms: there is no age gate, no safeguarding model and no age-appropriate review of the comic corpus (docs/04-personas.md, “Who this is not for”).

## Store declarations

Google Play data-safety form: no data collected, no data shared, no security-practice claims that depend on transmission; the same facts are repeated in the store listing ([docs/STORE-LISTING.md](docs/STORE-LISTING.md)).

## Changes

Changes to what the app stores or transmits are recorded in [CHANGELOG.md](CHANGELOG.md) under the release that made them. Questions and reports: [SECURITY.md](SECURITY.md).
