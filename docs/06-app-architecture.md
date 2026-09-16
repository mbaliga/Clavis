# App Architecture

How Clavis is built. Three layers: a **corpus pipeline** that turns open-source comics into annotated parallel data, a **template** that is the entire application, and a **packer** that fuses them into one file.

---

## 1. Shape of the system

```
corpus/                    raw Pepper&Carrot pages + per-episode translator SVGs (23 episodes)
   │
   ├─ extract.py           SVG → scenes with per-language balloon geometry
   ├─ annotate.py          tokenise, lemmatise, filter junk, attach frequency
   ├─ etym.py              EtymDB 2.1 → real origin + cognate/borrowing edges
   ├─ pseudo.py            pseudoword foils for the baseline tests (ships in clavis-pipeline.zip;
   │                       pseudo.json is cached, so it is not in pipeline/)
   │       ↓
   │   scenes.json  lexicon.json  etym.json  pseudo.json
   │       ↓
   └─ pack.py              gzip+base64 the data, WebP the pages, inline G6,
                           fill template.html's placeholders
                                   ↓
                            clavis.html   (~11 MB, single file, no network)
```

**Design constraint that produced this shape:** the app must run offline, from a file, with no server and no account. Everything — 156 page images, ~14,000 lemmas, the etymology graph, and a 1.38 MB graph library — is inlined. The cost is an 11 MB file. The benefit is that it works on a plane, in a browser, forever, with no infrastructure to maintain or shut down.

---

## 2. Source corpus

**Pepper&Carrot** (davidrevoy.com) — a webcomic published under CC BY with community translations into dozens of languages, distributed as layered SVGs where the dialogue lives as real text elements. That last property is what makes the whole project possible: the translations are *aligned by construction*, because each language's SVG is the same artwork with the balloons re-typeset.

- 23 episodes; 163 pages yielded extractable scenes, **156 pages** survive filtering into the build
- 852 raw scenes extracted → **649 kept** after junk/credits filtering
- Ten languages carried: the nine targets plus English as the bridge

**Licence caution.** `03-evidence.md` §12 records that Pepper&Carrot's licence is not documented anywhere in the research corpus, despite being the foundation of the build. Verify independently before any commercial step.

---

## 3. `extract.py` — per-language balloon geometry

The single most consequential piece of the pipeline, and the fix for the "blank balloons" problem.

Two source formats exist across the episodes and they need different treatment:

**Episodes 1–12 — Inkscape flowed text.** Dialogue sits in `<flowRoot><flowRegion><rect>`. The geometry is simply that rect, with SVG transforms accumulated down the tree.

**Episodes 13–23 — plain text.** Dialogue is `<text>` with `<tspan>` children and **no flowRegion at all**. There is no box to read, so it is reconstructed:

1. Collect every `<tspan>` line as `(x, y, font-size, text)`.
2. Read `text-anchor` (`middle`/`center`/`end`/`start`) to find the true left edge — a centred line's `x` is its centre, not its origin.
3. Estimate each line's width with a per-script character metric:
   - CJK: **0.98 em** per character
   - Arabic: **0.46 em**
   - Latin: **0.5 em**
4. Box width = widest line. Top = `y₀ − fontsize × 1.02`. Height = `(y₁ − y₀) + fontsize × 1.34`.
5. Normalise to 0–1 page coordinates.

**Result:** usable geometry went from **~45% → 90%**.

| | es | fr | it | de | nl | ja | ko | zh | ar | en |
|---|---|---|---|---|---|---|---|---|---|---|
| credible rects | 90% | 90% | 90% | 90% | 89% | 90% | 81% | 90% | 82% | 89% |

