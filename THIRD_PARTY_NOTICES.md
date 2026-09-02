# Third-party notices

Clavis is licensed under Apache License 2.0. The application depends on third-party software, typefaces, data and platform components with their own licences or terms; the content packs and fonts are explicitly outside the Apache grant (see the NOTE ON SCOPE in [LICENSE](LICENSE) and [NOTICE](NOTICE)).

This notice is reconciled against the two generated dependency graphs — `releaseRuntimeClasspath` (Android) and `desktopRuntimeClasspath` (desktop) — which the release workflow attaches to every GitHub Release as `clavis-<version>-dependencies-android.txt` and `clavis-<version>-dependencies-desktop.txt`. The graphs remain the authoritative inventory for a specific build because Gradle may resolve or upgrade transitive modules over time. Regenerate them locally with `./gradlew :composeApp:dependencies --configuration releaseRuntimeClasspath` (and `desktopRuntimeClasspath`). Last reconciled: 2026-09-02, Kotlin 2.1.20 · Compose Multiplatform 1.8.2 · AGP 8.9.1.

## Apache License 2.0 families

Both resolved runtime graphs contain the following open-source families distributed under Apache License 2.0:

- Kotlin standard library, and kotlinx coroutines (`core`, `android`; `swing` on desktop), kotlinx serialization (`core`, `json`) and kotlinx datetime
- JetBrains Compose Multiplatform (runtime, foundation, material3, ui, components-resources, ui-tooling-preview, material-icons-core) and its `org.jetbrains.androidx.*` multiplatform builds of Lifecycle, Navigation and SavedState
- On desktop: JetBrains Skiko, the Skia binding (Apache-2.0), which bundles Skia (BSD 3-Clause) and Skia's own third-party components
- On Android: AndroidX Activity, Annotation, Arch Core, Autofill, Collection, Compose (animation, foundation, material, material3, runtime, ui), Concurrent, Core, CustomView, Emoji2, Graphics (`graphics-path` — the only native library in the APK), Interpolator, Lifecycle, Navigation, ProfileInstaller, SavedState, Startup, Tracing and VersionedParcelable
- Square okio
- multiplatform-settings (Russell Wolf)
- JSpecify annotations and JetBrains annotations
- Guava `listenablefuture` (the empty conflict-avoidance artifact)

The exact artifacts and versions are in the two graph files attached to each release. Of the artifacts resolved on 2026-09-02, none ships a `NOTICE` file whose text Apache-2.0 §4(d) would require reproducing; the AndroidX and JetBrains artifacts carry their licence texts under `META-INF/` inside the jars and AARs.

Clavis's packaging configuration removes duplicate `META-INF` licence resources from the APK (`/META-INF/{AL2.0,LGPL2.1}`) to avoid Android packaging collisions; that technical exclusion does not change or waive any licence obligation.

## Desktop runtime (jlink image)

The Linux `.deb`, macOS `.dmg` and Windows `.msi` bundle an Eclipse Temurin 17 runtime image produced by jlink/jpackage — OpenJDK under the GNU General Public License v2 with the Classpath Exception. Its own third-party notices are installed with the application under `runtime/legal/<module>/` (observed in the verified Linux build: `java.base` — aes, asm, c-libutl, cldr, gcc, icu, public_suffix, siphash, unicode, zlib; `java.desktop` — colorimaging, freetype, giflib, harfbuzz, jpeg, lcms, libpng, mesa3d, pipewire, xwd; plus `java.datatransfer`, `java.logging`, `java.prefs`, `java.xml`, `jdk.crypto.ec`). The uber-jar bundles no runtime and runs on the user's own JRE 17+.

## Desktop audio (LGPL 2.1; desktop JVM target only)

