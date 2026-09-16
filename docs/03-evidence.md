# Evidence

Madhav's claims, tested against the research corpus in `research/`. Where the evidence supports him it says so; where it complicates or contradicts him it says that instead, at the same volume.

This document exists because of a standing instruction from the builder: *evidence-based debate, not begrudging compliance*. It is written to be publishable alongside the app, and the in-app colophon links to a 40-source subset of it.

---

## 0. How to read this — a warning about the labels

The seven research memos **do not share one labelling scheme**, and a published document that flattens them into one would be misrepresenting its own sources.

| Memo | Scheme actually used |
|---|---|
| 01 Multi-language pedagogy | `STRONG` / `MIXED` / `PRACTITIONER` / `NOT VERIFIED` |
| 02 Mother tongue in India | `[Strong]` / `[Mixed]` / **`[K]`** — where `[K]` means *not fetched this session*, a **provenance** marker, not a strength grade |
| 03 Coverage, SRS, microlearning | `[strong]` / `[mixed]` / `[anecdotal]` / `[unverified this session]` |
| 04 Competitors & market | **No evidence grades at all.** `[unverified]`, "[my estimate]", "[fetch blocked]" |
| 05 Data & platform | `[verified 2026-08]` vs `[unverified]` — provenance only |
| 06 Culture, motivation, games | `STRONG` / `MIXED` / `ANECDOTAL` |
| 07 Media & music | **No grades.** `[fetched]` vs `[search-snippet]` provenance tags only |

Consequences worth stating plainly:

- **"PRACTITIONER" does not exist** outside memo 01. Memo 06's equivalent is `ANECDOTAL`.
- **Memo 07 assigns no strength grades at all.** Any claim that a media or music finding is "strong" is an interpolation, not something a memo said.
- **Two memos ran out of search budget mid-way** (01: "my web-search budget was exhausted part-way"; 02: "search budget exhausted after ~25 queries"). This is a *systematic* gap, not a random one — the unfetched sources cluster in the areas searched last.
- **Memos 04–06 and memo 07 describe partly different products** (an Indic-languages + smartwatch concept vs. the nine-language *Pepper&Carrot* build). Read the market and platform material with that in mind.

---

## 1. "The 90–10 rule: 10% of a language serves 90% of use cases"

**Verdict: directionally right, numerically wrong in both terms, and wrong about "use cases."**

Zipf's law holds (exponent 2.03 ± 0.15, Moreno-Sánchez et al. 2016). The top ~100 English word forms cover ~50% of running text; the top ~1,000, ~80%. Nation 2006, on BNC word-family lists:

| Text type | 2,000 families | Mid tier | 98% reached at |
|---|---|---|---|
| Novels (5 combined) | 87.83% | 4,000 + proper nouns = 94.8% | 9,000 + PN = 98.24% |
| Newspapers | ~83% | 4,000 + PN ≈ 95% | 8,000 + PN ≈ 98% |
| Conversation | 89.35% | 3,000 + PN = 96.03% | 6,000 + PN = 97.67% |
| Talk-back radio | 89.41% | 3,000 + PN = 96.52% | 6,000 + PN = 98.26% |

Nation's own conclusion: 98% coverage needs **8,000–9,000 families for written text, 6,000–7,000 for spoken**. Adolphs & Schmitt 2003 tightened the spoken figure further — on CANCODE, 2,000 families gave *less than* 95% coverage, overturning Schonell et al.'s 1956 claim of ~99%.

**The strategy survives; the numbers do not.**

- **Numerator.** A 3,000-family core is ~15–25% of a native family vocabulary, not 10% — though far less than 10% of the word-form space. Brysbaert et al. 2016 (n = 221,268): an average 20-year-old American English speaker knows ~42,000 lemmas, ~11,100 of 18,269 word families. The unit swings the result 2–4×.
- **Denominator.** Coverage ≠ comprehension. Hu & Nation 2000 (n=66): at 95% coverage the majority still did not adequately comprehend; only at 100% did 15/17 succeed on multiple choice. Schmitt, Jiang & Grabe 2011 (n=661, 8 countries): comprehension rises **linearly** 90→100% with **no threshold jump**.
- **Mechanism.** The 50% covered by the top 100 forms is *the, of, and, to, a, in* — near-zero meaning. The residual 5% at 95% coverage carries a disproportionate share of the content, which is why comprehension sits at ~50–60% there.
- **"Use cases."** Coverage studies measure reception only. Milton 2010: vocabulary size explains up to 72% of variance in reading, ~52% writing, ~39% listening, 40–61% speaking.
- **Listening is the best case for the claim.** van Zeeland & Schmitt 2013: most listeners adequately comprehended informal narrative at 90% coverage (means 7.35/10 at 90%, 8.22 at 98%, 9.62 at 100%).

