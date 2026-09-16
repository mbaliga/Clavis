# Contributing to Clavis

Thank you for helping build a small, honest language-learning app. Clavis is one person's experiment (N=1) made public; the bar for a change is that it keeps the app truthful, offline and licence-clean.

## Before opening code

For material features, start with an issue that states:

- the persona it serves ([docs/04-personas.md](docs/04-personas.md)) and the evidence rule it touches ([docs/03-evidence.md](docs/03-evidence.md), the `R-*` rules in the product spec);
- what it changes on Android and on desktop, and at which window widths;
- what data it touches — learner state, imported text, downloads — and whether any byte would leave the device (it must not, unless the user starts it: [docs/DECISIONS.md](docs/DECISIONS.md) D-13);
- the licence of any content it introduces (see below);
- failure, cancellation and process-death behaviour;
- accessibility (TalkBack, keyboard, 200% text, RTL);
- the tests and evidence you will provide.

Feature count is not the goal. A smaller reliable behaviour is preferred over a broad mock.

## Development setup

- JDK 17 (any JDK ≥ 17 works for the Gradle daemon; the foojay resolver provisions 17 for compilation)
- Android SDK: platform 35, build-tools 35.0.0 (`export ANDROID_HOME=/opt/android-sdk`, or `sdk.dir=` in `local.properties`)
- Gradle 8.14.3 via the wrapper — never a system Gradle
- Python 3.11 for the pipeline

Everything about these pins, and why, is in [docs/build-platform.md](docs/build-platform.md). If Maven Central rate-limits your network (HTTP 429), the opt-in mirrors are described there in §8 (`clavis.mirror=true` in `~/.gradle/gradle.properties`; never in the repo, never in CI).

What CI runs on every push and pull request (`.github/workflows/ci.yml`):

```bash
./gradlew :shared:allTests :composeApp:desktopTest :composeApp:lintDebug \
          :composeApp:assembleDebug :composeApp:assembleRelease :composeApp:packageUberJarForCurrentOS
```

Release candidates, unsigned unless the maintainer's secrets are present ([docs/RELEASE.md](docs/RELEASE.md)):

```bash
./gradlew :composeApp:assembleRelease :composeApp:bundleRelease   # APK + AAB
./gradlew :composeApp:packageDeb                                    # Linux; .dmg / .msi only on that OS
./gradlew :composeApp:clavisVersion                                 # prints the resolved version numbers
```

Headless desktop UI tests need no display (`docs/build-platform.md` §6). `./gradlew :composeApp:run` starts the desktop app.

Pipeline:

```bash
python -m venv venv && . venv/bin/activate
pip install -r pipeline/requirements.txt pytest
python -m pytest pipeline/tests -q          # no corpus or network needed
```

Running the stages themselves needs the Pepper&Carrot clone and the kaikki extracts ([pipeline/README.md](pipeline/README.md)); nothing under `pipeline/build/` or `corpus/` is ever committed.

## Branch and pull request guidance

- Keep a change focused; use descriptive commits.
- Include screenshots or recordings for visible changes at phone and desktop widths, in light and dark.
- State what is implemented and what remains staged or mocked.
- Add tests for behaviour: domain logic lives in `:shared` and is tested with plain JVM tests on every target (`:shared:allTests`); UI flows get headless Compose tests in `composeApp/src/desktopTest`.
- Do not weaken the no-permission manifest, the offline default or the atomic state write to simplify a demo.
- Every `@Serializable` change to the learner-state document needs a migration step and a `State migration` line in `CHANGELOG.md` (D-12).
- Do not give `@Serializable` classes named companion objects (R8 full mode; `composeApp/proguard-rules.pro`).

## Content and licence rules (the pipeline)

- Only CC0 / CC BY / CC BY-SA content is bundled or offered for download. Anything NC, ND or of unknown licence is excluded at pipeline time, including per-file audio (D-7). A licence check is a test, not a review comment.
- Every displayed fact traces to a source row: comic text to an episode and language folder, an etymology to a Wiktionary or EtymDB record, a sentence to a Tatoeba id, a recording to a Commons file. Never invent an etymology; where the graph is silent the app shows nothing (R-10).
- Attribution is data. `pack_assets.py` regenerates `ATTRIBUTION.md` and the manifest's `attribution[]` from `info.json` and the enrichment fragments on every build; never hand-edit a generated pack or its attribution file. If a credit is wrong, fix the source or the extractor.
- Languages are data, not code (`pipeline/languages.json`, D-6). Adding a language is a registry entry plus, if needed, a tokeniser — the worked example is in `pipeline/README.md`.
- Regenerated packs change numbers. Run `validate.py --check` against the golden file and, when the change is intended, write a new golden file and say so in the pull request.

## Copy rules (D-18 — enforced in review, and by the copy lint once it lands)

User-facing text must not say `subconscious`, `subliminal`, `absorb`, `passive(ly)`, `in your sleep`, `without noticing`, `effortless` (as a promise), `fluent` / `fluency` (as an outcome), `master`, `% of the language`, `level`, `A1`…`C2`, `streak`, `XP`, `proven`, `scientifically`. Coverage is "scenes readable at the 95% threshold", never "% of a language learned". Every on-screen claim carries a citation `[n]`; ids 1–40 are fixed and new references are appended, never renumbered (R-8). The N=1 position is stated where the method is described, including onboarding and the store listing. Keep user-facing language factual and calm.

## Style

- Kotlin official style; the file-top comment explains why the file exists, not who owns the copyright (no licence banners).
- Compose UI exposes semantics, content descriptions and visible focus; targets are at least 44 dp.
- Prefer the theme tokens in `ui/theme` over hard-coded values; motion honours the reduced-motion setting.
- Load-bearing rules get a comment naming the defect they prevent.

## Licence of your contribution and sign-off

This project is licensed under **Apache-2.0** (see `LICENSE`; fonts and content packs are under their own licences — see `NOTICE`). By submitting a contribution to the Apache-2.0-covered parts of this repository, you agree it is licensed under the same terms (Apache-2.0 §5 covers this — no separate CLA).

Sign off every commit, certifying you wrote it or otherwise have the right to submit it — the [Developer Certificate of Origin](https://developercertificate.org/). Use `git commit -s`, or add by hand:

```
Signed-off-by: Your Name <your.email@example.com>
```

## Provenance

- Do not copy source from another language app merely because it is open source; preserve licence compatibility and attribution.
- Record the origin and licence of third-party assets, fonts, fixtures and significant code adaptations in `THIRD_PARTY_NOTICES.md`.
- Generated code is still your responsibility: review it for licences, correctness, security and maintainability.
- Test fixtures must not contain real learner exports or private texts.

## Security-sensitive changes

Download and unpacking code, the `.txt` / `.srt` / `.lrc` and JSON-import parsers, the Piper process wrapper, learner-state migration and anything that adds a permission or a network call need explicit security review. Report vulnerabilities privately as described in [SECURITY.md](SECURITY.md), not in a public issue.
