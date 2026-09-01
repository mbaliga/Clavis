# Information Architecture

How Clavis is organised: what the screens are, what each one is *for*, what data sits behind them, and the rules that govern moving between them.

This describes the shipping build (v0.10). Where a decision was forced by evidence, the reference is given.

---

## 1. The organising idea

There is **one dataset** and three arrangements of it.

| View | The same words, arranged by… | Question it answers |
|---|---|---|
| **Learn** | time — what is due, what is next | *What should I do right now?* |
| **Cosmos** | relation — family, origin, familiarity | *What do I know, and how does it hang together?* |
| **Paths** | possibility — what each choice opens | *Where could I go from here?* |

This is the "ferrofluid" idea in its final form. It was originally read as a decorative blob; the builder corrected it: *seamless transitions between Learn ↔ Cosmos ↔ Paths — same dataset, different views, the fluid re-pooling between states.* The morph transition exists to make that literal — the same coloured drops gather and re-settle into the next arrangement, rather than one screen replacing another.

Everything else in the app is either a **prerequisite** (Letters), a **record** (You), or **apparatus** (Settings, Colophon, Story).

---

## 2. Screen inventory

### 2.1 Primary navigation — the bottom dock

Five destinations, always in this order. A bottom dock rather than top tabs, at the builder's instruction.

| id | Label | Icon | Sub-title shown in the top bar |
|---|---|---|---|
| `learn` | Learn | speech bubble | *(none — it is home)* |
| `cosmos` | Cosmos | scattered dots | "every word you've touched" |
| `paths` | Paths | branching tree | "what could come next" |
| `letters` | Letters | あ | "letters & sounds" |
| `you` | You | person | "your record" |

The dock is **hidden** during onboarding, during a baseline test, and during a session in progress. Those are modal states; leaving them mid-way is a deliberate action, not a tab tap.

### 2.2 Secondary destinations

Reachable, but not in the dock:

| id | Reached from | Purpose |
|---|---|---|
| `set` | gear icon, top bar | Settings — retention target, new-words-per-day, session size, readings on/off, mono-language sessions, motion |
| `colophon` | any `[n]` citation superscript | "evidence & credits" — the 40 in-app references, each linking to the paper |
| `story` | Paths → *Read freely* branch | "just reading" — free reading with no grading and no clock |

The colophon being reachable **from inside a claim** rather than from a menu is the whole point: the builder's requirement was that in-app claims be cited with links to the actual papers. Every `cite(n)` call renders a tappable `[n]` that jumps to the colophon and scrolls to that reference.

### 2.3 Modal states

These take over the whole view and suppress navigation:

- **Onboarding** (`!S.onboarded`) — runs once. Exists because the first prototype left the builder asking *where do I go, what am I learning*, and because the jargon (fringe, coverage) was alien to a first-timer.
- **Baseline test** (`T`) — a per-language yes/no vocabulary check with pseudoword foils, producing a frequency threshold. This is the heritage-speaker diagnostic (`04-personas.md`, P2): it decides what Clavis will *assume you already know* rather than what level you are.
- **Session** (`session`) — a run of encounters, then a summary.

---

## 3. The render contract

A single dispatcher, in strict precedence order:

```
render()
├── rollDay()                       reset per-day new-word counters if the date changed
├── morphBegin(key)                 capture the "from" shape before the DOM changes
├── if !S.onboarded  → renderOnboard()
├── else if T        → renderTest()
├── else if session  → session.done ? renderSummary() : renderEncounter()
└── else switch S.view
      cosmos | letters | paths | you | set | colophon | story | (default) learn
└── morphEnd()                      capture the "to" shape and animate between them
                                   (onboarding returns early and never reaches this)
```

Consequences worth knowing:

- **There is no router and no history stack.** `S.view` is a string in persisted state. Reloading the page returns you to where you were.
- **Modal states outrank the current view.** A session in progress cannot be navigated away from by tapping the dock, because the dock is not rendered.
- The morph only runs between the three body views — `morphBegin` receives `null` during onboarding, tests and sessions, which suppresses it.

---

## 4. Each view in detail

### 4.1 Learn — the pool

The default and the home. Deliberately close to empty.