**The complication that matters most for a corpus-driven product:** Cobb 2007 — beyond the first 2,000 families, words recur too rarely in ordinary reading to be acquired incidentally. The mid-frequency band **must be taught deliberately**. The 90–10 framing implies the other 90% looks after itself; the evidence says the opposite, and this is precisely where a curriculum earns its keep.

**Adopted formulation** (used in the app): *a core of ~2,000–3,000 word families lets you follow most everyday conversation and is the highest-leverage investment a learner can make; beyond that the return per word drops by Zipf's law, and reading needs ~8,000–9,000 families.*

---

## 2. "Indians educated and working in English show less command of their stated mother tongue than of English"

Madhav flagged this himself as an unresearched personal observation to be validated. **The validation comes back split: supported in a narrow register-specific form, refuted in its broad form, and his own sampling method is a certain confound.**

**Supported, narrowly.** Among urban English-medium-educated Indians, command of the *formal/academic register* and of *script literacy* in the declared mother tongue is typically weaker than in English, and adult professional vocabulary lives mainly in English. The best Indian data on exactly this question — Sarma 2025, IIT Bombay, n = 1,375 first-years:

- \>90% studied school subjects in English; English acquired at ~6.8 years, with **reading fluency (10.3 y) arriving before speaking fluency (11.5 y)** — the reverse of a home language.
- Two-thirds prefer to read in English; self-rated English reading clusters at 9–10/10 while regional-language **speaking is bimodal** — peaks at both ends, no middle ground.
- **39% report having forgotten a language** (Sanskrit most, 12.6%).
- Authors' conclusion: academic and technical domains "have been ceded to English/Hindi, and any literacy skills, if gained, are either eroded or lost."

Corroborating: Pune Marathi speakers describe Marathi as a "kitchen language… increasingly irrelevant for professional use"; of ~200 Grade-7 Marathi Olympiad entrants in 2016, **one** came from a Marathi-medium school.

**And the pipeline explains why he sees it in Hyderabad specifically.** UDISE 2019-20: 26% English-medium nationally, but **Telangana 73.8%**; Telangana UG admissions 2023-24: **93.55% English medium vs 5.77% Telugu**. A Telangana-born professional under ~35 has a >70% chance of English-medium schooling and >90% for their degree. Hyderabad is the epicentre of the phenomenon he is describing.

**Refuted in the broad form.** Census 2011: English as first language 259,678 people (0.02%); all-English-speakers ~129.3 m ≈ 10.6%. The Lok Foundation/Oxford/CMIE survey puts it at 6% (urban 12%, rural 3%; richest 41%, poorest 2%). For the other ~90%, the mother tongue is unambiguously the stronger language in every skill. ASER 2023 (rural 14–18, n = 34,745): **75% can read a Std-2 text in their regional language vs 57.3% in English.**

**Contradicted even inside his own sample.** Everyday oral fluency is intact: **0% of IIT Bombay students use English with parents and elders** (Hindi 53%, regional 47%). At an English-medium Mysore school, 90.6% of children socialised in Kannada despite 98% of parents teaching home literacy in English. Those who said they "forgot Hindi" typically meant *formal* Hindi, not the Hindi they speak at home. Regional-language media is growing, not dying: Indian-language internet users 234 m (2016) → projected 536 m (2021) against English 175 m → 199 m; 9 of 10 new users are Indian-language users.

**The methodological objection, stated as the memo states it.** He samples people in education and work — the two domains where English is institutionally mandated. Even a perfectly balanced bilingual would look English-dominant there, and the sample excludes the ~90% without English. Grosjean's complementarity principle predicts exactly this. The memo's formulation: *judging someone's Telugu at the office is like judging someone's English at a wedding in their village.*

**And the claim rests on a definitional confusion.** The Census records mother tongue "as declared, without any test," and the Registrar General's own caveat says the data reflect the respondent's *designation* of their linguistic medium and "need not be identical with the actual linguistic mediums." When someone "claims" a mother tongue they are using the term exactly as the Indian state does — as an ancestry label. The divergence he noticed between claim and competence is not a contradiction; **it is what the definition permits.**

**The mechanism is not incompetence.** Cummins' BICS/CALP: conversational fluency takes ~2 years, cognitive-academic proficiency 5–7 years *and requires schooling in that language*. Mother-tongue CALP was simply never built. The profile — native phonology and listening, reduced productive vocabulary confined to home domains, informal registers only, often no literacy — is the textbook heritage-speaker distribution.