The desktop build decodes the MP3 and OGG Vorbis recordings a learner explicitly downloads
(Wikimedia Commons word audio, Tatoeba sentence audio) with pure-Java decoders; javax.sound alone
knows only WAV. They are consumed as ordinary jars on the desktop runtime classpath, unmodified,
and instantiated explicitly (`MpegAudioFileReader`, `VorbisAudioFileReader` and their conversion
providers) rather than through `ServiceLoader`; nothing from them is linked into the Android APK.

| Artifact | Version | Licence | Source |
|---|---|---|---|
| `com.googlecode.soundlibs:mp3spi` (JavaZOOM MP3SPI) | 1.9.5.4 | LGPL 2.1 | http://www.javazoom.net/mp3spi/mp3spi.html |
| `com.googlecode.soundlibs:jlayer` (JavaZOOM JLayer) | 1.0.1.4 | LGPL 2.1 | http://www.javazoom.net/javalayer/javalayer.html |
| `com.googlecode.soundlibs:vorbisspi` (JavaZOOM VorbisSPI) | 1.0.3.3 | LGPL 2.1 | http://www.javazoom.net/vorbisspi/vorbisspi.html |
| `com.googlecode.soundlibs:jorbis` (JCraft JOrbis) | 0.0.17.4 | LGPL 2.1 | http://www.jcraft.com/jorbis/ |
| `com.googlecode.soundlibs:tritonus-share` (Tritonus) | 0.3.7.4 | LGPL 2.1 | http://www.tritonus.org/ |

LGPL 2.1 §6 is satisfied by shipping them as separate, replaceable jars (the uber-jar and the
jpackage image both keep them as distinct entries under `app/`); the licence text is inside each
jar's `META-INF/`. A user may relink against a modified copy by replacing the jar.

## Fonts (SIL Open Font License 1.1)

The typefaces below are bundled as application resources (docs/DECISIONS.md D-9: Hind, Rozha One and IBM Plex Mono everywhere; the Noto Sans families on desktop, where system coverage of Arabic, Hebrew and CJK cannot be assumed). The set actually shipped is the content of `composeApp/src/commonMain/composeResources/font/` (and the desktop-only set) at the release commit; step 5 of the review procedure compares it with this table. Each family's copyright notice, as required by OFL §2, and the full licence text (Appendix A) accompany every distribution.