- A **pool** — a soft aura of the active lanes' colours, canvas-rendered. It stirs and glows when words are due and sits still when nothing is overdue. It says which of the two it is, in words, rather than manufacturing urgency.
- A row of **lanes** — the nine languages with their mode (speak/read), what is due, and whether a baseline check has been taken.
- Tapping the pool starts a session.

The copy is the honest form required by `03-evidence.md` §5: *"A still pool — nothing is overdue. A session will meet a few new words."* There is no streak, no XP, no league.

### 4.2 The session — encounters

A session is a sequence of encounters. Two kinds come from the selection engine (§6); two are injected around it by `nextEncounter()`:

| Type | What it is | Evidence basis |
|---|---|---|
| `cloze` | A word blanked from a line you have met; recall it | Recall over recognition — Kang et al. 2007, ref `[30]` |
| `read` | A new scene with at least one unknown word — the scorer prefers three or fewer | ~10 encounters is the incidental-learning dose — Webb 2007, ref `[4]` |
| `echo` | A known line returning for fluency — injected at ~22% on any encounter after the first | Maintenance retrieval — Bahrick et al. 1993, ref `[27]` |
| `langid` | "Which language is this?" — name a line by its shapes. Offered at ~70% on the *last* encounter of a session when ≥3 lanes are active, and as the fallback when lane-picking fails | Confusion tracks geography, not genealogy — Skirgård et al. 2017, ref `[33]` |

**Every encounter is anchored to a comic page.** The scene renders cropped to its balloon by default, with a *See the whole page* toggle that letters **every** balloon on the page in the target language. Non-current balloons are tappable for their English gloss.

**The cloze integrity rule.** When the encounter is a cloze, the same gap appears in the balloon *and* in the caption — computed once and passed to both the cropped and full-page renderers. If a line cannot be safely gapped, the balloon is left unlettered entirely rather than risk showing the answer. On answering, both fill in together. This exists because an earlier build leaked the answer through the balloon, which the builder caught from a screenshot.

### 4.3 Cosmos — every word you've touched

Two modes over one dataset:

- **Field** (default) — a 3D-ish canvas of specks stretching to the horizon. Height is familiarity with the word, angle is language family, radius is relation to the family. Colour intensity carries familiarity in the top-down read. The ground fades into the page rather than sitting in a box. Entry animation: all dots start grey and flat at minimum size, and a wave of colour spreads from the centre, raising and brightening as it sweeps.
- **Explore** — a real graph, AntV G6, a legible ~1,250-word subsample, top-down. Tools: **Fisheye** (Sarkar–Brown distortion that spends the gained space on labels, refs `[36] [37]`), **Relations** (an edge-filter lens, ref `[38]`), **Families** (BubbleSets isocontours, ref `[39]`), plus pan, zoom, drag and undo/redo.

Selecting a word gives a card: its surface form, reading, origin, cognate links, and *See its web* — which hands off to Field mode focused on that word's neighbourhood.

The tools are cited because they are real published techniques, not house inventions; and they were rebuilt on G6 after the builder judged the hand-rolled versions "performative at best."

### 4.4 Paths — what could come next

The most opinionated screen, and the one carrying H8.

The engine computes a **fan of at most five branches**, each a genuine next step with the actual words it would use and what it opens up:

| Branch kind | Offered when | "Then" promise |
|---|---|---|
| `test` | a language has no baseline yet | "Clavis stops treating X as brand new" |
| `due` | cards are due in a lane | "each one you recall settles for days, then weeks" |
| `new` | a scene with few unknowns is available | "*n* more bubbles become readable" — a **computed** number |
| `read` | always, as a floor | "glimpsed words are relearned faster later" (refs `[15] [17]`) |
| `game` | ≥3 active lanes | "places scripts on the map, not just the family tree" (ref `[33]`) |

Two presentations: **As a tree** (cards down a spine) and **As a map** (G6 `d3-force` layout with BubbleSets grouping by kind).

Three rules that make it read as *paths* rather than a menu — all from builder feedback that renaming alone didn't fix the jargon:

1. **Variety is enforced** — at most two branches of any one kind, so the fan never collapses into five due-piles.
2. **Downstream is fuzzy and unpromised.** A tail card reads: *"and onward — the paths after these are not computed yet. They form from what you do next."* Faint nodes beyond each branch on the map are possible, not computed. This is an honesty requirement: `02-hypothesis.md` H8 says the graph-as-curriculum claim is unevidenced, so the UI must not imply an optimal plan exists.
3. **Choices are undoable.** Taking a branch logs it; an undo control reopens the fan.

Below the fan sits **the longer view, per language**: learning-progress state, due count, words held, % of the corpus readable, new-today against the daily cap, what the baseline assumes — plus two diagnostic lines that are the app's own interference instrumentation: *look-alikes that fooled you* (cross-language confusions) and *bridges that helped* (which language actually carried the meaning).

### 4.5 Letters — letters & sounds

Script introductions with sounds and readings, for the languages where glyph literacy cannot be assumed. Built because the builder had lost touch with hiragana and CJK/Arabic glyphs needed their own introduction rather than being assumed.

### 4.6 You — your record

Encounters, active days, cloze accuracy, per-lane holdings. A record, not a score. No streak.

### 4.7 Story — just reading

Free reading. Pick a language and an episode; the whole page is lettered in that language with numbered balloons; tapping a balloon speaks it and highlights the matching caption row. No grading, no clock.

Episodes with fewer than six aligned scenes in the chosen language are filtered out of the picker, because thin translation coverage would otherwise open the reader on a title card.

---

## 5. The content model

```
corpus (pipeline output, packed into the file)
├── scenes[]              649 usable, across 156 pages
│    ├── i                id
│    ├── e, p             episode, page
│    ├── m                page key → image (webp data URI) + size
│    ├── r                shared balloon rect, 0–1 page coords (legacy/fallback)
│    ├── q{lang}          PER-LANGUAGE balloon rect — the important one
│    ├── x{lang}          the line of dialogue in that language
│    └── k{lang}[]        tokens: [surface, lemma|0, zipf, bridges|0, reading?]
├── lex{lang}{lemma}      → [count, zipf, surface, reading]
├── etym{lang}{lemma}     → {o: origin string, ot: inh|bor, e: [[lang, lemma, type]]}
└── pseudo{lang}[]        pseudoword foils for the baseline test
```

**Derived indices**, built once at boot: `byLang` (scenes per language), `byLemma` (scenes per lemma), `byPage` (scenes per page — this is what makes whole-page lettering possible), `lexArr`.

**Why `q` exists.** The original extraction produced one English-derived rect per scene, credible ~45% of the time, which is why early builds showed blank balloons with a single pulsing marker. Re-extracting per language from the translators' SVGs raised usable geometry to **90%** and made whole-page lettering in any language possible. Details in `06-app-architecture.md` §3.

---

## 6. The selection engine

What decides the next encounter. Two stages.

**Stage 1 — which language.** A ZPDES-style bandit over lanes (ref `[10]`). Each lane's score combines its learning-progress weight, a due-pressure term, and a ×1.5 multiplier for productive lanes. Selection is proportional, with a 25% exploration rate ("trying something new"). A pinned lane overrides everything. The reason is always shown to the user in plain words — *"3 words ready to return"*, *"you're improving quickly here"*, *"getting to know you"*.

Learning progress is measured as the absolute change in mean reward between the most recent *n* rewards and the *n* before them, where *n* is at most 8 — i.e. **the lane where your results are changing fastest**, not the lane where you are doing best. That is the ZPDES property.

**Stage 2 — which encounter, within that lane.** `pickForLane()` returns only `cloze` and `read`; `echo` and `langid` are injected by `nextEncounter()` around it (see §4.2).

1. **Due cards first.** Take the oldest-due lemma, then choose among scenes containing it by highest known-word coverage, fewest unknowns, and not-seen-in-the-last-6-hours. → `cloze`.
2. **Otherwise a new scene**, if the daily new-word cap allows. Scored by coverage × 6, penalised for more than three unknowns, bonused for higher-frequency unknowns, with a small bonus for continuing the story from the last scene and a random jitter. → `read`.
3. **Otherwise a fully-known scene** for fluency. → `read` with `fluency: true`.

