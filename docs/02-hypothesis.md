# Hypothesis

What Clavis asserts, stated so it can be wrong.

Each hypothesis below has: the claim, the mechanism it rests on, what would count as **confirmation**, what would count as **disconfirmation**, and its current **status** against the research corpus (full argument in `03-evidence.md`).

A note on epistemic honesty before the list: several of these were formulated *before* the research was commissioned, and the research came back against some of them. They are stated here in their original form with the correction attached, rather than quietly rewritten to match the findings. The rewritten versions are marked **restated**.

---

## H1 — The family hypothesis

> Learning languages grouped by family is cheaper per word than learning them independently, because cognates and shared roots make each new item partly pre-learned.

**Mechanism.** Cognate facilitation in lexical acquisition; shared orthographic and phonological form reduces encoding cost.

**Confirmation.** Words with a cognate link to an already-known lemma in another lane should show (a) higher first-attempt accuracy at introduction, and (b) longer FSRS stability at equal review count, than frequency-matched non-cognate words.

**Disconfirmation.** Cognate and non-cognate items show equal acquisition cost — or cognates show *higher* lapse rates, which would indicate the link is producing interference rather than transfer.

**Status: supported for reception, with a named failure mode.** Cognate facilitation is a strong finding (Lotto & de Groot 1998: 86.5% vs 72% recall). But false cognates are learned *faster* than non-cognates while carrying the wrong meaning (Marecka et al. 2021) — so the mechanism amplifies both the signal and the error. The app must track cognate-linked lapses separately or it will mistake interference for transfer.

---

## H2 — The reception/production asymmetry

> Reception can be widened across a whole family safely; production cannot. Producing two sibling languages from zero simultaneously will generate blends.

**Mechanism.** In comprehension, similarity is *perceived* and helps. In production, similarity is *assumed*, and learners construct one-to-one equivalences that later have to be unlearned (Ringbom 2007). Close languages require restructuring an entrenched near-match, which is harder than developing a new structure from nothing (Gil 2026).

**Confirmation.** Receptive accuracy in a non-studied sibling (e.g. Italian, never produced) rises measurably as the studied sibling (Spanish) grows — while cross-lane blend errors stay near zero because no two siblings are in productive mode.

**Disconfirmation.** Blends appear anyway between a productive lane and a merely receptive sibling; or receptive accuracy in the untaught sibling does not move.

**Status: supported, and it is the single strongest constraint the research imposed on the design.** MICReLa gives measured receptive figures (Portuguese→Spanish 77.4%, Italian→Spanish 65.7%). Every mature intercomprehension programme restricts itself to reception by design and none claims productive outcomes. This hypothesis is why the lane split is es/nl/zh/ar productive and fr/it/de/ja/ko receptive.

---

## H3 — Simultaneity is not harmful

> Studying nine languages concurrently does not slow any one of them relative to studying it alone.

**Mechanism.** Cross-language interleaving functions as spacing; prior languages contribute metalinguistic awareness.

**Confirmation.** The Spanish lane's acquisition curve is not slower than a hypothetical Spanish-only baseline. (N=1 cannot supply that baseline — see the protocol limits below.)

**Disconfirmation.** Per-lane learning-progress collapses as lanes are added; or lapse rates rise systematically with the number of active lanes.

**Status: thinly supported, and only for *distant* pairs.** Three studies exist, all on typologically distant pairs (Rahmatian & Farshadjou 2013, n=36; Huang et al. 2022; Libersky et al. 2025 — where a rest break erased the effect, meaning it was spacing, not multilingualism). Nothing establishes that simultaneous study is *better* than sequential, and nothing establishes it is worse. **This is an open question, not a finding, and should be published as one.**

---

## H4 — Etymology as a retention multiplier

> Knowing where a word comes from makes it stick better than learning it as an arbitrary pair.

**Mechanism.** Elaborative encoding; a root gives a word a place in a structure rather than a slot in a list.

**Confirmation.** Items whose origin was surfaced show longer stability at equal review count than items never given an origin.

**Disconfirmation.** No stability difference — or, worse, origin-surfaced items show *worse* form recall, which would mean the etymology is stealing attention from the word itself.

**Status: supported as a retention tool, contradicted as a first-contact tool.** This is the correction that most changed the build. Barcroft 2002: increased semantic processing at first exposure **inhibits encoding of the word's form**. Meaning-work at first meeting steals resources from learning the shape of the word.

