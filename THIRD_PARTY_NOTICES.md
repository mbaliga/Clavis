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

How LGPL 2.1 §6 is met differs by distribution. The jpackage images (`.deb`, `.dmg`, `.msi`)
keep these five jars as separate, unmodified files under `app/`, so a user may relink against a
modified copy by replacing the jar. The per-OS uber-jars are a combined work: `packageUberJarForCurrentOS`
merges every dependency's class files into one flat jar (no nested jars). The LGPL classes inside
it are unmodified, keep their names and packages and are not obfuscated, so they can be replaced by
re-jarring; the reverse-engineering permission of §6 is not restricted. None of the five jars carries
the licence text in its `META-INF/` (only `MANIFEST.MF` and the Maven POM), so Clavis supplies it:
the full text of the GNU Lesser General Public License 2.1 ships in the application resources of
every build as `composeApp/src/commonMain/composeResources/files/licenses/LGPL-2.1.txt`, is shown in
the app's colophon (Evidence & credits → Licences → *Show the full notices*) next to the OFL texts,
and is reproduced in Appendix B below. `junit:junit:3.8.2` (Common Public License 1.0), which
jlayer's POM declares at compile scope by mistake, is excluded from the desktop runtime classpath in
`composeApp/build.gradle.kts` and is not shipped.

## Fonts (SIL Open Font License 1.1)

The typefaces below are bundled as application resources (docs/DECISIONS.md D-9: Hind, Rozha One and IBM Plex Mono everywhere; the Noto Sans families on desktop, where system coverage of Arabic, Hebrew and CJK cannot be assumed). The set actually shipped is the content of `composeApp/src/commonMain/composeResources/font/` (`hind_{regular,medium,semibold,bold}`, `ibmplexmono_{regular,medium}`, `rozhaone_regular`) and of the desktop-only `composeApp/src/desktopMain/composeResources/font/` (`notosans{arabic,devanagari,hebrew,jp,kr,sc}`) at the release commit; step 5 of the review procedure compares it with this table. Each family's copyright notice, as required by OFL §2, and the full licence text (Appendix A) accompany every distribution.

