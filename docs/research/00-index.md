# The research corpus

Seven memos, ~279 KB, ~470 unique sources, compiled 29–31 August 2026 to find out whether the Clavis method was **defensible before building** — not to justify it afterwards.

They are reproduced here verbatim, unedited. Where they contradict the product, the contradiction stands; `03-evidence.md` collects those contradictions rather than burying them.

---

## Read this first: the memos do not share a labelling scheme

This is the most important thing to know before quoting any of them. Flattening seven schemes into one would misrepresent every memo.

| Memo | Scheme | What the labels mean |
|---|---|---|
| **01** Multi-language pedagogy | `STRONG` / `MIXED` / `PRACTITIONER` / `NOT VERIFIED` | Evidence strength, plus a provenance flag |
| **02** Mother tongue in India | `[Strong]` / `[Mixed]` / `[K]` | **`[K]` is provenance, not strength** — "not fetched this session" |
| **03** Coverage, SRS, microlearning | `[strong]` / `[mixed]` / `[anecdotal]` / `[unverified this session]` | Strength + provenance |
| **04** Competitors & market | **none** | Only `[unverified]`, "[my estimate]", "[my arithmetic]", "[fetch blocked]" |
| **05** Data & platform | **none** | `[verified 2026-08]` vs `[unverified]` — provenance only |
| **06** Culture, motivation, games | `STRONG` / `MIXED` / `ANECDOTAL` | The only memo with a clean evidence-grade scheme |
| **07** Media & music | **none** | `[fetched]` vs `[search-snippet]` — provenance only |

Consequences:

- **`PRACTITIONER` exists only in memo 01.** Memo 06's equivalent is `ANECDOTAL`.
- **Memo 07 assigns no strength grades at all.** Calling a media or music finding "strong" is an interpolation, not something a memo said.
- **Memo 02's `[K]` is not a weak grade** — it means the primary text was never opened. Memo 02's own instruction: *verify `[K]` figures before quoting them externally.* Its entire theoretical apparatus (Grosjean, Cummins, heritage-language research, all the attrition science) and its whole global-analogues table are `[K]`.
- **Two memos ran out of search budget mid-way** (01: "exhausted part-way"; 02: "exhausted after ~25 queries"). The gaps are therefore *systematic* — they cluster in whatever was searched last, not randomly.
- **Memos 04–06 and memo 07 describe partly different products.** Memos 04–06 were written for an Indic-languages-plus-smartwatch concept; memo 07 for the nine-language *Pepper&Carrot* build the project actually became. Read the market and platform material with that scope shift in mind.

---

## The memos

### 01 — Multi-language pedagogy
*Learning several languages at once: what the research supports, what it warns against, and how to design around it.* 40 KB, 74 sources.

Third-language acquisition theory, transfer models (Typological Primacy, L2 Status Factor, psychotypology), sibling-language interference, intercomprehension programmes and their measured intelligibility figures (MICReLa), cognate facilitation and false friends, elaboration and the form-vs-meaning trade-off, loanword strata across the target families, and an Indic section that mostly comes back empty.

**Where it bites hardest:** §1.4 — *"What is missing is exactly your case: two closely related targets, both at A0–A2, with productive goals."* This memo is the reason the lane split exists.

**Its own do-not-quote list is at the end of the file** and should be honoured.

---

### 02 — Mother tongue in India
*Is the "mother tongue" really the weaker tongue? An evidence review for urban India.* 42 KB, 41 sources plus a large `[K]` block.

Written to test one of the builder's own stated observations, quoted in the memo's header. Census methodology and its explicit "as declared, without any test" caveat; English-speaking base rates; the English-medium schooling pipeline by state; the IIT Bombay heritage-language survey (n = 1,375); heritage-speaker profiles; language attrition; regional-language internet and OTT growth.

**Where it bites hardest:** it rules the builder's sampling method a *certain* confound — he sampled people in education and work, the two domains where English is institutionally mandated — and then identifies Hyderabad as the epicentre of the real, narrower phenomenon.

---

### 03 — Coverage, spaced repetition, microlearning
*Evidence brief.* 34 KB, 62 sources.

Zipf's law and vocabulary coverage curves; the 95%/98% comprehension thresholds; vocabulary size and CEFR; the spacing and testing effects with effect sizes; SM-2 vs HLR vs FSRS on the ~350 M-review benchmark; microlearning field studies (WaitChatter, WaitSuite, MicroMandarin); notification limits; the noticing literature and why sleep-learning claims are forbidden; language attrition and the cost of maintenance.