**Restated (H4′):** *Etymology is a second-meeting feature. Present the form with minimal semantic load at first exposure; unlock the origin at the second or third retrieval.* This is implemented — origins are gated behind the second encounter, and reference `[25]` in the in-app colophon says so explicitly.

---

## H5 — The 90–10 rule

> Ten per cent of a language serves ninety per cent of its use cases.

**Status: directionally right, numerically wrong in both terms, and wrong about "use cases."** The research addresses this claim by name and rejects the formulation:

- **The numerator is an artefact of the counting unit.** A 3,000-family core is ~15–25% of a native family vocabulary (Brysbaert et al. 2016: ~11,100 of 18,269 families known at age 20), though far less than 10% of the word-*form* space. The unit swings the answer by 2–4×. Publishing "10%" without naming the unit is not defensible.
- **The denominator conflates coverage with comprehension.** Hu & Nation 2000: at 95% lexical coverage the *majority of learners still did not adequately comprehend*. Schmitt, Jiang & Grabe 2011 (n=661): comprehension rises linearly from 90% to 100% coverage with **no threshold jump** — there is no cliff where a small core suddenly unlocks the language.
- **Coverage studies measure reception only.** Vocabulary size explains ~72% of variance in reading but only 40–61% in speaking (Milton 2010).
- **The nastiest complication:** Cobb 2007 — words beyond the first 2,000 families recur too rarely in ordinary reading to be acquired incidentally. The 90–10 framing implies the remaining 90% takes care of itself through exposure. The evidence says precisely the opposite.

**Restated (H5′):** *A core of ~2,000–3,000 word families plus high-frequency chunks lets you follow most everyday conversation and is the highest-leverage investment a learner can make. Beyond that the return per word drops by Zipf's law, and reading needs ~8,000–9,000 families.* The app's coverage readout reports **scene coverage at the 95% threshold**, not a "% of language learned" figure, for exactly this reason.

---

## H6 — Micro-dosing beats the study session

> Spaced repetition spread through the day in tiny bites embeds language into the subconscious better than a scheduled study session.

**Status: one third supported, one third neutral, one third contradicted.**

- **Spacing and retrieval: strongly supported** — but the evidence-backed unit is *days*, not minutes (Cepeda et al. 2008: optimal gap 1 day for a 7-day retention interval, 11 days for 35).
- **Many micro-sessions vs one session: no direct study exists.** The honest read is roughly neutral for retention per review and positive for adherence. Re-reviewing the same item several times in one day is mostly wasted — which is why the app enforces *no item repeats within a day unless it failed*.
- **"Subconscious": contradicted outright.** Schmidt 1990: "subliminal language learning is impossible." Sleep cueing only reactivates already-learned pairs and does nothing during wakefulness. Passive ambient exposure without attention has no support and is contradicted by the noticing literature.

**Restated (H6′):** *Tiny, frequent recall — a few seconds at a time — that doesn't feel like studying. Do it long enough and the words stop needing effort.* The mechanism is **automatisation through spaced retrieval**, not absorption. The subjective experience Madhav described ("it just came to me") is real and has a name; the mechanism he attributed it to does not survive.

---

## H7 — Multi-media recall ("the tingle")

> A phrase met in the app and later glimpsed elsewhere — in credits, in a song, on a sign — produces a flicker of recognition that strengthens the memory.

**Status: the best-supported claim in the set, via a mechanism slightly different from the one implied.**

The "tingle" is a named memory signal: familiarity without recollection (Yonelinas 2002). Incidental exposure leaves far more trace than it feels like (Brady et al. 2008: 2,500 objects seen once, 87–92% later recognition). "Forgotten is not gone" — Bowers, Mattys & Gage 2009 found adults relearned long-lost childhood Hindi phoneme contrasts better than controls with *no explicit memory of ever having known them*.

But the strongest support is **contextual diversity**, and it is a direct experimental hit: Frances et al. 2020 — at identical exposure counts (8), spreading them over 8 texts instead of 1 improved recall (ηp² = .14), recognition (.11) and meaning-matching (.04), **with identical benefits in a foreign language**. Meeting a word in a comic, a song line and a caption beats four flashcard repetitions of it.

**The limit, stated in the research and honoured in the design:** incidental encounters do *not* reliably produce recallable form–meaning knowledge on their own. The tingle is a feeder into explicit study, not a replacement for it.