| Family | Copyright notice | Reserved Font Name | Licence |
|---|---|---|---|
| Hind (Regular, Medium, SemiBold, Bold) | Copyright (c) 2014, Indian Type Foundry (info@indiantypefoundry.com) | — | SIL OFL 1.1 |
| Rozha One (Regular) | Copyright (c) 2014, Indian Type Foundry (info@indiantypefoundry.com) | — | SIL OFL 1.1 |
| IBM Plex Mono (Regular, Medium) | Copyright © 2017 IBM Corp. with Reserved Font Name "Plex" | Plex | SIL OFL 1.1 |
| Noto Sans Arabic, Noto Sans Devanagari (Regular; desktop only) | Copyright 2022 The Noto Project Authors (https://github.com/notofonts/arabic, .../devanagari) | — | SIL OFL 1.1 |
| Noto Sans Hebrew (the Thin static instance, per its own name table and OS/2 weight class; desktop only) | Copyright 2024 The Noto Project Authors (https://github.com/notofonts/hebrew) | — | SIL OFL 1.1 |
| Noto Sans JP, Noto Sans KR, Noto Sans SC (Noto Sans CJK, the Thin static instance, per their own name tables and OS/2 weight class; desktop only) | Copyright 2014-2021 Adobe (http://www.adobe.com/), with Reserved Font Name 'Source' | Source | SIL OFL 1.1 |

The fonts are used unmodified; no derivative uses a Reserved Font Name. The web reference build (`reference-prototype/template.html`) loads Hind, Rozha One and IBM Plex Mono from Google Fonts instead of bundling them.

## Content packs (Creative Commons; not covered by the Apache licence)

Only CC0 / CC BY / CC BY-SA material is bundled or offered for download (D-7); the pipeline excludes NC, ND and unlicensed items, including per-file audio. Each pack's manifest carries `licence` and `attribution[]`, and `pack_assets.py` regenerates an `ATTRIBUTION.md` naming every author, translator and source on every build — it is never hand-edited, and the in-app colophon ("Materials & credits") renders the same data. The pack as a whole is CC BY-SA 4.0 because it contains share-alike data.

| Pack | Version | Licence | Source and attribution |
|---|---|---|---|
| `clavis-comics` — Pepper&Carrot scenes and page art (17 languages) | 2026.09.02 | CC BY 4.0 | *Pepper&Carrot* by **David Revoy** (https://www.peppercarrot.com), from https://framagit.org/peppercarrot/webcomics, whose README states CC BY 4.0 for all content (verified 2026-09-01, D-4). Balloon text re-lettered and tokenised by the pipeline; pages scaled to 640 px, otherwise unmodified. Translators per language are credited from each episode's `lang/<code>/info.json` in the pack's `ATTRIBUTION.md`. |
| `clavis-lexicon` — lemmas, Zipf frequencies, pseudoword foils | 2026.09.02 | CC BY-SA 4.0 | Frequencies derived from **wordfreq** 3.1.1 by Robyn Speer (https://github.com/rspeer/wordfreq; data CC BY-SA 4.0, aggregating OpenSubtitles, Wikipedia, Google Books Ngrams, Common Crawl and others) plus corpus counts. Latin carries corpus-derived values. |
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

None. Clavis contains no Play Services, Firebase, ML Kit or advertising component. The Android build declares exactly one permission, `INTERNET`, used only when the learner taps a recording to fetch that public file over HTTPS from Wikimedia Commons or Tatoeba (SECURITY.md, PRIVACY.md); nothing else uses the network.

## Test-only dependencies

- JUnit 4.13.2 — Eclipse Public License 1.0 (via `kotlin-test`). The unrelated `junit:junit:3.8.2` (CPL 1.0) that jlayer's POM declares is excluded from the desktop runtime graph and never shipped (see Desktop audio).
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

## Appendix B — GNU Lesser General Public License 2.1 (full text)

Applies to the desktop audio decoders listed under "Desktop audio" (MP3SPI, JLayer, VorbisSPI, JOrbis, Tritonus share). The same text ships inside the application as `licenses/LGPL-2.1.txt`.

```
GNU LESSER GENERAL PUBLIC LICENSE

Version 2.1, February 1999

Copyright (C) 1991, 1999 Free Software Foundation, Inc.
51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA

Everyone is permitted to copy and distribute verbatim copies of this license document, but changing it is not allowed.

[This is the first released version of the Lesser GPL.  It also counts as the successor of the GNU Library Public License, version 2, hence the version number 2.1.]

Preamble

The licenses for most software are designed to take away your freedom to share and change it. By contrast, the GNU General Public Licenses are intended to guarantee your freedom to share and change free software--to make sure the software is free for all its users.

This license, the Lesser General Public License, applies to some specially designated software packages--typically libraries--of the Free Software Foundation and other authors who decide to use it. You can use it too, but we suggest you first think carefully about whether this license or the ordinary General Public License is the better strategy to use in any particular case, based on the explanations below.

When we speak of free software, we are referring to freedom of use, not price. Our General Public Licenses are designed to make sure that you have the freedom to distribute copies of free software (and charge for this service if you wish); that you receive source code or can get it if you want it; that you can change the software and use pieces of it in new free programs; and that you are informed that you can do these things.

To protect your rights, we need to make restrictions that forbid distributors to deny you these rights or to ask you to surrender these rights. These restrictions translate to certain responsibilities for you if you distribute copies of the library or if you modify it.

For example, if you distribute copies of the library, whether gratis or for a fee, you must give the recipients all the rights that we gave you. You must make sure that they, too, receive or can get the source code. If you link other code with the library, you must provide complete object files to the recipients, so that they can relink them with the library after making changes to the library and recompiling it. And you must show them these terms so they know their rights.

We protect your rights with a two-step method: (1) we copyright the library, and (2) we offer you this license, which gives you legal permission to copy, distribute and/or modify the library.

To protect each distributor, we want to make it very clear that there is no warranty for the free library. Also, if the library is modified by someone else and passed on, the recipients should know that what they have is not the original version, so that the original author's reputation will not be affected by problems that might be introduced by others.

Finally, software patents pose a constant threat to the existence of any free program. We wish to make sure that a company cannot effectively restrict the users of a free program by obtaining a restrictive license from a patent holder. Therefore, we insist that any patent license obtained for a version of the library must be consistent with the full freedom of use specified in this license.

Most GNU software, including some libraries, is covered by the ordinary GNU General Public License. This license, the GNU Lesser General Public License, applies to certain designated libraries, and is quite different from the ordinary General Public License. We use this license for certain libraries in order to permit linking those libraries into non-free programs.

When a program is linked with a library, whether statically or using a shared library, the combination of the two is legally speaking a combined work, a derivative of the original library. The ordinary General Public License therefore permits such linking only if the entire combination fits its criteria of freedom. The Lesser General Public License permits more lax criteria for linking other code with the library.

We call this license the "Lesser" General Public License because it does Less to protect the user's freedom than the ordinary General Public License. It also provides other free software developers Less of an advantage over competing non-free programs. These disadvantages are the reason we use the ordinary General Public License for many libraries. However, the Lesser license provides advantages in certain special circumstances.

For example, on rare occasions, there may be a special need to encourage the widest possible use of a certain library, so that it becomes a de-facto standard. To achieve this, non-free programs must be allowed to use the library. A more frequent case is that a free library does the same job as widely used non-free libraries. In this case, there is little to gain by limiting the free library to free software only, so we use the Lesser General Public License.

In other cases, permission to use a particular library in non-free programs enables a greater number of people to use a large body of free software. For example, permission to use the GNU C Library in non-free programs enables many more people to use the whole GNU operating system, as well as its variant, the GNU/Linux operating system.

Although the Lesser General Public License is Less protective of the users' freedom, it does ensure that the user of a program that is linked with the Library has the freedom and the wherewithal to run that program using a modified version of the Library.

The precise terms and conditions for copying, distribution and modification follow. Pay close attention to the difference between a "work based on the library" and a "work that uses the library". The former contains code derived from the library, whereas the latter must be combined with the library in order to run.

GNU LESSER GENERAL PUBLIC LICENSE
TERMS AND CONDITIONS FOR COPYING, DISTRIBUTION AND MODIFICATION

0. This License Agreement applies to any software library or other program which contains a notice placed by the copyright holder or other authorized party saying it may be distributed under the terms of this Lesser General Public License (also called "this License"). Each licensee is addressed as "you".

A "library" means a collection of software functions and/or data prepared so as to be conveniently linked with application programs (which use some of those functions and data) to form executables.

The "Library", below, refers to any such software library or work which has been distributed under these terms. A "work based on the Library" means either the Library or any derivative work under copyright law: that is to say, a work containing the Library or a portion of it, either verbatim or with modifications and/or translated straightforwardly into another language. (Hereinafter, translation is included without limitation in the term "modification".)

"Source code" for a work means the preferred form of the work for making modifications to it. For a library, complete source code means all the source code for all modules it contains, plus any associated interface definition files, plus the scripts used to control compilation and installation of the library.

Activities other than copying, distribution and modification are not covered by this License; they are outside its scope. The act of running a program using the Library is not restricted, and output from such a program is covered only if its contents constitute a work based on the Library (independent of the use of the Library in a tool for writing it). Whether that is true depends on what the Library does and what the program that uses the Library does.

1. You may copy and distribute verbatim copies of the Library's complete source code as you receive it, in any medium, provided that you conspicuously and appropriately publish on each copy an appropriate copyright notice and disclaimer of warranty; keep intact all the notices that refer to this License and to the absence of any warranty; and distribute a copy of this License along with the Library.

You may charge a fee for the physical act of transferring a copy, and you may at your option offer warranty protection in exchange for a fee.

2. You may modify your copy or copies of the Library or any portion of it, thus forming a work based on the Library, and copy and distribute such modifications or work under the terms of Section 1 above, provided that you also meet all of these conditions:

     a) The modified work must itself be a software library.

     b) You must cause the files modified to carry prominent notices stating that you changed the files and the date of any change.

     c) You must cause the whole of the work to be licensed at no charge to all third parties under the terms of this License.

     d) If a facility in the modified Library refers to a function or a table of data to be supplied by an application program that uses the facility, other than as an argument passed when the facility is invoked, then you must make a good faith effort to ensure that, in the event an application does not supply such function or table, the facility still operates, and performs whatever part of its purpose remains meaningful.

(For example, a function in a library to compute square roots has a purpose that is entirely well-defined independent of the application. Therefore, Subsection 2d requires that any application-supplied function or table used by this function must be optional: if the application does not supply it, the square root function must still compute square roots.)

These requirements apply to the modified work as a whole. If identifiable sections of that work are not derived from the Library, and can be reasonably considered independent and separate works in themselves, then this License, and its terms, do not apply to those sections when you distribute them as separate works. But when you distribute the same sections as part of a whole which is a work based on the Library, the distribution of the whole must be on the terms of this License, whose permissions for other licensees extend to the entire whole, and thus to each and every part regardless of who wrote it.

Thus, it is not the intent of this section to claim rights or contest your rights to work written entirely by you; rather, the intent is to exercise the right to control the distribution of derivative or collective works based on the Library.

In addition, mere aggregation of another work not based on the Library with the Library (or with a work based on the Library) on a volume of a storage or distribution medium does not bring the other work under the scope of this License.

3. You may opt to apply the terms of the ordinary GNU General Public License instead of this License to a given copy of the Library. To do this, you must alter all the notices that refer to this License, so that they refer to the ordinary GNU General Public License, version 2, instead of to this License. (If a newer version than version 2 of the ordinary GNU General Public License has appeared, then you can specify that version instead if you wish.) Do not make any other change in these notices.

Once this change is made in a given copy, it is irreversible for that copy, so the ordinary GNU General Public License applies to all subsequent copies and derivative works made from that copy.

This option is useful when you wish to copy part of the code of the Library into a program that is not a library.

4. You may copy and distribute the Library (or a portion or derivative of it, under Section 2) in object code or executable form under the terms of Sections 1 and 2 above provided that you accompany it with the complete corresponding machine-readable source code, which must be distributed under the terms of Sections 1 and 2 above on a medium customarily used for software interchange.

If distribution of object code is made by offering access to copy from a designated place, then offering equivalent access to copy the source code from the same place satisfies the requirement to distribute the source code, even though third parties are not compelled to copy the source along with the object code.

5. A program that contains no derivative of any portion of the Library, but is designed to work with the Library by being compiled or linked with it, is called a "work that uses the Library". Such a work, in isolation, is not a derivative work of the Library, and therefore falls outside the scope of this License.

However, linking a "work that uses the Library" with the Library creates an executable that is a derivative of the Library (because it contains portions of the Library), rather than a "work that uses the library". The executable is therefore covered by this License. Section 6 states terms for distribution of such executables.

When a "work that uses the Library" uses material from a header file that is part of the Library, the object code for the work may be a derivative work of the Library even though the source code is not. Whether this is true is especially significant if the work can be linked without the Library, or if the work is itself a library. The threshold for this to be true is not precisely defined by law.

If such an object file uses only numerical parameters, data structure layouts and accessors, and small macros and small inline functions (ten lines or less in length), then the use of the object file is unrestricted, regardless of whether it is legally a derivative work. (Executables containing this object code plus portions of the Library will still fall under Section 6.)

Otherwise, if the work is a derivative of the Library, you may distribute the object code for the work under the terms of Section 6. Any executables containing that work also fall under Section 6, whether or not they are linked directly with the Library itself.

6. As an exception to the Sections above, you may also combine or link a "work that uses the Library" with the Library to produce a work containing portions of the Library, and distribute that work under terms of your choice, provided that the terms permit modification of the work for the customer's own use and reverse engineering for debugging such modifications.

You must give prominent notice with each copy of the work that the Library is used in it and that the Library and its use are covered by this License. You must supply a copy of this License. If the work during execution displays copyright notices, you must include the copyright notice for the Library among them, as well as a reference directing the user to the copy of this License. Also, you must do one of these things:

     a) Accompany the work with the complete corresponding machine-readable source code for the Library including whatever changes were used in the work (which must be distributed under Sections 1 and 2 above); and, if the work is an executable linked with the Library, with the complete machine-readable "work that uses the Library", as object code and/or source code, so that the user can modify the Library and then relink to produce a modified executable containing the modified Library. (It is understood that the user who changes the contents of definitions files in the Library will not necessarily be able to recompile the application to use the modified definitions.)

     b) Use a suitable shared library mechanism for linking with the Library. A suitable mechanism is one that (1) uses at run time a copy of the library already present on the user's computer system, rather than copying library functions into the executable, and (2) will operate properly with a modified version of the library, if the user installs one, as long as the modified version is interface-compatible with the version that the work was made with.

     c) Accompany the work with a written offer, valid for at least three years, to give the same user the materials specified in Subsection 6a, above, for a charge no more than the cost of performing this distribution.

     d) If distribution of the work is made by offering access to copy from a designated place, offer equivalent access to copy the above specified materials from the same place.

     e) Verify that the user has already received a copy of these materials or that you have already sent this user a copy.

For an executable, the required form of the "work that uses the Library" must include any data and utility programs needed for reproducing the executable from it. However, as a special exception, the materials to be distributed need not include anything that is normally distributed (in either source or binary form) with the major components (compiler, kernel, and so on) of the operating system on which the executable runs, unless that component itself accompanies the executable.

It may happen that this requirement contradicts the license restrictions of other proprietary libraries that do not normally accompany the operating system. Such a contradiction means you cannot use both them and the Library together in an executable that you distribute.

7. You may place library facilities that are a work based on the Library side-by-side in a single library together with other library facilities not covered by this License, and distribute such a combined library, provided that the separate distribution of the work based on the Library and of the other library facilities is otherwise permitted, and provided that you do these two things:

     a) Accompany the combined library with a copy of the same work based on the Library, uncombined with any other library facilities. This must be distributed under the terms of the Sections above.

     b) Give prominent notice with the combined library of the fact that part of it is a work based on the Library, and explaining where to find the accompanying uncombined form of the same work.

8. You may not copy, modify, sublicense, link with, or distribute the Library except as expressly provided under this License. Any attempt otherwise to copy, modify, sublicense, link with, or distribute the Library is void, and will automatically terminate your rights under this License. However, parties who have received copies, or rights, from you under this License will not have their licenses terminated so long as such parties remain in full compliance.

9. You are not required to accept this License, since you have not signed it. However, nothing else grants you permission to modify or distribute the Library or its derivative works. These actions are prohibited by law if you do not accept this License. Therefore, by modifying or distributing the Library (or any work based on the Library), you indicate your acceptance of this License to do so, and all its terms and conditions for copying, distributing or modifying the Library or works based on it.

10. Each time you redistribute the Library (or any work based on the Library), the recipient automatically receives a license from the original licensor to copy, distribute, link with or modify the Library subject to these terms and conditions. You may not impose any further restrictions on the recipients' exercise of the rights granted herein. You are not responsible for enforcing compliance by third parties with this License.

11. If, as a consequence of a court judgment or allegation of patent infringement or for any other reason (not limited to patent issues), conditions are imposed on you (whether by court order, agreement or otherwise) that contradict the conditions of this License, they do not excuse you from the conditions of this License. If you cannot distribute so as to satisfy simultaneously your obligations under this License and any other pertinent obligations, then as a consequence you may not distribute the Library at all. For example, if a patent license would not permit royalty-free redistribution of the Library by all those who receive copies directly or indirectly through you, then the only way you could satisfy both it and this License would be to refrain entirely from distribution of the Library.

If any portion of this section is held invalid or unenforceable under any particular circumstance, the balance of the section is intended to apply, and the section as a whole is intended to apply in other circumstances.

It is not the purpose of this section to induce you to infringe any patents or other property right claims or to contest validity of any such claims; this section has the sole purpose of protecting the integrity of the free software distribution system which is implemented by public license practices. Many people have made generous contributions to the wide range of software distributed through that system in reliance on consistent application of that system; it is up to the author/donor to decide if he or she is willing to distribute software through any other system and a licensee cannot impose that choice.

This section is intended to make thoroughly clear what is believed to be a consequence of the rest of this License.

12. If the distribution and/or use of the Library is restricted in certain countries either by patents or by copyrighted interfaces, the original copyright holder who places the Library under this License may add an explicit geographical distribution limitation excluding those countries, so that distribution is permitted only in or among countries not thus excluded. In such case, this License incorporates the limitation as if written in the body of this License.

13. The Free Software Foundation may publish revised and/or new versions of the Lesser General Public License from time to time. Such new versions will be similar in spirit to the present version, but may differ in detail to address new problems or concerns.

Each version is given a distinguishing version number. If the Library specifies a version number of this License which applies to it and "any later version", you have the option of following the terms and conditions either of that version or of any later version published by the Free Software Foundation. If the Library does not specify a license version number, you may choose any version ever published by the Free Software Foundation.

14. If you wish to incorporate parts of the Library into other free programs whose distribution conditions are incompatible with these, write to the author to ask for permission. For software which is copyrighted by the Free Software Foundation, write to the Free Software Foundation; we sometimes make exceptions for this. Our decision will be guided by the two goals of preserving the free status of all derivatives of our free software and of promoting the sharing and reuse of software generally.

NO WARRANTY

15. BECAUSE THE LIBRARY IS LICENSED FREE OF CHARGE, THERE IS NO WARRANTY FOR THE LIBRARY, TO THE EXTENT PERMITTED BY APPLICABLE LAW. EXCEPT WHEN OTHERWISE STATED IN WRITING THE COPYRIGHT HOLDERS AND/OR OTHER PARTIES PROVIDE THE LIBRARY "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE ENTIRE RISK AS TO THE QUALITY AND PERFORMANCE OF THE LIBRARY IS WITH YOU. SHOULD THE LIBRARY PROVE DEFECTIVE, YOU ASSUME THE COST OF ALL NECESSARY SERVICING, REPAIR OR CORRECTION.

16. IN NO EVENT UNLESS REQUIRED BY APPLICABLE LAW OR AGREED TO IN WRITING WILL ANY COPYRIGHT HOLDER, OR ANY OTHER PARTY WHO MAY MODIFY AND/OR REDISTRIBUTE THE LIBRARY AS PERMITTED ABOVE, BE LIABLE TO YOU FOR DAMAGES, INCLUDING ANY GENERAL, SPECIAL, INCIDENTAL OR CONSEQUENTIAL DAMAGES ARISING OUT OF THE USE OR INABILITY TO USE THE LIBRARY (INCLUDING BUT NOT LIMITED TO LOSS OF DATA OR DATA BEING RENDERED INACCURATE OR LOSSES SUSTAINED BY YOU OR THIRD PARTIES OR A FAILURE OF THE LIBRARY TO OPERATE WITH ANY OTHER SOFTWARE), EVEN IF SUCH HOLDER OR OTHER PARTY HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.

END OF TERMS AND CONDITIONS

How to Apply These Terms to Your New Libraries

If you develop a new library, and you want it to be of the greatest possible use to the public, we recommend making it free software that everyone can redistribute and change. You can do so by permitting redistribution under these terms (or, alternatively, under the terms of the ordinary General Public License).

To apply these terms, attach the following notices to the library. It is safest to attach them to the start of each source file to most effectively convey the exclusion of warranty; and each file should have at least the "copyright" line and a pointer to where the full notice is found.

     one line to give the library's name and an idea of what it does.
     Copyright (C) year  name of author

     This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation; either version 2.1 of the License, or (at your option) any later version.

     This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.

     You should have received a copy of the GNU Lesser General Public License along with this library; if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA Also add information on how to contact you by electronic and paper mail.

You should also get your employer (if you work as a programmer) or your school, if any, to sign a "copyright disclaimer" for the library, if necessary. Here is a sample; alter the names:

Yoyodyne, Inc., hereby disclaims all copyright interest in
the library `Frob' (a library for tweaking knobs) written
by James Random Hacker.

signature of Ty Coon, 1 April 1990
Ty Coon, President of Vice
That's all there is to it!
```