**Where it bites hardest:** it addresses the "90-10 rule" by name, endorses the strategy, and rejects the formulation — then supplies the restatement the app actually uses.

---

### 04 — Competitors & market
*Multi-language-by-family app + culture/geo layer + watch companion.* 35 KB, 91 sources.

Every major language app with pricing and positioning; Indian-language apps; intercomprehension and etymology products; geography games; market sizing; smartwatch market and wrist-attention behaviour; the open-source and F-Droid landscape.

**Where it bites hardest:** the market gap is *linking*, not multiplicity. Seven apps already sell "all languages," and Glossika explicitly sells simultaneous multi-language study. What none of them do is connect the courses.

**Caveat the memo states about itself:** treat market-size figures from research-report vendors as directional only.

---

### 05 — Data & platform feasibility
*Feasibility map.* 61 KB, 95 sources — the largest memo.

Lexical, etymology and cognate databases with licences; Indian-language resources and their thinness; frequency lists; offline TTS and ASR; geography, imagery and map data; Android and Wear OS ambient surfaces; SRS storage and query performance; the consolidated licence matrix.

**Where it bites hardest:** it names the blockers precisely — offline ASR for several Indic languages is not solvable with permissive models; excluding MMS-TTS on NC grounds leaves Kannada and Konkani with no offline voice at all; Android notification delivery is structurally unreliable and Indian OEMs make it worse; F-Droid will not accept opaque prebuilt databases without a reproducible generator.

**It also recommends replacing EtymDB with wiktextract/kaikki.org** — the highest-value open change in the project (`07-etymology.md` §7).

---

### 06 — Culture, motivation, games
*Culture-integrated learning, motivation, micro-games, scripts and multi-language display.* 36 KB, 59 sources.

CLIL and content-integrated instruction meta-analyses; narrow reading; cultural schema and elaborative encoding; motivation research; gamification, rewards and streaks; habit formation; quiz format, feedback and difficulty; dual coding; multi-script literacy; geography games.

**Where it bites hardest, twice.** First: gamification's motivational and behavioural effects *become non-significant in rigorous designs* — only cognitive effects survive, so no design can claim to sustain motivation. Second: it defends streaks with the largest behavioural dataset in the whole corpus, and recommends *forgiving* streaks rather than none — a recommendation the build did not take.

---

### 07 — Media & music
*Does multi-media exposure "ignite recall"? Evidence + openly-licensed sources.* 29 KB, 73 sources.

Familiarity vs recollection; savings in relearning; pre-exposure effects; contextual diversity experiments; singing and verbatim production; background music as interference; the seductive-details literature; and Part 2, a survey of openly-licensed media and music sources with their licence terms.

**Where it helps most:** this memo carries the strongest support for anything the builder proposed — contextual diversity (Frances et al. 2020), a direct experimental hit on the "meet the word in many places" premise.

**Where it bites:** background music with lyrics measurably harms verbal memory and reading, and users misjudge this. "Add music" implemented as ambience would make the app worse.

---

## What none of the memos cover

Silence is not support. The gaps, consolidated:

1. Adaptive-learning-platform outcomes, mastery learning, intelligent tutoring systems — **not searched at all**. The graph-as-curriculum hypothesis therefore has no outcome evidence behind it.
2. Any Indic lexical-similarity or intelligibility coefficient — none retrievable.
3. Brahmic→Brahmic reading transfer — no experimental evidence.
4. Objective mother-tongue vs English vocabulary testing in Indian adults — **no study exists**.
5. Geo-game → language-learning transfer — zero evidence either way.
6. Whether timed recognition micro-games beat untimed retrieval — explicitly listed as untested.
7. Accountless design and its effect on retention, conversion or piracy — never analysed.
8. **Pepper&Carrot's licence** — undocumented anywhere in the corpus, despite being the foundation of the build.

---

## How these memos were used

`03-evidence.md` maps eleven of the builder's stated claims against this corpus and records the verdict for each — five of which came back complicated or contradicted. `02-hypothesis.md` restates the affected claims in their defensible form and marks which restatements are already implemented in the shipping build.

The reading order that makes the most sense: **01 → 03 → 07** for the pedagogy the app is built on, **02 → 04** for who it is for and what exists already, **05** before any platform decision, **06** before adding any mechanic.