| Family | Copyright notice | Reserved Font Name | Licence |
|---|---|---|---|
| Hind (Regular, Medium, SemiBold, Bold) | Copyright (c) 2014, Indian Type Foundry (info@indiantypefoundry.com) | — | SIL OFL 1.1 |
| Rozha One (Regular) | Copyright (c) 2014, Indian Type Foundry (info@indiantypefoundry.com) | — | SIL OFL 1.1 |
| IBM Plex Mono (Regular, Medium) | Copyright © 2017 IBM Corp. with Reserved Font Name "Plex" | Plex | SIL OFL 1.1 |
| Noto Sans (Regular, Medium, Italic), Noto Sans Arabic, Noto Sans Devanagari, Noto Sans Hebrew | Copyright 2022 The Noto Project Authors (https://github.com/notofonts) | — | SIL OFL 1.1 |
| Noto Sans JP, KR, SC, TC (Noto Sans CJK) | Copyright 2014-2021 Adobe (http://www.adobe.com/), with Reserved Font Name 'Source' | Source | SIL OFL 1.1 |

The fonts are used unmodified; no derivative uses a Reserved Font Name. The web reference build (`reference-prototype/template.html`) loads Hind, Rozha One and IBM Plex Mono from Google Fonts instead of bundling them.

## Content packs (Creative Commons; not covered by the Apache licence)

Only CC0 / CC BY / CC BY-SA material is bundled or offered for download (D-7); the pipeline excludes NC, ND and unlicensed items, including per-file audio. Each pack's manifest carries `licence` and `attribution[]`, and `pack_assets.py` regenerates an `ATTRIBUTION.md` naming every author, translator and source on every build — it is never hand-edited, and the in-app colophon ("Materials & credits") renders the same data. The pack as a whole is CC BY-SA 4.0 because it contains share-alike data.

| Pack | Version | Licence | Source and attribution |
|---|---|---|---|
| `clavis-comics` — Pepper&Carrot scenes and page art (17 languages) | 2026.09.01 | CC BY 4.0 | *Pepper&Carrot* by **David Revoy** (https://www.peppercarrot.com), from https://framagit.org/peppercarrot/webcomics, whose README states CC BY 4.0 for all content (verified 2026-09-01, D-4). Balloon text re-lettered and tokenised by the pipeline; pages scaled to 640 px, otherwise unmodified. Translators per language are credited from each episode's `lang/<code>/info.json` in the pack's `ATTRIBUTION.md`. |
| `clavis-lexicon` — lemmas, Zipf frequencies, pseudoword foils | 2026.09.01 | CC BY-SA 4.0 | Frequencies derived from **wordfreq** 3.1.1 by Robyn Speer (https://github.com/rspeer/wordfreq; data CC BY-SA 4.0, aggregating OpenSubtitles, Wikipedia, Google Books Ngrams, Common Crawl and others) plus corpus counts. Latin carries corpus-derived values. |
| `clavis-etymology` — origins, cognate/borrowing edges | 2026.09.02 | CC BY-SA 4.0 | Wiktionary contributors via wiktextract / kaikki.org (https://kaikki.org/dictionary/, CC BY-SA 4.0); EtymDB 2.1 — Clémentine Fourrier & Benoît Sagot, LREC 2020 (https://github.com/clefourrier/EtymDB, CC BY-SA 4.0). Every displayed origin traces to a source row (R-10). |
| `clavis-sentences` — example sentences | 2026.09.02 | CC BY 2.0 FR (text); audio per clip CC BY 4.0 / CC BY-SA 4.0 / CC0 1.0 | Tatoeba contributors (https://tatoeba.org/, exports of 2026-08-29); per-item authors, URLs and per-clip licences in `sentences/ATTRIBUTION-tatoeba.json` inside the pack. |
| `clavis-pronunciations` — IPA and word-audio references | 2026.09.02 | CC BY-SA 4.0 (IPA); audio per file CC0 / CC BY / CC BY-SA / public domain | Wiktionary contributors via wiktextract / kaikki.org; Wikimedia Commons contributors (https://commons.wikimedia.org/), per-file licence and author recorded in the pack. |

`reference-prototype/vendor-g6.min.js` is AntV G6 (MIT); it is part of the frozen web reference build only and is not shipped in the app.

## Optional downloads (not distributed with Clavis)

- **Piper** text-to-speech runtime (https://github.com/rhasspy/piper, MIT) is downloaded only when the user asks for a voice and is executed as a separate process, never linked, because its runtime bundles **espeak-ng** (GPL-3.0) for phonemisation (D-8).
- **Piper voices** are offered only where the model card states CC0, CC BY, CC BY-SA or public domain — audit of 2026-09-01: German, English, Spanish, French, Italian, Dutch, Polish, Portuguese, Russian, Ukrainian and Chinese; none for Arabic, Japanese, Korean, Hindi or Turkish. Each voice's card is shown before download.
- **Word and sentence audio** from Wikimedia Commons and Tatoeba are fetched per file with the licence recorded in the pack; nothing NC or ND is offered.

## Build-time tools (not redistributed)

The pipeline's Python dependencies (pinned in `pipeline/requirements.txt`; licences from the packages' metadata and the pack manifest): wordfreq (code Apache-2.0; data CC BY-SA 4.0, credited above), simplemma (MIT), fugashi (MIT) with unidic-lite (MIT packaging of NINJAL's UniDic, BSD 3-Clause), mecab-python3 (BSD) with mecab-ko-dic (Apache-2.0, Eunjeon project), jieba (MIT), opencc-python-reimplemented (Apache-2.0), pypinyin (MIT), indic-transliteration (MIT), Pillow (MIT-CMU / HPND), pytest (MIT). Their dictionaries and models are used at build time only; nothing from them is embedded in the app except the derived frequencies and readings credited in the pack.

## Google-distributed SDKs and services

None. Clavis contains no Play Services, Firebase, ML Kit or advertising component; the Android build declares no permissions.

## Test-only dependencies

- JUnit 4.13.2 — Eclipse Public License 1.0 (via `kotlin-test`)
- kotlinx-coroutines-test, Compose `uiTest` — Apache License 2.0
- pytest — MIT (pipeline)

## Release review procedure

Before each public release:

1. Run the **Release** workflow (dry run via *Run workflow*, or the tag) on the exact release commit.
2. Download `clavis-<version>-dependencies-android.txt` and `-desktop.txt`.
3. Compare the generated graphs with this notice and with the prior release's graphs.
4. Review every new or upgraded family for licence compatibility, security advisories, provenance and maintenance status; confirm no artifact has started shipping a `NOTICE` file (unzip and look under `META-INF/`).
5. Compare the font resources actually bundled with the Fonts table, and the pack `manifest.json` / `ATTRIBUTION.md` with the Content packs table; update the versions here.
6. Update this notice and `NOTICE` when direct dependencies, fonts, packs or material transitive obligations change.
7. Store the reviewed graphs with the release record (`docs/RELEASE.md` §4).

No private package, asset, model, credential, signing material or internal dependency may be included in the public Clavis build.

## Appendix A — SIL Open Font License 1.1 (full text)

Applies to every typeface in the Fonts table, together with that family's copyright notice.

```
-----------------------------------------------------------
SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
-----------------------------------------------------------

PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide
development of collaborative font projects, to support the font creation
efforts of academic and linguistic communities, and to provide a free and
open framework in which fonts may be shared and improved in partnership
with others.

The OFL allows the licensed fonts to be used, studied, modified and
redistributed freely as long as they are not sold by themselves. The
fonts, including any derivative works, can be bundled, embedded, 
redistributed and/or sold with any software provided that any reserved
names are not used by derivative works. The fonts and derivatives,
however, cannot be released under any other type of license. The
requirement for fonts to remain under this license does not apply
to any document created using the fonts or their derivatives.

DEFINITIONS
"Font Software" refers to the set of files released by the Copyright
Holder(s) under this license and clearly marked as such. This may
include source files, build scripts and documentation.

"Reserved Font Name" refers to any names specified as such after the
copyright statement(s).

"Original Version" refers to the collection of Font Software components as
distributed by the Copyright Holder(s).

"Modified Version" refers to any derivative made by adding to, deleting,
or substituting -- in part or in whole -- any of the components of the
Original Version, by changing formats or by porting the Font Software to a
new environment.

"Author" refers to any designer, engineer, programmer, technical
writer or other person who contributed to the Font Software.

PERMISSION & CONDITIONS
Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed, modify,
redistribute, and sell modified and unmodified copies of the Font
Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components,
in Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled,
redistributed and/or sold with any software, provided that each copy
contains the above copyright notice and this license. These can be
included either as stand-alone text files, human-readable headers or
in the appropriate machine-readable metadata fields within text or
binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font
Name(s) unless explicit written permission is granted by the corresponding
Copyright Holder. This restriction only applies to the primary font name as
presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font
Software shall not be used to promote, endorse or advertise any
Modified Version, except to acknowledge the contribution(s) of the
Copyright Holder(s) and the Author(s) or with their explicit written
permission.

5) The Font Software, modified or unmodified, in part or in whole,
must be distributed entirely under this license, and must not be
distributed under any other license. The requirement for fonts to
remain under this license does not apply to any document created
using the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are
not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE
COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL
DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM
OTHER DEALINGS IN THE FONT SOFTWARE.
```