(The 90% figure is reproducible against `scenes.json` with the app's own `rectOK` gate. The ~45% "before" figure was measured during the rewrite and the pre-change output no longer survives — treat it as approximate.)

Each scene emits `rects: {iso: [x,y,w,h]}` plus `src: 'flow'|'text'`. The app consumes this as the compact `q` field and falls back to the shared `r` **only when a language has no box at all** — `rectFor` is `(sc.q && sc.q[l]) || sc.r`. A box that exists but fails `rectOK` gets no fallback: that balloon goes unlettered and receives a position marker instead. This is why Korean and Arabic, at ~81–82% credible, leave roughly one balloon in five unlettered rather than silently borrowing the English box.

This is what makes whole-page lettering in *any* of the nine languages possible, which in turn is what made the comic reader, the story mode and the tappable-gloss interaction viable.

---

## 4. `annotate.py` and the lexicon

Tokenises each line per language, lemmatises, filters credit/title scenes, and attaches frequency data. Rects and `src` are carried through with a `[0,1]` clamp.

Lemma counts after filtering:

| es | fr | it | nl | ja | ko | zh | ar | de | en |
|---|---|---|---|---|---|---|---|---|---|
| 1828 | 1863 | 1845 | 1857 | 2088 | 867 | 1424 | 605 | 1923 | 1832 |

Each lexicon entry is `[count, zipf, surface, reading]`. **Zipf is load-bearing**: it is the axis the baseline test sets a threshold on, and therefore the mechanism by which the app decides what to assume you already know (`05-information-architecture.md` §6).

Token format in the packed data is deliberately compact: `[surface, lemma|0, zipf, bridges|0, reading?]`, where `lemma: 0` means "same as lowercased surface" and `bridges` is up to two cross-language hints.

---

## 5. `etym.py` — the etymology graph

Detailed separately in `07-etymology.md`. In summary: EtymDB 2.1 (CC BY-SA 4.0) is matched against corpus lemmas with per-script normalisation, ancestors are walked up to depth 3, and two products are emitted — an **origin** string per lemma and **cognate/borrowing edges** between corpus lemmas in different languages. Hub ancestors with more than 40 descendants (Proto-Indo-European roots) are skipped, because they connect everything to everything and produce a hairball rather than a graph.

---

## 6. `pack.py` — the build

1. Load `scenes.json`, `lexicon.json`, `pseudo.json`, `etym.json`.
2. Compact each scene: `{i, e, p, m, r, q, x, k}`; compact each token.
3. `json → gzip(level 9) → base64` into `__DATA_B64__`.
4. Resize every page to **640 px wide**, encode **WebP quality 55, method 6**, emit as data URIs into `__IMAGES__`, with dimensions into `__SIZES__`.
5. Inline `vendor-g6.min.js` (1,383,347 bytes) into `__G6_JS__`.
6. Write `clavis.html`.

Output: **~11,077 KB**. Run it with:

```bash
python3 /home/claude/pipeline/pack.py
```

Editing only `template.html` needs only this step — the JSON intermediates are cached.

---

## 7. The application: `template.html`

2,368 lines. Vanilla JS, no framework, no build step, no bundler. Everything is a function that returns DOM.

### 7.1 Layout

```
topbar        wordmark · view sub-title · gear
#view         the entire body of the app, re-rendered wholesale
nav.bar       the five-destination dock (hidden in modal states)
.toast        transient messages
canvas.morphveil   full-viewport, pointer-events:none, the transition layer
```

### 7.2 Rendering model

`render()` clears `#view` and rebuilds it. There is no virtual DOM and no diffing — the app is small enough that wholesale rebuild is cheaper than reconciliation, and it removes an entire class of stale-state bug. Long-lived canvas animations register a stop function (`orbStop`, `cosmosStop`) which `render()` calls before tearing down.

The `el(tag, attrs, ...children)` helper is the only abstraction: `class`, `html`, `on*` handlers and plain attributes, with automatic text-node coercion.

### 7.3 Scheduling — FSRS-5

A faithful FSRS-5 implementation with the published default 19-parameter weight vector, `DECAY = −0.5`, `FACTOR = 19/81`.

```
retr(days, S)   = (1 + FACTOR·days/S)^DECAY        retrievability
ivlDays(S, r)   = S/FACTOR · (r^(1/DECAY) − 1)     interval for a target retention
initS(g), initD(g)                                  first-review stability/difficulty
recallS / forgetS / shortS                          stability updates by outcome
nextD(d, g)                                         difficulty update with mean reversion
```

Cards are per `(language, lemma)`. A failed card returns in 10 minutes; a passed card gets `ivlDays(stability, S.settings.retention)` days. Chosen because FSRS beats SM-2 in ~99.6% of collections across a ~350 M-review benchmark (ref `[12]`) — and because it is a few floating-point operations per review needing no server, which is what makes offline-first honest rather than a compromise.

### 7.4 Graph rendering — AntV G6 v5.1.1

Vendored as a UMD bundle (`window.G6`), MIT licensed, obtained with `npm pack @antv/g6`. Used after the builder judged hand-rolled equivalents "performative at best" and instructed direct use of the library.

- **Plugins:** `fisheye` (Sarkar–Brown, `trigger:'drag'` so tap-to-place works on touch, reveals labels via a `nodeStyle` callback), `edge-filter-lens` (the Relations tool), `bubble-sets` (real BubbleSets contours, replacing a marching-squares approximation), `history`.
- **Layout:** `d3-force` for the Paths map, with `link` / `manyBody` / `collide`.
- **Behaviours:** `zoom-canvas`, `drag-canvas`, `drag-element`, `drag-element-force`, `auto-adapt-label`.

**Two integration gotchas, both load-bearing:**

1. `auto-adapt-label` fights the lens plugins' own label overrides and keeps labels hidden inside the lens. It must be disabled while a lens is active:
   ```js
   graph.updateBehavior({ key: 'adapt', enable: S.cosmos.exlens === 'off' });
   ```
2. BubbleSets sometimes does not paint on the first render after `setPlugins`, because it listens for `AFTER_RENDER`. Nudge it:
   ```js
   graph.draw().then(() => { if (S.cosmos.exsets && G6.GraphEvent) graph.emit(G6.GraphEvent.AFTER_RENDER, {}); });
   ```

All G6 colours are read from CSS custom properties at render time via a `cssVar()` helper, never hardcoded, so the graph re-themes with light/dark mode.

### 7.5 The Field view

A hand-written canvas renderer, kept deliberately rather than replaced by G6. Projects the word cloud in pseudo-3D: family sets the angle, relation-to-family the radius, familiarity the height. Includes a camera, a terrain function with gentle undulation, an entry wave (grey and flat → colour sweeping outward, raising and brightening), dust, and a focus mode for a single word's neighbourhood.

It exists because Explore answers *"show me the structure"* and Field answers *"show me how much there is"* — and those want different renderers.

### 7.6 Whole-page lettering

```js
rectFor(sc, l)   → (sc.q && sc.q[l]) || sc.r        per-language box, with fallback
rectOK(r)        → 0.045 ≤ w ≤ 0.70 && 0.010 ≤ h ≤ 0.45     credibility gate
pageOf(sc, lang) → sibling scenes on the same page with text in that language
letterPage(box, cur, lang, opts)                     the renderer
bPlate(r)        → blurred translucent plate behind every lettered balloon
lineOf(l, str)   → strips CJK line-break spaces (display and TTS)
```

`letterPage` positions each balloon as a percentage box, auto-fits font size against box area and text length, and re-fits on image load and on resize via `ResizeObserver`.

**`lineOf` explained.** The translators' SVGs break Japanese and Chinese lines with literal spaces — invisible in the original tight tspan layout, glaring when re-laid-out at a different width. A regex strips spaces *between two CJK characters only*, leaving Latin text and punctuation spacing untouched. Applied to display, glosses and speech synthesis alike, so TTS does not pause mid-word either.

**The cloze mask contract.** `opts.mask` is three-valued and this matters:

| Value | Meaning |
|---|---|
| `null` | Not a cloze, or already revealed — show the real text |
| `''` | This line cannot be safely gapped — **omit lettering entirely** rather than leak the answer |
| a string | Show this gapped text |

The mask is computed **once** in `renderEncounter` and passed to both the cropped and the full-page renderer. An earlier build wired it only into the full-page path, so the default cropped view — the first thing shown — displayed the answer to the question below it. On answering, a DOM sweep restores `dataset.full` on every `.btext.maskedline`, so balloon and caption fill in together.

### 7.7 The morph transition

A full-viewport canvas that animates between the three body views. `morphBegin()` captures the from-shape at the top of `render()`; `morphEnd()` captures the to-shape after the new view has rendered and runs the animation.

`morphShape(view)` returns `{cx, cy, rx, ry}` derived from the live DOM: a held drop for Learn (`.orbwrap`), a long horizon ellipse for Cosmos (`.cosmos-wrap`), a thin spine for Paths (`.tree`). `MORPH_MS = 520`.

The body is **not a solid shape** — it is up to six soft radial-gradient drops in the active lanes' colours, at golden-angle spacing (`i × 2.39996`) so they never align. A `gather` term shrinks the whole body to near-nothing at the midpoint and blooms it into the target:

```js
const gather = 1 - 0.45 * Math.sin(q * Math.PI);
```

This is the third rewrite. A particle system read as confetti; a metaball/goo-filter version read as an ink smear; a single morphing body read as "a black blob sliding across the screen." All three were rejected by the builder as "comical and kiddish." The version that survived is the one that matches the pool aesthetic structurally — the same drops, re-settling.

**Clamp the interpolation.** `const t = clamp((now - t0) / MORPH_MS, 0, 1)`. Unclamped, a backwards clock jump (a resumed background tab, a stale `t0`) produces a negative `t`, an out-of-range ease, an inverted shape, and `createRadialGradient` throwing *"r0 provided is less than 0."* Found only by deterministic-clock testing (§10), but reachable in real use.

---

## 8. Design system (v0.10)

The brief, verbatim from the builder, against a Louis Vuitton bag-configurator reference and two grainy gradient posters: *"the entire interface is low contrast, similar colours for the foreground and background… the ground is a low contrast colour that fades into the background. Subtle, unassuming. Light mode and dark mode."*

**Token architecture.** Light palette on bare `:root`. Dark redefined **identically in two places** — `@media (prefers-color-scheme: dark) :root:not([data-theme="light"])` for the system preference, and `:root[data-theme="dark"]` for the explicit toggle — so both paths land on the same values and the toggle wins in both directions.

Key light tokens:

```
--paper #F4F4F2   --surface #FBFBFA   --surface-2 #EEEEEB
--ink #2A2C31     --ink-2 #6B6E75     --ink-3 #9C9EA5
--line #E6E6E2    --line-2 #EFEFEC    --accent #4A5578
--cosmos-ground #EAEAE6             --hud-bg rgba(251,251,250,.82)
--btn-primary #2A2C31               --btn-primary-ink #F7F7F5
--shadow 0 1px 2px rgba(38,40,46,.03), 0 12px 32px -20px rgba(38,40,46,.16)
```

Dark mirrors the structure rather than inverting the values: `--paper #15161A`, `--ink #DCDDE1`, `--accent #9AA5C8`, `--btn-primary #D7D8DC`.

**Structural moves that make it low-contrast rather than merely pale:**

- Grounds **fade** instead of being framed: `radial-gradient(ellipse 78% 64% at 50% 44%, var(--cosmos-ground) 30%, var(--paper) 100%)`. No borders around the visualisation areas at all.
- **Grain**, via an inline SVG `feTurbulence` (`fractalNoise`, `baseFrequency 0.85`, `numOctaves 2`) tiled at 220 px — itself masked with a matching radial gradient so the grain fades out at the edges too, rather than ending in a hard rectangle.
- HUD chrome became soft pills on `--hud-bg` instead of dark glass.
- Buttons are charcoal-on-porcelain (and the inverse in dark), token-driven, with no per-theme override blocks.
- The Learn pool became a soft-focus aura of lane colours rather than a saturated black orb.

**Touch.** 44 px minimum targets throughout; lens plugins use tap-to-place rather than pointer-follow; `.btext.tap::after` provides invisible expanded hit areas around small page-lettered text; `matchMedia('(pointer:coarse)')` gates coarse-pointer sizing. `prefers-reduced-motion` stops the pool and the field animations.

---

## 9. Accessibility and internationalisation

- `dir="rtl"` and `lang` attributes per rendered language; Arabic lays out correctly throughout.
- Readings (rōmaji, romanised Korean, pīnyīn, romanised Arabic) available as subscripts, toggleable in settings.
- Speech synthesis per language with locale-matched voice selection and graceful absence — `canSpeak(l)` gates the button rather than failing silently.
- `prefers-reduced-motion` honoured by every animation.
- Citations are keyboard-reachable (`role="link"`, `tabindex="0"`).

---

## 10. Testing

Playwright against headless Chromium. **Note the non-default module path:**

```bash
NODE_PATH=/home/claude/.npm-global/lib/node_modules node /tmp/<script>.js
```

Rebuild the local preview from the current build before each run:

```python
h = open('/home/claude/app/clavis.html', encoding='utf-8').read()
open('/tmp/clavis-preview.html','w', encoding='utf-8').write(
  '<!doctype html><html><head><meta charset="utf-8">'
  '<meta name="viewport" content="width=device-width,initial-scale=1"></head>'
  '<body style="margin:0">' + h + '</body></html>')
```

| Script | Covers |
|---|---|
| `clavis-boot-test.js` | Fresh install through real onboarding clicks, all five views, a full session, plus a second context simulating a v0.2→v2 localStorage migration. **Run before every publish.** |
| `v8b.js` | G6 suite — node/edge counts, fisheye place+drag, relations lens, families, search → card → web handoff, Paths branch tap → walk, touch tool sizing ≥44 px |
| `v9.js` | Page lettering counts (ja/es), encounter lettering + halo, gloss on tap |
| `v9t.js` | Touch context (Pixel 5) — balloon tap → caption highlight, touch gloss, tap-target sizes |
| `v9f.js` | Deterministic-clock frame sampling of the morph |
| `v10.js` | Light/dark screenshots of every view **plus the cloze-leak regression** |

**The deterministic-clock technique** is worth keeping. Stub `performance.now` and `requestAnimationFrame` to read a fake `window.__clock` you advance yourself, then assert on specific frames. Normal wall-clock waits cannot sample the middle of a 520 ms animation reliably — and this technique is what surfaced the unclamped-interpolation bug in §7.7, which random real-time testing would probably never have hit.

**The cloze-leak check in `v10.js`** is the most important single regression: it seeds a due Spanish card with credible geometry, forces a cloze encounter, inspects the rendered `.btext` for the answer word, asserts no leak, answers, and confirms the gap is gone afterward. Re-run it for any change touching balloons, masking or scene rendering.

---

## 11. Build and release checklist

1. `python3 /home/claude/pipeline/pack.py`
2. Rebuild `/tmp/clavis-preview.html`
3. Run `clavis-boot-test.js`, `v8b.js`, `v9.js`, `v9t.js`, `v10.js` — require 0 errors and `LEAK: false`
4. Rebuild `clavis-standalone.html` (doctype/head wrapper around `clavis.html`)
5. Refresh `clavis-pipeline.zip` if any pipeline file, `template.html` or the G6 vendor bundle changed
6. Publish to the same artifact URL to preserve the link; label and note the version

---

## 12. Architectural decisions and their costs

| Decision | Why | What it costs |
|---|---|---|
| Single HTML file | Offline, no server, no account, no shutdown risk | 11 MB payload; no incremental updates |
| Vanilla JS, no build | Auditable by one person; no toolchain rot | No type safety; `template.html` is a 2,368-line file |
| Wholesale re-render | Eliminates stale-state bugs | Canvas lifecycles must be managed by hand |
| `localStorage` only | True privacy, zero infrastructure | No sync, no backup, one device |
| G6 vendored, not CDN | Offline requirement | 1.38 MB of the payload is the graph library |
| Field kept alongside Explore | Two different questions | Two renderers to maintain |
| FSRS-5 in-app | Best-benchmarked scheduler, runs offline | Parameters are the published defaults, not optimised on this user's reviews |
| CC0/CC BY/CC BY-SA only | Keeps a paid tier legally possible | Excluding NC sources costs real capability — no permissive offline TTS for Kannada or Konkani (`03-evidence.md` §11) |

---

## 13. Open technical work

- **FSRS parameter optimisation** on the user's own review log. The defaults are population priors; the whole point of an N=1 instrument is that it could fit its own.
- **Per-pair receptive expectations** (`04-personas.md`, P3) — MICReLa has the numbers, the app doesn't use them.
- **Instrument the hypotheses properly.** Cognate vs non-cognate cost, origin-surfaced vs not, and branch-prediction accuracy are all computable from existing state but are not surfaced anywhere (`05-information-architecture.md` §9).
- **Korean and Arabic geometry** sit at 81–82% credible against 90% elsewhere; the reconstruction metric for those scripts could be improved.
- **Route Letters from where it is needed** rather than leaving it stranded in the dock.
