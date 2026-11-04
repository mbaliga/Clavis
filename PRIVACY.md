# Privacy

Clavis collects no data. There is no account, no server, no sync, no analytics, no crash reporting, no advertising and no third-party SDK that phones home. This page says what the app stores on your device, where, and the three situations in which bytes can leave it — all of them started by you.

## What the app stores

One JSON document per profile, `clavis-state.json`, containing your learning state: enabled languages and their productive/receptive setting, cards and their spaced-repetition schedule, the review log, baseline-test results, Paths history, settings, and the texts you imported. Recordings you tap to play (Wikimedia Commons word audio, Tatoeba sentence audio) are cached as plain files under the app's cache directory, named by a hash of their address; optional content packs and voices, when they ship, will be stored next to the state document. If the state document cannot be read at start-up, it is kept (so nothing is silently lost) in a `corrupt/` subdirectory next to the fresh one, as `corrupt/clavis-state.json.corrupt-<timestamp>`, and the app says so. Nothing is encrypted at rest beyond what the operating system provides for per-app storage; nothing in the document identifies you unless you type it in.

On Android, while a Clavis home-screen widget is placed, the app also writes a small summary for it each time you leave the app — `widgets/glance.json` under the app's no-backup directory (`Context.noBackupFilesDir`): your active languages with their due times, words held and readable share, your last session's numbers, the next due word's lemma and comic line, and a few words with their recorded origins. The widgets show language names, counts, one comic line with the word gapped and an etymology; never your imported texts. With no widget placed the file is not written, and removing the last widget deletes it. It stays on the device, is excluded from every backup and device transfer, and is replaced each time; it is never read back into your learner state (docs/widgets.md).

## Where it lives

| Platform | Learner state | Cache (tapped recordings) |
|---|---|---|
| Android | the app's private storage (`Context.filesDir`, i.e. `/data/data/io.github.mbaliga.clavis/files/`), readable only by Clavis | the app's private cache (`Context.cacheDir`) |
| Linux | `$XDG_DATA_HOME/Clavis/` (default `~/.local/share/Clavis/`) | `$XDG_CACHE_HOME/Clavis/` (default `~/.cache/Clavis/`) |
| macOS | `~/Library/Application Support/Clavis/` | `~/Library/Caches/Clavis/` |
| Windows | `%APPDATA%\Clavis\` | `%LOCALAPPDATA%\Clavis\cache\` |

These are the directories the desktop shell resolves (`DesktopAppDirs`; the directory name is `Clavis` with a capital C on every platform, which matters on case-sensitive file systems).

## What can leave the device, and only when you act

1. **Export.** You → Export writes your state document to a file you choose. Where that file goes afterwards is up to you; it contains everything in the section above.
2. **Feedback.** The feedback flow, when present, opens your system share sheet with text you see and edit first; Clavis does not send it anywhere itself (docs/DECISIONS.md D-11).
3. **Downloads you start.** When you tap *Play* or *Download* on a recording, the app fetches that one public file over HTTPS — from Wikimedia Commons or Tatoeba — and caches it under the cache directory above; optional content packs and Piper voices (from the Piper voice repository) will use the same path when they ship. Such a request carries what any HTTPS download carries (your IP address and a user agent naming Clavis and its version) and nothing else; Clavis keeps only the downloaded file, refuses anything that is not HTTPS, and stops at 25 MB. The Android build declares the `INTERNET` permission solely for this user-initiated path; nothing else in the app uses the network — nothing at start-up, nothing in the background.

## Android backup

Android's `allowBackup` stays on, but the learner-state document (`clavis-state.json`, its atomic-write temp file, and the `corrupt/` subdirectory holding a timestamped copy if a document is ever unreadable) is excluded from Android's cloud backup on Android 12 and later (`android:dataExtractionRules`). This exclusion applies to cloud backup only: device-to-device transfer (Android's "Copy your data" migration when you set up a new phone) still carries your learner state across, consistent with keeping your data on the device you chose (docs/DECISIONS.md D-13). On Android 8.0–11 (API 26–30), the OS has no split between cloud backup and device-to-device transfer, so the same exclusion there also withholds the state document from D2D transfer — a platform limitation on those older versions, not a Clavis choice. Recording clips you tap to play are cached under the app's cache directory, which Android excludes from Auto Backup by default on every version.

## Deleting your data

Uninstalling the Android app removes its private storage. On desktop, delete the directory listed above, or use Settings → Reset everything inside the app (it asks `Erase all local progress?` first). Export beforehand if you want to keep a copy.

## Children

Clavis is not designed for children or classrooms: there is no age gate, no safeguarding model and no age-appropriate review of the comic corpus (docs/04-personas.md, “Who this is not for”).

## Store declarations

Google Play data-safety form: no data collected, no data shared, no security-practice claims that depend on transmission; the same facts are repeated in the store listing ([docs/STORE-LISTING.md](docs/STORE-LISTING.md)).

## Changes

Changes to what the app stores or transmits are recorded in [CHANGELOG.md](CHANGELOG.md) under the release that made them. Questions and reports: [SECURITY.md](SECURITY.md).