**Knownness** is deliberately three-valued: a card is known if it is FSRS-recalled at ≥0.75 retrievability and wasn't just lapsed; *or* if it has no card but its Zipf value clears the baseline-test threshold for that language — which is how the heritage diagnostic prevents already-owned words from arriving as "new."

**Lane choice repeats per encounter.** `nextEncounter()` re-runs `chooseLane()` for every encounter, avoiding the lane used by the previous one — so **sessions hop languages by default**, and the app says so on screen. A session is held to one lane only when a Paths branch set an intent, or when the `monoSession` setting is on (default off). This follows memo 01's within-session interleaving rather than memo 03's block-within-interleave-across; the conflict and its evidential weight are recorded in `03-evidence.md` §3.

**Scheduling constraints imposed by evidence:**
- No item repeats within a day unless it failed (`03-evidence.md` §5).
- A scene is not re-shown within 6 hours (`S.seen`).
- Semantically related batches are never introduced together (ref `[13]`, Nakata & Suzuki: ~3× confusion).

---

## 7. State

One object, `S`, persisted to `localStorage` under `clavis.v1`. Schema version `v: 2`, with a merge-on-load migration.

| Key | Holds |
|---|---|
| `lanes{l}` | `prod` \| `rec` \| `off` — the reception/production split (`03-evidence.md` §3) |
| `cards{l}{lemma}` | FSRS-5 card: stability, difficulty, due, last, reps, lapses, grade, first-seen |
| `tests{l}` | baseline result: completion time, frequency threshold, false alarms, bands |
| `bandit{l}` | `{w, hist[]}` — learning-progress weight and a 40-review reward buffer (LP compares the last ≤8 against the ≤8 before) |
| `inter{l}{other}` | cross-language confusions — the interference instrument |
| `routes{l}{other}` | which language actually bridged the meaning |
| `seen{sceneId}` | recency, for the 6-hour no-repeat rule |
| `newToday{l}`, `newDay` | daily new-word budget |
| `log[]`, `pathLog[]` | encounter record; last 12 branches taken |
| `settings` | retention target (0.9), new/day (10), session size (5), readings, mono-session, motion |
| `pin`, `view`, `coach`, `onboarded`, `ob`, `v`, `created` | navigation and schema |
| `intent`, `story`, `pathLog`, `cosmos`, `lastScene`, `pathView`, `refTarget`, `lastSession` | created lazily, not by `freshState()` |

**No account, no server, no sync.** One device, one state. This costs something real and `04-personas.md` says so.

---

## 8. Navigation rules

1. **Modal beats view.** Onboarding, test and session suppress the dock.
2. **View is state, not URL.** Persisted; a reload resumes.
3. **Citations are a destination.** Any `[n]` jumps to the colophon and scrolls to that entry.
4. **Branch-taking is a navigation act with consequences** — it sets `S.intent`, may switch view, and is logged and undoable.
5. **Cosmos handoff is one-way.** A word card in Explore hands off to Field focused on that word (*See its web*). There is no reverse handoff — Field's own *See its web* focuses within Field, and nothing focuses Explore on a given word. Mode is remembered.
6. **The three body views morph; everything else cuts.** Learn ↔ Cosmos ↔ Paths animate as one substance re-pooling. A jump to Settings or the Colophon does not.

---

## 9. Known IA weaknesses

Recorded rather than hidden.

- **Letters is stranded.** It is a prerequisite for the CJK and Arabic lanes but sits in the dock as a peer of Learn, so nothing routes a user to it at the moment they need it. It should probably be a branch kind in Paths.
- **Story is only reachable through a Paths branch.** Free reading is one of the more compelling things the app does and it is two taps deep behind a fan that may not offer it.
- **Settings is behind a gear**, which is where settings go, but `monoSession` and `readings` are pedagogically significant switches hidden as preferences.
- **The colophon is a one-way trip** — it scrolls to the reference but does not offer a route back to the claim that sent you.
- **`You` is thin.** It reports encounters and accuracy but not the things the hypotheses actually need: cognate vs non-cognate acquisition cost, origin-surfaced vs not, branch prediction accuracy. The instrumentation exists in state; the view doesn't surface it.
