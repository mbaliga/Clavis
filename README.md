# Clavis

An open-source, offline-first application that teaches nine languages at once — Spanish, French,
Italian, German, Dutch, Japanese, Korean, Chinese, Arabic, bridged through English — by treating
them as one connected system rather than nine separate courses: annotated open-source comics, real
etymology, spaced repetition (FSRS-5), and an adaptive branching curriculum instead of a fixed
syllabus.

> Multi-lingual everyday proficiency without deliberate, dedicated learning effort by the user.

The one-sentence version: every mainstream language app models a learner acquiring *a* language.
Clavis models a learner acquiring *a region of the world's lexicon* — where knowing Spanish `casa`
is already partial knowledge of Italian `casa`, French `chez`, and Latin `casa`, and the useful unit
of study is the relationship between words, not the word.

## Start here

- **[docs/00-README.md](docs/00-README.md)** — the full documentation set: premise, hypotheses (and
  what the research actually said about them), personas, information architecture, app
  architecture, etymology, references, handoff.
- **[docs/10-build-plan.md](docs/10-build-plan.md)** — how to get from what's seeded in this repo to
  a running prototype, both a quick reproduction of the original web build and a native Android
  build in this constellation.
- **[reference-prototype/](reference-prototype/)** — the corpus-pipeline generator and the
  single-file web app template from the original build. This is the "authoritative spec": a native
  port's job is to reproduce its behaviour, not its architecture.

## Status

Concept and prior-build documentation seeded; no corpus, generated data, or built app in this repo
yet. See `docs/10-build-plan.md` §1 for exactly what that does and doesn't mean, and §2 for the one
blocking research item (Pepper&Carrot's licence terms are not yet confirmed) before pulling in the
comic corpus.

## Licensing

Content packs are restricted to CC0 / CC BY / CC BY-SA. The etymology graph is built from EtymDB
2.1 (CC BY-SA 4.0, Fourrier & Sagot 2020); the comic corpus is Pepper&Carrot (David Revoy and
translators) — see the blocking note above before treating that as settled. App code is licensed
separately from the CC BY-SA data pack.
