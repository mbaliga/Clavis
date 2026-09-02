# Security policy

Clavis keeps learner data on the device, reads text files the user imports, and — only when the user asks — downloads content packs and voices. Security reports are welcome.

## Supported versions

Until the first stable release, only the latest code on `main` and the latest tagged pre-release are supported.

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability, privacy leak or data-loss condition.

Use GitHub's private vulnerability reporting feature for this repository when enabled (Security → Report a vulnerability). If it is not available, contact the repository owner through the private contact channel listed on their GitHub profile and include “Clavis security” in the subject.

Please provide:

- affected version or commit, and the platform (Android version and device, or OS and package type: `.deb`, `.jar`, `.dmg`, `.msi`);
- which languages, packs and voices were enabled or installed;
- minimal reproduction steps or a proof of concept using non-sensitive fixtures;
- expected and observed behaviour;
- potential confidentiality, integrity, availability or data-loss impact — including any network request the app made that you did not start;
- whether the issue is already public or under active exploitation;
- suggested mitigation, if known.

Do not attach real learner-state exports that contain personal notes or imported private texts, nor API keys, passwords or signing material.

## Priority areas

Reports are especially valuable for:

- any network request made without an explicit user action (docs/DECISIONS.md D-13 is a security requirement, not a preference);
- integrity of downloaded content packs and Piper voices: HTTPS only, checksum verification, no cleartext, no silent re-fetch;
- pack unpacking: path traversal (zip-slip), missing size or compression-ratio limits;
- parsers for imported `.txt` / `.srt` / `.lrc` files and for the learner-state JSON import — malformed input must fail closed without touching the existing state document;
- invoking Piper as an external process: argument injection, working-directory hygiene, anything shell-interpolated;
- learner-state atomicity and migration — a failed write or a failed migration must leave the previous document intact;
- logs, crash records, screenshots or shared feedback text that contain imported text or learner data;
- desktop builds writing outside the application data directory (see [PRIVACY.md](PRIVACY.md) for the intended locations).

## Coordinated disclosure

Maintainers will acknowledge a complete report, assess impact, work on a fix, and coordinate disclosure based on severity and exploitability. No fixed response SLA is promised during the pre-release stage, but credible reports will be handled in good faith.

## Current security posture

The foundation:

- declares exactly one Android permission, `INTERNET`, used by exactly one code path: fetching a public recording (Wikimedia Commons or Tatoeba) or an optional voice/pack when the user taps *Download* or *Play* on it (D-13). Nothing is fetched at start-up, in the background, or without that tap; `aapt2 dump badging` on a release APK shows `INTERNET` plus the `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` that the Android Gradle plugin injects into every app, and nothing else;
- has no analytics, no advertising SDK, no crash upload and no third-party service SDK (the full dependency inventory is in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md));
- stores learner state as one JSON document per profile, written to a temporary file and atomically renamed over the previous one (D-12);
- bundles its content packs at build time; downloading optional packs and voices is not implemented yet and will be user-initiated when it is (D-7, D-8);
- shrinks release builds with R8 in full mode and signs them with the maintainer's RSA upload key when the signing secrets exist; unsigned artifacts carry `-unsigned` in their names and are validation outputs, not releases;
- ships desktop installers without an Apple Developer ID or Authenticode signature for now. Verify `SHA256SUMS.txt` before installing (README → Downloads).

Staged, not production-ready: pack and voice download, pack unpacking, `.txt` / `.srt` / `.lrc` import and the Piper process wrapper. Their limits, checksum checks and hostile-input fixtures are a gate before they ship; reports against the staged code are still welcome.
