# Premise

**Clavis** — working name. An open-source, offline-first application that teaches nine languages at once by treating them as one connected system rather than nine separate courses.

---

## 1. The one-sentence version

Every mainstream language app models a learner as someone acquiring *a* language. Clavis models a learner as someone acquiring *a region of the world's lexicon* — where knowing Spanish `casa` is already partial knowledge of Italian `casa`, French `chez`, and Latin `casa`, and where the right unit of study is the relationship between words, not the word.

## 2. What provoked it

Three observations, in the builder's own framing:

1. **Language courses discard the learner's existing languages.** A Hindi–Konkani–English trilingual starting Spanish is treated as a blank slate, when in fact they arrive with a metalinguistic apparatus, a script-decoding habit, and — through English — several thousand Latin-derived roots already in memory.
2. **The unit of instruction is wrong.** Courses teach words as isolated pairs (form ↔ gloss). But words have histories, and those histories are the thing that makes a second, third, and fourth language cheap. Learning `libro / livre / libro / boek / 本` as five unrelated facts is five times the work of learning one Latin root and its Germanic and Sinographic counterparts.
3. **Study is scheduled; language is not.** Anything that requires the learner to sit down and open an app competes with every other thing that wants forty minutes. The interaction that survives is the one that fits in the gap before a lift arrives.

## 3. What Clavis actually is

A single self-contained HTML file (~11 MB) that runs with no server, no account, and no network after first load. Inside it:

- **A corpus** of parallel-translated comic pages (*Pepper&Carrot*, CC BY), 649 usable scenes across 156 pages. A scene is kept when at least four languages carry the line, so coverage is uneven: English 649, Italian and Japanese 648, German 642, Dutch 641, Spanish and French 634, Chinese 625 — but **Korean 329 (51%) and Arabic 177 (27%)**. Each scene carries per-language balloon geometry, so any page can be lettered in any language that has the line.
- **A lexicon** of ~14,000 lemmas across the nine targets, each with frequency, Zipf value, surface form and (for CJK/Arabic) a reading.
- **An etymology graph** built from EtymDB 2.1 (CC BY-SA 4.0): real inheritance, borrowing and cognacy links between corpus lemmas, plus an attested origin for ~2,950 lemmas in the nine targets (3,852 including English). Coverage is very uneven — Romance and Germanic ~30%, CJK and Arabic under 5%. `07-etymology.md` §4 reports the numbers rather than rounding them up.
- **A scheduler** — FSRS-5, running per-word, per-language, entirely on device.
- **A curriculum engine** that does not exist as a fixed syllabus. What to do next is computed from what you know, presented as a small fan of branches, and chosen by you.

## 4. The nine languages, and why these nine

| Family | Languages | Role |
|---|---|---|
| Romance | Spanish, French, Italian | Dense cognate mesh; the clearest demonstration of the premise |
| Germanic | German, Dutch | A second mesh, plus a bridge to the learner's English |
| CJK | Japanese, Korean, Chinese | Shared Sinographic stratum with three different surface systems; tests whether the premise survives a script change |
| Semitic | Arabic | A deliberate outlier: RTL, non-concatenative morphology, and almost no shared lexicon with the rest — the control condition |

Four are **productive** lanes (Spanish, Dutch, Chinese, Arabic — one per family, so no two siblings are produced at once); five are **receptive** (French, Italian, German, Japanese, Korean). This split is not cosmetic. It is the direct consequence of the interference evidence in `03-evidence.md` §3, which says producing two sibling languages from zero simultaneously is the one configuration the research actively warns against.

## 5. What it is *not*

- **Not a course.** There is no Unit 1. There is a corpus, a scheduler and a set of branches.
- **Not a streak machine.** No XP, no leagues, no leaderboard, no loss-aversion mechanics. The evidence on extrinsic reward (Deci, Koestner & Ryan 1999) is the reason, and it is cited in-app.
- **Not a claim about sleep or subliminal learning.** The app says "tiny, frequent recall" and never "absorb passively." Schmidt 1990 forbids the stronger claim and the copy was written to respect it.
- **Not for everyone.** It is for people who find the *shape* of a language interesting — and it says so. Mass-market comprehensibility was traded away for density on purpose.

## 6. The N=1 position

The builder is the first and, at time of writing, only user. This is deliberate and it is the honest scope of every claim in this documentation set:

> This is a method being tested on one person who wanted it to exist. It is not a validated pedagogy. The research corpus in `research/` was assembled to find out whether the method was *defensible before building*, not to prove it works after the fact — and it returned a split verdict, recorded without softening in `03-evidence.md`.

The design consequence of N=1 is that the app is instrumented for its own falsification: baseline tests per language, an interaction log, per-lane learning-progress tracking, and a corpus-coverage readout. What it cannot do is generalise. Any claim beyond "this worked for one person" would need a study that does not exist.

## 7. Licensing posture

Content packs are restricted to **CC0 / CC BY / CC BY-SA** so that a paid tier remains legally possible later. NC-licensed sources (CogNet, PanLex, MMS-TTS, NLLB) were excluded for this reason, at a known cost — see `03-evidence.md` §11 and `research/05-data-and-platform.md`. The app code is separately licensed; the CC BY-SA data pack and the own-licence application code co-exist without contaminating each other, which was verified before the corpus was chosen.

## 8. Where to go next in this set

- Why it might work, stated as falsifiable predictions → `02-hypothesis.md`
- What the research actually says about those predictions, including where it says *no* → `03-evidence.md`
- Who it is for → `04-personas.md`
- How the app is organised → `05-information-architecture.md`
- How it is built → `06-app-architecture.md`
- The etymology model → `07-etymology.md`
- Sources → `08-references.md`
