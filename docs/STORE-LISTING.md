# Store listing copy

Listing text for Google Play, F-Droid (fastlane metadata) and the GitHub Release page. Written to the honesty rules that are product requirements (docs/DECISIONS.md D-18; product spec R-7 and OD-5): the N=1 position appears in the first paragraph of every surface, coverage is never "% of a language learned", and none of the forbidden vocabulary appears anywhere in this file (CI's copy lint scans it as it scans the app strings). Edit here, then paste — never the other way round. "Clavis" is a working name; a trademark check precedes the first listing.

## App name

`Clavis` (≤ 30 characters)

## Short description (≤ 80 characters)

`Nine languages as one connected system. Offline, open source, evidence-cited.`

## Full description (≤ 4,000 characters)

Clavis is an experiment in learning nine languages at once — Spanish, French, Italian, German, Dutch, Japanese, Korean, Chinese and Arabic, bridged through English — as one connected lexicon, through a libre comic, real etymology and spaced recall. Every design choice cites the research behind it, including the research that argues against it. It is being tested on one person; it is not a validated course, it will not get you far in six weeks, it does not score pronunciation, and it keeps all your data on your device.

WHAT IT DOES
• Reads with you: short scenes from the open-source comic Pepper&Carrot (David Revoy, CC BY 4.0), lettered in the language you are working on, with the English line one tap away.
• Asks you to recall, not recognise: cloze gaps in real balloons, quick reads, echoes of earlier words, and a "which language is this?" round. The answer is always shown.
• Schedules with FSRS-5: the same open scheduler used by serious flashcard tools. A missed day is not an event; a lapse comes straight back in ten minutes.
• Shows where words come from: origins and cognates from Wiktionary and EtymDB, unlocked after you have met a word twice — because meaning-work at first exposure gets in the way of learning the form.
• Reads across a family: one language per family is set to speaking, its siblings to reading, with the cited reason and the option to override.
• Offers paths, not a syllabus: a small fan of next steps with honest reasons and no promise that the order is optimal.
• Tells you how much comic you can read: the only score is the share of scenes you could read unaided at the 95% threshold. Nothing else is scored, ranked or graded.
• Speaks when it can: recorded pronunciations from Wikimedia Commons where they exist, otherwise your device's own voice or an optional downloaded one — always labelled which is which, never judged.

WHAT IT IS NOT
• Not a validated pedagogy. The evidence for its premise is split, and the app says so in its own colophon with forty references.
• Not for a trip in six weeks; a single-language course will be faster on that language.
• Not for children or classrooms: no safeguarding model, no teacher view, no age review of the corpus.
• Not connected: no account, no sync, no analytics, no crash upload. Export and import your data as a file whenever you want.

CONTENT AND LICENCES
Comics: Pepper&Carrot by David Revoy and its translators (CC BY 4.0). Word frequencies: wordfreq (CC BY-SA 4.0). Etymology: Wiktionary via wiktextract and EtymDB 2.1 (CC BY-SA 4.0). Example sentences: Tatoeba (CC BY 2.0 FR). Pronunciations: Wikimedia Commons, per-file licence. Only CC0 / CC BY / CC BY-SA material is included. Every author and translator is named in the app's colophon. The app itself is open source under the Apache License 2.0; the source is at github.com/mbaliga/Clavis.

Languages are data: more languages present in the comic (Portuguese, Latin, Russian, Polish, Ukrainian, Turkish, Hindi) can be turned on as reading languages; coverage varies by language and the app shows the real numbers.

## Feature bullets (for graphics or short formats)

- Nine languages, one lexicon — reads across families
- Real comics, real etymology, spaced recall
- Offline. No account. No tracking. Open source.
- Every claim cited; the counter-evidence too
- One person's experiment, shared — not a validated course

## Category and audience

- Category: Education. Tags: language learning, vocabulary, comics, spaced repetition.
- Content rating questionnaire (IARC): no violence beyond cartoon comedy in the source comic, no user interaction, no data sharing, no purchases.
- Target audience: 18 and over. The app is not designed for children (docs/04-personas.md, "Who this is not for"); do not opt into the Families policy.

## Data safety (Google Play)

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | No |
| Data collected | None — learning state, review history and settings never leave the device |
| Data shared | None |
| Is all of the user data collected by your app encrypted in transit? | Not applicable — nothing is transmitted |
| Do you provide a way for users to request that their data is deleted? | Not applicable — uninstalling, or Settings → Reset everything, removes everything; there is no server copy |
| Permissions | None declared |
| Privacy policy URL | the `PRIVACY.md` of the released tag on GitHub |

When optional downloads (packs, voices, recordings) ship, they are user-initiated fetches of public files over HTTPS; no user data is sent, so the answers above do not change.

## F-Droid (fastlane metadata)

- `metadata/en-US/title.txt`: `Clavis`
- `metadata/en-US/short_description.txt`: the short description above
- `metadata/en-US/full_description.txt`: the full description above
- Categories: `Science & Education`
- Licence: `Apache-2.0`; anti-features: none (no network, no non-free assets — the packs are CC BY / CC BY-SA and reproducible from `pipeline/`)
- Build: `gradle :composeApp:assembleRelease` on the pinned toolchain (docs/build-platform.md); the content packs are generated by the published pipeline, which is what makes the data reproducible rather than an opaque database.

## GitHub Release page

The release body is generated by the workflow: the `CHANGELOG.md` section for the version, checksum instructions, per-platform install notes including the unsigned-installer steps, and the sentence "Clavis is one person's experiment (N=1); it is not a validated pedagogy."

## Vocabulary check

Allowed: offline, no account, open source, cited, spaced recall, read across a family, one speaking language per family, held, ready, readable, take root, stops needing effort. Never: the R-7 list (subconscious, subliminal, absorb, passive/passively, in your sleep, without noticing, effortless as a promise, fluent/fluency as an outcome, master, % of the language, level, A1–C2, streak-style counters, points, proven, scientifically), nor "learn 9 languages fast", "authentic content", "native speed", any coverage figure phrased as language learned, or any MICReLa intelligibility number without its direction.