---

## H8 — The graph is the curriculum

> An adaptive branching structure, computed from what the learner knows, can replace a fixed class/level syllabus.

**Mechanism.** Knowledge-space theory (Doignon & Falmagne): what you know is a state in a prerequisite structure, and the "fringe" is what you are ready to learn next. Learning-progress bandits (Clement et al. 2015, ZPDES) pick the next activity per learner with no population data.

**Confirmation.** Branch choices track measurable readiness: taking a branch labelled "*n* more bubbles become readable" actually makes *n* more scenes cross the 95%-coverage line.

**Disconfirmation.** Branch predictions do not come true; or the bandit's lane weights fail to track actual per-lane learning progress; or learners ignore the fan and the ordering may as well have been fixed.

**Status: the weakest-evidenced hypothesis in the set, and it should be labelled a design hypothesis rather than a finding.** Adaptive *item scheduling* is decisively supported (FSRS beats SM-2 in 99.6% of collections across ~350M reviews) — but that benchmark measures **recall prediction, not learning outcomes**. Nothing in the corpus demonstrates that an adaptive system produces better *learners* than a fixed curriculum. Extending "when to review item X" to "what a curriculum should be" is an unevidenced leap, and the research contains no bridge for it.

Two specific warnings the graph must encode, and does:
- Levels are not arbitrary — CEFR bands index vocabulary size (Milton 2010), so a graph can compute a level but cannot make the construct go away.
- The graph needs **negative edges**, not just prerequisites. Semantically related items produce ~3× more within-set confusion (Nakata & Suzuki 2019, 8.22% vs 2.69%) and spacing does not remove it. Days-of-the-week batches and adjacent false friends are scheduling *hazards*, not conveniences.

---

## H9 — Attrition is worth designing against

> Languages decay from lost practice, and maintenance should be a first-class feature.

**Status: well supported as consensus, with the causal attribution complicated.**

Attrition is real and quantified — Bahrick 1984, 773 people up to 50 years post-study: steep loss over the first 3–6 years, then a ~25–30-year plateau ("permastore"). The *order* of loss is consistent and directly designable: productive before receptive, lexicon before grammar, formal and low-frequency vocabulary before everyday speech, phonology last.

But: retention was determined by **the level originally reached, not by rehearsal** — most of Bahrick's subjects had almost none. And in adults, amount of use is a surprisingly weak predictor; emotional attitude to the language matters more. So the design target is depth of initial learning and meaningful use, not volume of drilling.

**The most actionable number in the corpus:** Bahrick et al. 1993 — 13 sessions spaced 56 days apart preserved vocabulary as well as 26 sessions spaced 14 days apart. One successful retrieval every ~2 months holds a known word for years. For a 2,000-word core that is ~25–35 items/day, ~2–3 minutes. **Maintenance is cheap, and the UI should say so** — which is also why lapses are framed as recoverable rather than as failure.

---

## Falsification protocol (N=1)

What the single-subject setup can and cannot establish.

**Can test:**
- H1 (cognate vs frequency-matched non-cognate acquisition cost) — within-subject, adequately powered over months.
- H2 (blend errors between productive lanes; receptive gain in untaught siblings) — measurable via the language-ID game and receptive checks.
- H4′ (origin-surfaced vs never-surfaced stability at equal reps) — clean within-subject comparison.
- H8 (branch predictions vs realised coverage change) — deterministic, checkable per branch taken.
- H9 (whether a ~56-day maintenance interval holds items) — directly observable in FSRS stability.

**Cannot test:**
- H3. There is no Spanish-only counterfactual for one person. This needs a between-subjects design that does not exist.
- H6′ at the session-shape level. Comparing "many micro-sessions" against "one evening session" requires alternating regimes over months with matched material — possible in principle, not currently instrumented.
- Anything about generalisation. One person's result is a case report.

**Instrumentation already in the build:** per-language baseline tests with a threshold band; a per-lane bandit history (a 40-review reward buffer, from which learning progress compares the most recent ≤8 rewards against the ≤8 before them); an interaction log; corpus coverage at the 95% threshold per lane; FSRS stability and lapse counts per card; a path log of the last 12 branches taken.

**Known confound, stated up front:** the builder designed the system, so every "it worked" is contaminated by investment in it working. The instrumented measures above are the ones that can be checked against the record rather than against the feeling.
