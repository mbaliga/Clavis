# Clavis — documentation

An open-source, offline-first application that teaches nine languages at once by treating them as one connected system: Spanish, French, Italian, German, Dutch, Japanese, Korean, Chinese, Arabic, bridged through English, built on annotated open-source comics, real etymology, spaced repetition, and an adaptive branching curriculum.

Single self-contained HTML file. No server, no account, no network after load.

---

## Note on what is actually in this repo right now

The numbered documents below describe the *design* of Clavis and a v0.10 web build that existed in a
prior environment — "v0.10 ships and works" refers to that build. What this repository now contains
beyond the documents: the Kotlin Multiplatform app (`../shared/` — the domain layer ported from that
build, with its tests; `../composeApp/` — the Compose Multiplatform shell for Android and desktop), the
parameterised corpus pipeline (`../pipeline/`, the successor of the generator kept in
[`../reference-prototype/`](../reference-prototype/), which stays as the frozen executable spec — D-1),
the decision record ([DECISIONS.md](DECISIONS.md)), the verified toolchain
([build-platform.md](build-platform.md)) and the release procedure ([RELEASE.md](RELEASE.md)). The
generated packs are committed as app resources (`../composeApp/src/commonMain/composeResources/files/packs/`,
with their `ATTRIBUTION.md`); the corpus and the kaikki extracts that produce them are not —
`pipeline/README.md` says how to rebuild them, and the app's own `README.md` says where release
builds come from. **[10-build-plan.md](10-build-plan.md)** remains the plan that got from the
seed to here; where it and `DECISIONS.md` disagree, `DECISIONS.md` wins.

---

## The set

| File | What it is | Read it when |
|---|---|---|
| **[01-premise.md](01-premise.md)** | What Clavis is, why it exists, what it is not, the N=1 position | Start here |
| **[02-hypothesis.md](02-hypothesis.md)** | Nine claims stated so they can be wrong, with the falsification protocol | You want to know what is being asserted |
| **[03-evidence.md](03-evidence.md)** | Those claims tested against the research — including the five that came back complicated or contradicted | You want to know what is actually true |
| **[04-personas.md](04-personas.md)** | One real user, three extrapolated ones, and who this is explicitly not for | You are making a product decision |
| **[05-information-architecture.md](05-information-architecture.md)** | Screens, navigation, content model, selection engine, state | You are changing what the app does |
| **[06-app-architecture.md](06-app-architecture.md)** | Pipeline, template internals, FSRS, G6, design system, testing | You are changing how it works |
| **[07-etymology.md](07-etymology.md)** | The word graph: source, build, measured coverage, and where it fails | You are working on the core idea |
| **[08-references.md](08-references.md)** | 40 in-app citations + 473 research sources + data licences | You are checking a claim |
| **[09-handoff.md](09-handoff.md)** | Build commands, tests, gotchas, open work — for the original web/HTML build | You are picking that codebase up |
| **[10-build-plan.md](10-build-plan.md)** | How to get from this seed to a running prototype in *this* repo, web reference and native Android | You are starting the build from here |
| **[STATUS.md](STATUS.md)** | What is done, what is pending and who can do it — against the original brief and the pre-tag checklist | You want to know where the release candidate stands |
| **[USABILITY.md](USABILITY.md)** | The screenshot review and heuristic walkthrough of 0.1.0-rc.1: every finding, its evidence and status | You are about to do the on-device pass or change a screen |
| **[research/](research/)** | The seven research memos, verbatim, plus an index | You want the primary argument |

---

## Reading orders

**"I want to understand the idea."** 01 → 02 → 03 → 07

**"I'm continuing the build."** 09 → 06 → 05

**"I'm assessing whether this is any good."** 03 → `research/00-index.md` → 02

**"I'm designing something on top of it."** 04 → 05 → 01

---

## The short version

**The premise.** Every mainstream language app models a learner acquiring *a* language. Clavis models a learner acquiring *a region of the world's lexicon*, where knowing Spanish `casa` is already partial knowledge of Italian `casa` and Latin `casa`, and the useful unit of study is the relationship between words rather than the word.

**What the research said about that.** Split. Reception across a family: supported, with measured figures. Production of two sibling languages from zero, simultaneously: the one configuration the literature actively warns against — which is why the app produces only one language per family and reads the rest. Etymology: a retention multiplier, not a first-contact tool, because meaning-work at first exposure suppresses learning the word's form. The "90-10 rule": directionally right, numerically wrong, and restated in the app. "Subconscious" learning: contradicted outright, and the copy was rewritten to respect it. Multi-media recall: the best-supported thing in the whole set.

**Where it stands.** v0.10 ships and works. The Romance and Germanic etymology graph does its job at ~30% lemma coverage; the CJK and Arabic graph does not, at 0.6–4.5%, and `07-etymology.md` says so plainly rather than rounding it up. The hypotheses are instrumented in state but not yet surfaced anywhere a person could read them, which is the largest gap between what this project claims and what it can currently demonstrate.

---

## A note on how these documents are written

The builder's standing instruction was **evidence-based debate, not begrudging compliance**. So where the research contradicts the product, these files say so at the same volume as they report support — and where a claim was restated after the evidence came back, the original is left visible with the correction attached rather than quietly rewritten.

`03-evidence.md` contradicts the project in five places. `07-etymology.md` reports its own worst numbers. `04-personas.md` names a persona the corpus cannot currently serve. That is the intended behaviour of this documentation, not a defect in it.

---

## Sources the project is built on

- **Pepper&Carrot** — David Revoy, <https://www.peppercarrot.com/> — the comic corpus and its community parallel translations. CC BY 4.0, stated in the framagit repository's README and verified on 2026-09-01 (`DECISIONS.md` D-4); the research corpus had left this undocumented.
- **EtymDB**, release 2.1 (the paper describes 2.0) — Fourrier & Sagot 2020, CC BY-SA 4.0 — the etymology graph.
- **AntV G6 v5.1.1** — MIT — graph rendering.
- **FSRS-5** — the scheduler.

Content is restricted to CC0 / CC BY / CC BY-SA so a paid tier stays legally possible. That rule has a cost, recorded in `03-evidence.md` §11.