**Defensible restatement:** *Among English-medium-educated urban Indians, the mother tongue is a heritage language: strong in listening and everyday speech, weak in reading, writing, formal register and adult-domain vocabulary — which is precisely the part of the language that shows up at work.*

**The central gap, worth flagging loudly:** no study was found that objectively tests *vocabulary size* in the mother tongue vs English among English-medium-educated Indian adults. Everything above is self-report, children's data, or ethnography. Confidence is high for the pattern, moderate for magnitude. An app that ran matched picture-naming or lexical-decision tasks in both languages could generate the first such data.

---

## 3. "Learning several languages at once, grouped by family, works and beats sequential study"

**Verdict: the claim bundles two propositions the evidence pulls apart. Simultaneity is supported — but only for *distant* pairs. Family grouping is supported — but only for *receptive* work. Combined at beginner level with productive goals, it is the one configuration the research calls "risky; not evidence-based."**

**Simultaneity, distant pairs: supported, thinly.** Three studies, all distant pairs.
- Rahmatian & Farshadjou 2013 (Persian L1; English + French): 36 adults; simultaneous groups scored higher in French (67.3 vs 52.3). *Tiny n, no delayed test.*
- Huang et al. 2022 (Chinese L1; English vs English + Russian, one year): the dual-language group "did not develop their L2 to a lesser extent" — only more week-to-week variability in writing fluency.
- Libersky et al. 2025 (German + Polish vocabulary): interleaving beat blocking — **but a rest break removed the difference, so the benefit was a spacing effect, not a multilingual one.**

**Family grouping, for reception: supported.** MICReLa (Gooskens et al. 2017; 1,833 listeners, 16 languages): Portuguese→Spanish 77.4%, Italian→Spanish 65.7%, Czech↔Slovak 92.7/95.0%, with ~40% cloze as a working threshold. Marx 2012: German students reading five unknown Germanic languages showed lexical comprehension substantially outperforming structural comprehension — **vocabulary transfers first, grammar lags.** Every mature intercomprehension programme restricts itself to reception, and none claims productive outcomes.

**Family grouping, for production, concurrently, from zero: explicitly not supported.** Memo 01 states it flatly — *"no supporting study; the sibling-language interference literature gives principled reasons to expect blends and slowed restructuring. Risky; not evidence-based."* And: *"What is missing is exactly your case: two closely related targets, both at A0–A2, with productive goals."*

The interference findings are individually solid:
- **Carvalho & da Silva 2006:** learners of Portuguese rely heavily on Spanish whether Spanish is their L1 or L2 — *linguistic similarity overrides order of acquisition.*
- **Lipski 2006:** fossilised "portuñol" hybrids; the similarity itself "creates interference rather than facilitating learning."
- **Williams & Hammarberg 1998:** 844 non-adapted switches over two years — the closest, most-recent non-native language became the **default supplier** of lexical material.
- **Ringbom 2007:** in production, similarities are "merely assumed," and learners build oversimplified one-to-one equivalences that later have to be unlearned.
- **Gil 2026:** Japanese-L1 learners of Korean did *worse* than English-L1 learners despite structural near-identity; Korean-L1 learners of English did better than Chinese-L1. Close languages require *restructuring* an entrenched near-match; distant ones require *developing* a new structure, which is easier.
- **Nakata & Suzuki 2019:** semantically related sets produced ~3× more within-set confusion (8.22% vs 2.69%), and **spacing did not remove it.**

**"Better than sequential" is unevidenced in both directions.** No controlled study shows concurrent productive study of two siblings is worse than sequential; equally, nothing shows it is better. Publish as an open question.

**Practitioner opinion runs against him:** get a basic grasp of one before adding another; choose two *very different* languages; weight time unevenly; keep separate sessions and contexts.

**What the design actually adopted** — and this is the largest single research-driven change to the product:

1. **Receptive-first, family-wide; productive one-at-a-time.** Reading and listening across a whole family; speaking/writing in a second sibling gated until the first is solid.
2. **Pair a productive target with a distant one, not a sibling.** Spanish + Japanese production is evidence-compatible; Spanish + Portuguese is not. Hence the lane split: **es, nl, zh, ar productive — one per family**; fr, it, de, ja, ko receptive.
3. **Never schedule cross-language false friends adjacently** (ref `[13]`). Note what the build does *not* adopt: memo 03's block-within-a-session rule. Sessions hop languages by default — memo 01's recommendation — with a mono-language mode available as an opt-in setting. See the cross-memo conflict below.

**A cross-memo conflict left unresolved, and worth publishing as such:** memo 01 recommends interleaving languages *within* a session; memo 03 recommends the opposite. Memo 01's own source (Libersky et al.) attributes its interleaving benefit to spacing rather than to interleaving, which weakens memo 01's side. **The build nonetheless follows memo 01** — sessions hop languages, and the app says so on screen. Since that is the weaker-evidenced of the two options, flipping the default to blocked-within-session is a cheap experiment the N=1 setup could actually run.

**And the Indic evidence base is close to empty.** No published lexical-similarity coefficient for Konkani–Marathi, Hindi–Marathi, Hindi–Gujarati, Hindi–Punjabi or Hindi–Nepali could be retrieved. No measured intelligibility figure for any Indic pair. No experimental evidence for Brahmic→Brahmic reading transfer — treat it as a structural argument, not a measured effect. For a method that ultimately wants to serve Indian language families, this is the largest gap in the entire corpus.

---

## 4. "Etymology and cognate awareness beat rote memorisation"

**Verdict: a false dichotomy, and the sequencing is backwards in the original form.**

**The direct contradiction.** Barcroft 2002: *"increased semantic processing can inhibit the ability to encode the formal properties of new words."* Elaborate meaning-work at first exposure steals resources from learning the form. The resolution is a sequencing rule, not an endorsement: **form first, story second.** Etymology is a *retention and inference* tool, not a first-contact tool.

**No study compares "etymology" against "rote."** They compare cognate vs non-cognate *items*, semantic vs mnemonic *elaboration*, morphology instruction vs none. Two specifics cut against the framing:

- Lotto & de Groot found learning via L1 translation **beat** learning via pictures — the plainest paired-associate format won the comparison actually run.
- What "rote" means in an app is **spaced retrieval**, which is the largest effect in the entire evidence base: Karpicke & Roediger 2008 — ~80% recall after a week with continued testing vs 33–36% without, **d = 4.03**; spaced retrieval meta-analytic g = 0.74; L2 spacing g ≈ 1.15. Nothing in the etymology literature approaches those numbers.

**The defensible claim is additive:** etymology and cognate awareness make items cheaper to acquire and more resistant to forgetting, and morphology generalises to unseen words — but they are a **multiplier on spaced retrieval, not a replacement for it.**

**Two further complications the design has to handle:**

1. **Learners ignore cognates unless taught.** Nagy et al. 1993: 74 Spanish–English bilingual children recognised only about **half** of available cognates *even when explicitly told to look*; the Spanish-vocabulary→English-comprehension link was positive only for good cognate recognisers (r = .52 vs −.60). Suffix-changing pairs (*reality/realidad*) were hardest. The feature must be taught as a **strategy**, not surfaced as trivia.
2. **False friends are memorable, which is the problem.** Marecka et al. 2021: false cognates were learned *faster* than non-cognates. The failure is semantic (the wrong meaning persists), not memorial. An etymology-forward design amplifies both the signal and this failure mode.

**Implemented:** origins are gated behind the second encounter; the in-app colophon states the reason at reference `[25]`.

---

## 5. "Micro-doses through the day beat a scheduled session, and embed language subconsciously"

Covered as H6 in `02-hypothesis.md`. Summary of the three parts:

- **Spacing/retrieval: strongly supported**, but the evidence-backed gap is *days* (Cepeda et al. 2008, n=1,354: 64% higher recall at the optimal gap, d ≈ 1.1; optimal gaps 1 day for 7-day retention, 11 days for 35, 21 days for 70+). Schedule *shape* barely matters — expanding vs uniform, g = 0.034, n.s.
- **Many micro-sessions vs one: no direct RCT found.** Roughly neutral per review, positive for adherence. Re-reviewing the same item within a day is mostly wasted. Set size scarcely matters (Nakata & Webb 2016: 4/10/20-item sets — "spacing dominates set size"), so 3-item sessions are fine *if the spacing is right*. Supporting work is thin: WaitChatter (20 users, 2 weeks, median exercise 1.83 s, ~4 new words/day); WaitSuite bounds the format to waits where switch time < wait duration (~50-second elevator waits worked, sub-second Wi-Fi connects did not); MicroMandarin actually cuts the other way — the contextual micro-version produced shorter sessions in more places but the frequency-ordered version **taught more cards (41.3 vs 19.1)**.
- **"Subconscious": contradicted.** Schmidt 1990: subliminal language learning is impossible; noticing is necessary and sufficient for converting input to intake. Schreiner & Rasch 2015: sleep cueing *reactivated* already-learned pairs, and the same cueing while awake did nothing — what helps is good encoding before sleep, not audio overnight. Webb 2007: incidental acquisition needs ~10 encounters in context for sizeable gains. Passive ambient exposure without attention has **no supporting evidence and is contradicted by the noticing literature**.

**Publishing constraint adopted verbatim from memo 03:** do not say "learn while you're not paying attention," "learn in your sleep," or "passively absorb." The honest claim is **automaticity through spaced retrieval**.

**A hard delivery constraint if bites arrive by push:** the only fatigue figure available is self-report and its URL was unretrievable — 46% would disable push at 2–5 notifications/week, 32% would abandon at 6–10/week. The rule adopted: 1/day default, hard cap 3/day with explicit opt-in, never within 60 minutes of the last interaction, never in quiet hours; everything else *pull*, not push.

---

## 6. "A culture/geography layer (Busuu × GeoGuessr) increases engagement and retention"

**Verdict: partially supported for vocabulary learning; largely unsupported for engagement and retention, which is the actual claim.**

**Supports.** Content-integrated instruction grows receptive vocabulary (EMI-CLIL meta: d = 0.73 post / 1.01 delayed, N = 7,434, vocabulary the largest effect) — `STRONG`. Narrow, topic-bound reading recycles vocabulary — `MIXED→STRONG`. Culture as retrieval cue (context reinstatement, d ≈ 0.3, small). Duolingo's highest-streak languages are the heritage/culture-motivated ones (Scottish Gaelic, Welsh, Yiddish) — the best available observational link, explicitly correlational.

**Complicates.**
- **The CLIL effects are learning outcomes, not engagement**, and carry an explicit selection-bias discount: the 2023 meta found effects *larger* in studies that did not confirm baseline equivalence, and Goris et al. found no significant longitudinal receptive-vocabulary advantage in most studies.
- **Motivation research says culture is a weak lever.** Integrative/instrumental orientations correlate ≈ .20/.16 with achievement. Ideal-L2-self explains ~4% of variance in objective achievement, while **learning experience correlates .66 with effort**. The design principle that follows: measure behaviour, not stated motivation.
- **Order matters, and the naive design gets it backwards.** Unfamiliar cultural content is *harder* to process (Steffensen et al.), and pre-reading activities could not compensate. Dropping a learner into unfamiliar cultural material makes the language input less comprehensible unless the schema is built first.

**Contradicts.**
- **There is essentially no evidence that geo-game play produces language learning.** Girgin 2017 is 103 teacher candidates, qualitative, **no control group**; 35% merely *said* it helped. No later controlled study with knowledge tests was found.
- **Skirgård et al. 2017 — 15 million guesses, the largest dataset in this space — explicitly assume casual players learn little from play.** That is the closest thing to a direct contradiction of the transfer premise.
- "Does script-recognition play transfer to script literacy?" is listed as an open gap with **zero evidence either way**.
- The named reference point, **Geo Challenge (Playfish, 2008–10), is itself an unverified citation** (fetch blocked, from memory).

**One supportive twist worth keeping:** because players confuse languages by **geography, not genealogy**, a geography layer is a good *complement* to family grouping rather than a competitor to it. This is the justification for the "Which language is this?" game, and reference `[33]` in the colophon states it.

**Honest formulation:** a culture/geography layer is well supported as a *vocabulary-encoding and content-organising device* and is commercially unoccupied. There is **no direct evidence** that it increases engagement or retention.

---

## 7. "Multi-media recall works — the tingle"

**The best-supported claim in the set.** Full treatment as H7 in `02-hypothesis.md`. Key findings:

- Familiarity without recollection is a real, named memory signal (Yonelinas 2002).
- Incidental exposure leaves enormous traces (Standing 1973, 10,000 pictures; Brady et al. 2008, 2,500 objects at 87–92%).
- "Forgotten is not gone": savings in relearning (Nelson 1978; MacLeod 1988), and most on-point, **Bowers, Mattys & Gage 2009** — adults with lost childhood Hindi/Zulu exposure relearned phoneme contrasts better than controls *despite no explicit memory*.
- Pre-exposure measurably speeds later intentional learning (Elgort et al. 2024; Durso & Shore 1991; Pavia et al. 2019).
- **Contextual diversity is the direct experimental hit:** Frances et al. 2020 — at identical exposure counts, 8 texts beat 1 on recall (ηp² = .14), recognition (.11) and meaning-matching (.04), with identical benefits in the foreign language. Rosa et al. 2017 — three genres beat one on all four measures.

**Limits.** Incidental encounters do not reliably produce recallable form–meaning knowledge on their own — they feed explicit study, which still has to happen. *Cosmetic* variation does not work: the rule is variability in **context**, constancy in **form and meaning**. And the specific example — film credits — is **unstudied**; it is an instance of incidental pre-exposure supported by analogy, not by direct evidence.

**Provenance caution.** The strongest items here split by tag: `[fetched]` — Frances 2020, Rosa 2017, Souza & Barbosa 2023, Ludke 2014, Salcedo 2010. `[search-snippet]` — Yonelinas, Standing, Brady, Nelson, MacLeod, **Bowers**, Zajonc, Adelman, Durso & Shore, **Elgort et al. 2024** (publisher 403'd). The entire savings/pre-exposure chain rests on snippets. Anything built on Bowers or Elgort should be re-verified against the primary paper before publication.

---

## 8. "Music and audio are worth adding as a distinct modality"

**Verdict: supported under narrow, non-obvious conditions stated as hard constraints — and one well-designed study found the retention benefit gone at two weeks.**

**Supports.** Ludke et al. 2014 `[fetched]`: singing beat speaking on **verbatim production, d = 0.75**, and beat rhythmic speaking on a delayed conversation test, **d = 0.95** — exactly the skill a phrase-based app targets. Good et al. 2015: sung > spoken poem on verbatim recall, vowel pronunciation and translation, persisting after **6 months**. Pavia et al. 2019: gains rise with repeated listening and in-song item frequency. Salcedo 2010: involuntary rehearsal ("the din") in 66.7% vs 33.3% (χ² = 7.22, p < .03).

**Complicates.**
- Ludke found **no group differences** on recognition, multiple-choice vocabulary, or L1 recall. The benefit is specifically verbatim production.
- **Salcedo's delayed test showed no group difference at 2 weeks** (F = 0.94, p = .40). The earworm is real and measurable but did not by itself preserve a recall advantage.
- **Wallace 1994 is a boundary condition, not a caveat:** with a different melody per verse, sung recall fell *below* spoken. Melody helps only once the melody itself is learned — i.e. short, strophic, same tune every repetition.
- A 2024 *System* review: positive-but-heterogeneous effects with many weak designs — promising, not proven at scale.
- **Audio as ambience is affirmatively harmful.** Souza & Barbosa 2023: lyrics impaired verbal memory d = −.32, visual memory −.33, reading comprehension −.19 — and users *misjudge this*, correctly believing lyrics hurt while wrongly believing instrumental helps. Plus the seductive-details meta (Sundararajan & Adesope 2020). "Add music" implemented as background audio would make the app measurably worse.

**Implemented:** nothing plays behind exercises. Reference `[23]` says why.

**Licensing is the practical blocker.** Real songs with vocals are the hardest category: Great 78 public domain is US-only; Free Music Archive needs track-by-track NC/ND filtering; composition PD ≠ recording PD for anthems. The only globally clean path is **public-domain folk lyrics re-sung in-house or by the community** — which happens also to satisfy Wallace's simple-repeated-melody constraint.

---

## 9. "There is a market gap: no popular platform teaches several languages at once"

**Verdict: half right, and the wrong half matters commercially. The gap is *linking*, not *multiplicity*.**

**Contradicted as worded.** Every app surveyed lets a paid user open several courses, and at least seven monetise "all languages" (Glossika, Clozemaster, Lingvist, Memrise, Babbel Complete, uTalk, Mondly lifetime, Pimsleur All Access). **Glossika Pro explicitly sells simultaneous multi-language study across 60+ languages.** Duolingo *measures* the behaviour, ranking "polyglot countries" (learners studying 3+ languages) in its 2025 report.

**Supported once restated.** *"None connects the courses: no shared-lexicon view, no cognate scheduling, no family grouping."* That read-across is unqualified. The nearest relatives: **Language Transfer** (audio, free, donation-funded, 8 courses — but one language at a time) and Clozemaster's source→target pairings. The family/intercomprehension idea "lives in academia and hobby projects": EuroComRom is a book; the only software found, EuroRomCom on GitHub, has **26 stars and 1 fork**. The word-graph niche has one traction-bearing app — Etymology Explorer, ~50K Android installs after ~8 years, English-centric, last updated Aug 2024.

**Complications.** Demand is thin — multi-language learners are a minority everywhere, and Duolingo's own guidance frames it as "double your study time." Indian app demand is overwhelmingly English-acquisition; foreign-language learning there is a niche within a niche. Content cost is real: family-aware lexicons across 12 languages is a linguistics project, and a solo developer should scope to 2–3 families. And **the interference risk must be demonstrated as reduced, not assumed.**

**A neat detail:** Glossika's own blog advises against two beginner languages while selling exactly that — a marketing/pedagogy contradiction that family-aware scheduling could actually resolve.

---

## 10. "Thoughtful gamification sustains motivation better than streaks and points"

**Verdict: half supported, half contradicted. The evidence supports removing tangible rewards and leaderboards. It does *not* support the claim that any gamification sustains motivation, and it contradicts the implied dismissal of streaks.**

**Supports.** Deci, Koestner & Ryan 1999 (128 studies, `STRONG`): expected tangible rewards undermine free-choice intrinsic motivation **d = −0.36**, while positive verbal feedback **enhances it, d = +0.33**. Hanus & Fox 2015: badges + leaderboard + coins produced *lower* intrinsic motivation, satisfaction and **lower final-exam scores** over 16 weeks. Al-Hoorie 2018 (`STRONG`): learning experience correlates **.66** with effort — the best available support for "make the thing itself good."

The "thoughtful" specifics are the best-evidenced material in the corpus: recall > recognition (Kang et al. 2007, d ≈ 0.41); always show the answer (Butler & Roediger 2008); expanding spacing (+18 pp vs +8 pp); desirable difficulties (Bjork & Bjork 2020) — micro-games that feel easy are teaching little; timed rounds only on already-known items.

**Contradicts.**
- **The killer caveat cuts both ways.** Sailer & Homner 2020: g = 0.49 cognitive / 0.36 motivational / 0.25 behavioural — **but restricted to rigorous designs, motivational and behavioural effects become non-significant; only cognitive effects survive.** The literature therefore cannot support *any* claim that gamification sustains motivation, thoughtful or otherwise.
- **Streaks demonstrably work at scale.** ~9M Duolingo users hold 365+-day streaks; two streak freezes at onboarding beat one; earn-back repair was a retention winner. This is anecdotal but is the largest behavioural dataset in the corpus, and **nothing shows a non-streak mechanic outperforming it.**
- **Streak anxiety, the usual argument against streaks, has no rigorous quantification.**
- The recommendation in the research is not "no streaks" but **forgiving streaks** — freezes, earn-back, no penalty for one missed day — justified by Lally et al. 2010: median 66 days to automaticity, and **missing one day did not materially dent the curve.**

**What the build actually does:** no XP, no leagues, no leaderboard, no loss-aversion mechanics — and also no streak. That is a *stronger* position than the evidence requires, taken on design grounds rather than evidential ones, and this document should not pretend otherwise. References `[28]` and `[29]` cover the reward and habit findings.

---

## 11. "An open-source, offline-first, no-account app is viable"

**Verdict: technically viable with named non-trivial blockers; commercially plausible but unproven; and the "no-account" half is never examined in any memo.**

**Supports.** Precedents exist — AnkiMobile at $24.99 one-time with a free open-source Android sibling; Language Transfer, free and donation-funded, solo creator, no telemetry. One-time purchase is a genuine differentiator against every top-10 competitor. The whitespace is explicit: *"Privacy-first, one-time-purchase language app on F-Droid. Literally none with real content."* Technically the core loop is solved: prebuilt read-only SQLite + FTS5 (30–45 MB per 50k entries), FSRS portable from MIT-licensed implementations, queue query <5 ms. The licence architecture works — **CC BY-SA data pack + own-licence app code, commercial use permitted.**

**Named blockers.**
- **Offline ASR for Marathi/Telugu/Kannada/Urdu/Konkani is not solvable offline with permissive models today.** Do not promise phoneme-level scoring for Indic.
- **Kannada and Konkani have no offline permissive TTS voice** — a *direct consequence* of excluding MMS-TTS on NC grounds. The exclusion has a real product cost and should be recorded as a trade, not a free win.
- **Content coverage is the real constraint:** Konkani 1,252 Wiktionary entries ("essentially a seed list"), Kannada 2,482, Marathi 5,140.
- **Notifications — the core micro-SRS surface — are structurally unreliable on Android.** `USE_EXACT_ALARM` is Play-restricted to alarm and calendar apps; inexact alarms fire within ~1 h; `POST_NOTIFICATIONS` is off by default on Android 13+; Android 16 Live Updates explicitly disallow "quick access to app features"; and Indian OEMs score 5/5 on dontkillmyapp. **Prefer a widget and an open-the-app model over guaranteed-time notifications.**
- **F-Droid will not accept opaque prebuilt databases without a reproducible generator** — so the corpus build pipeline must be published. (It is: `clavis-pipeline.zip`.)
- **Watch sync is incompatible with an F-Droid build** — the Wear Data Layer requires Play services on both ends.

**Unexamined:** no memo tests whether accountless design helps or hurts retention, conversion or piracy. Treat "no account" as a design premise, not an evidenced choice.

---

## 12. What the research does not cover

Stated so nobody mistakes silence for support.

1. **Adaptive-learning-platform outcomes, mastery learning, intelligent tutoring systems** — not searched at all. H8 (the graph as curriculum) therefore rests on mechanism, not outcome evidence.
2. **Any Indic lexical-similarity or intelligibility coefficient** — none retrievable. The largest gap for the method's eventual Indian application.
3. **Brahmic→Brahmic reading transfer** — no experimental evidence.
4. **Objective mother-tongue vs English vocabulary testing in Indian adults** — no study exists.
5. **Geo-game → language-learning transfer** — zero evidence either way.
6. **Whether timed recognition micro-games beat untimed retrieval** — explicitly listed as untested.
7. **Accountless design effects** — never analysed.
8. **`xkcd`, `NLLB`, `Wikinews` and `Global Voices` are not mentioned in any memo.** Their inclusion or exclusion cannot be justified by reference to this research.
9. **Pepper&Carrot has no licence documentation anywhere in the research** — despite being the corpus the whole build stands on. Verify independently before any commercial step.

---

## 13. Do not quote without re-verification

The memos' own lists, consolidated. These are cited in the corpus but were **not read from a primary source** in the session that produced it.

**Blocked or unfetchable:** Craik & Lockhart 1972 (levels of processing — the theoretical foundation of the elaboration argument); Rothman 2015; Otwinowska et al. 2020 and Otwinowska & Szewczyk 2019 (effect sizes); Boers 2001 and Boers, Demecheleer & Eyckmans 2004; Sagarra & Alba 2006; Kellerman 1983; Weiss et al. 2009; Daulton 2008's "≈45% of the 3,000 most frequent English words" katakana figure; Bahrick 1984 (APA blocks robots); Elgort et al. 2024 (publisher 403).

**Secondary-source dependencies to replace:** memormore.app carries the figures for Kim & Webb 2022, Bahrick & Phelps 1987 and Chukharev-Hudilainen & Klepikova 2016. ERIC review EJ1316858 carries Laufer 1989, Hu & Nation 2000, van Zeeland & Schmitt's means, and Durbahn et al. 2020.

**Search-URLs, not record URLs** (must be resolved to stable links): Wang & Thomas 1995; Barcroft 2002. Goodwin & Ahn 2013 has an ERIC ID and no URL.

**Memo 02's entire `[K]` block** — Grosjean, Cummins, Polinsky & Kagan, Montrul, Benmamoun et al., all the attrition science, and the whole global-analogues table (Singapore, Philippines, Ireland, Wales, Kenya/Nigeria, US Hispanic, Hong Kong). Memo 02's own instruction: *verify `[K]` figures before quoting them externally.*

**Licences marked "must-check-before-shipping":** Etymological Wordnet (CC BY-SA 3.0?), Hermit Dave FrequencyWords, Leipzig corpora, NorthEuraLex, KartaView, Panoramax, and **every individual Piper voice** (some are CC BY-NC).

**Unverifiable Indic figures:** any lexical-similarity or intelligibility coefficient for Konkani–Marathi, Hindi–Marathi, Hindi–Gujarati, Hindi–Punjabi, Hindi–Nepali; Nag & Snowling's akshara-transfer findings; the Marathi Portuguese-loanword list; the claim that Korean L1 speakers learn Japanese fastest; the exact Ethnologue Spanish–Catalan coefficient (0.85 vs 0.87).

---

## 14. Scorecard

| # | Claim | Verdict |
|---|---|---|
| 1 | 90–10 rule | Directionally right, numerically wrong; restated in the app |
| 2 | Indian mother-tongue attrition | Supported narrowly (register/literacy); refuted broadly; sampling confound certain |
| 3 | Family-grouped simultaneity | Reception yes, production no; "better than sequential" unevidenced |
| 4 | Etymology beats rote | False dichotomy; etymology is a second-meeting multiplier on retrieval |
| 5 | Micro-dosing | Spacing yes; session-shape neutral; "subconscious" contradicted |
| 6 | Culture/geo layer | Good for vocabulary encoding; no evidence for engagement/retention |
| 7 | Multi-media recall | **Best-supported claim**; contextual diversity is a direct hit |
| 8 | Music as modality | Yes, narrowly — verbatim production; not as ambience |
| 9 | Market gap | Gap is *linking*, not multiplicity |
| 10 | Thoughtful gamification | Reward findings yes; "sustains motivation" no; streaks defended by data |
| 11 | Open-source offline-first | Viable with named blockers; "no account" unexamined |
