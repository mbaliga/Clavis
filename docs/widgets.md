# Home-screen widgets (Android)

Six standard Android App Widgets (`AppWidgetProvider` + `appwidget-provider` XML + `RemoteViews`), so
any launcher can host them: Pixel Launcher, One UI, Nova, Orrery Home. They are built from the
learner's real data, are resizable from 2×1 upward with four layouts each, and follow the app's own
rules: no network, no new permission, no telemetry, no streaks or urgency (D-13, D-15, D-21), the
D-18 copy rules, and the quiet palette (D-11). Nothing here has been seen on a device yet: this
environment has no phone or emulator. Everything a device must confirm is listed at the end.

## How the data gets there

The learner document has one owner, `ClavisStore` on the UI thread, and the coverage, cloze lines
and etymology the widgets show need the 20 MB packs, which a widget cannot load inside a broadcast.
So the widgets never read `clavis-state.json`. Instead:

1. **Publish.** When the activity stops (`MainActivity.onStop`, right after the learner document is
   flushed) and at least one Clavis widget is placed, `ClavisWidgets.publish` builds a
   `WidgetSnapshot` from the live engine (`WidgetSnapshots.build`, `:shared`) and writes it
   atomically to `noBackupFilesDir/widgets/glance.json` (kilobytes, at most tens of kilobytes: no
   more than 600 due times per lane), then sends every placed widget an update. With no widget
   placed nothing is written; removing the last widget deletes the file (`onDisabled` →
   `ClavisWidgets.forget`), so a newly placed widget shows its first-run text until the app is next
   left.
2. **Draw.** Each provider reads that file on one background thread inside `goAsync()`, turns it
   into faces (`WidgetFaces.render`, `:shared`, unit-tested) and binds them to RemoteViews
   (`WidgetViews`). On Android 12+ one `RemoteViews(Map<SizeF, RemoteViews>)` carries all four
   layouts and the launcher picks per size without waking the app; below 12 the provider picks from
   `OPTION_APPWIDGET_MIN_WIDTH` × `OPTION_APPWIDGET_MAX_HEIGHT` and redraws on
   `onAppWidgetOptionsChanged`.
3. **Time.** Each lane carries the times its cards fall due, so a widget redrawn later counts what
   is due *then*, without the app. One inexact, non-waking alarm (`AlarmManager.set`, RTC; no
   exact-alarm permission) redraws at the next moment a card falls due, and at local midnight when a
   Word origins widget is placed. Time, time-zone and locale changes redraw through the providers'
   own `onReceive`. `updatePeriodMillis` is 0.

Values that decay with memory (words held, bubbles readable) are as of the snapshot, and the widgets
that show them print that date and time; a date never goes wrong without a redraw.

**Why "Word due" is read-only (D-31).** The seed was a three-answer cloze answered on the home screen. That
needs an application-scoped owner of the store (today the activity owns it, together with the
importer and share sheet it needs), a booted engine inside a broadcast receiver, and a grading path
outside a session. It would also break two of the app's own rules: every encounter is anchored to a
comic page (`05-information-architecture.md` §4.2), and the cloze grade depends on the response time,
which a home screen cannot measure. So the widget shows the gapped line, and **Recall it** opens a
session held in that word's lane, which serves that same word first
(`ClavisEngine.startLaneSession`). Answering on the home screen waits for an application-scoped store.

## The widgets

Sizes are chosen by the size in dp the launcher reports, not by cells: `WidgetSize` puts SMALL and
MEDIUM at 112 dp tall and LARGE at 240 dp tall (100 and 240 dp wide). At those heights each
class's layouts hold, at 1× font scale, the parts that must stay (the label, an "as of" time, the
buttons) with pads at least 48 dp tall, and LARGE has room for its fullest card (about 235 dp).
On Android's reference handset ((73n − 16) × (118m − 16) dp in portrait, (142n − 15) × (66.5m − 15)
in landscape) that makes: **TINY** a one-row strip
(102 dp portrait, 51 landscape) or anything under two cells wide; **SMALL** 2×2 and 3×3 portrait;
**MEDIUM** 4×2 portrait (the default, 220 dp), 2×2 to 4×3 landscape (117–184 dp); **LARGE** 4×3
portrait (337 dp) and 4×4 landscape (250 dp). Other launchers' cells differ, so a given cell count
can land one class up or down; the layouts fit whichever class they get. Below a template's minimum
every widget collapses to one tappable summary line. When a widget is shorter than its content (a
small cell, a larger font), the list rows and the card's detail lines give way first; the "as of"
footer and the buttons keep their height (`WidgetStoreTest` checks the layouts for this).

| Widget | Template | TINY | SMALL | MEDIUM | LARGE | Tap |
|---|---|---|---|---|---|---|
| **Word due** | `card` (read-only `choice`) | "● Spanish · 3 ready" | the gapped line, "● 3 words ready to return" | label "Word due · Spanish", the gapped line, count, **Recall it** | adds "do you remember?", up to three other lanes with words ready, **Open the pool** | a session held in that lane |
| **The pool** | `stat` → `list` | "● 4 words ready to return" / "○ A still pool" | "● 4 ready", "across 2 languages" | headline and three lanes, each "● n ready" or "○ still" | caption, five lanes, "and n more languages" | Learn (the pool) |
| **Your record** | `stat` → `list` | "412 words held", "as of <date>" | 412, "words held · as of …" | three lanes "120 held · 34% readable", "as of …" | five lanes, what "readable" means, "as of …" | You |
| **Last dip** | `stat` → `card` | "4/5 understood or recalled" | "4/5", "understood or recalled · <date time>" | label with the date, new words met, languages visited, **Another** | adds the language names and **See the paths** | You; buttons start a session / open Paths |
| **Word origins** | `card` | "casa · Spanish" | the word, "grew from Latin casa" | label, the word with its reading ("猫 (neko)"), "grew from Latin casa · Wiktionary + EtymDB" beside **See its web** | the reading on its own line, up to three relations ("chez · French · kin"), "source: …" | Cosmos, focused on that word |
| **Your languages** | `pads` | "3 languages · ● 4 ready" | two pads | four pads | six pads; when lanes outnumber pads the last is **More…** | a session held in that lane |

### Word due
- **Shows** the card first in line across the active lanes — per lane the earliest-due reviewable
  card, exactly as `dueCards` orders them — as the line the cloze would most likely use, with the
  word gapped (`Mask.forCloze`, then `lineOf` for ja/zh). The word itself, its reading and the
  options are never shown. A line that cannot be gapped safely, or that would still contain the
  word, is not shown at all (the cloze integrity rule, one step stricter); the widget then names the
  lane and the count.
- **Empty:** "A still pool — nothing is overdue." with "the next word returns <date time>" and
  **Begin**, the pool's own words (IA §4.1).
- **Data:** `WidgetSnapshot.word`, `LaneGlance.dueNow`/`laterMin`.

### The pool
- **Shows** whether words are ready or the pool is still, as a shape and a word per language,
  most-ready first. No colour carries the state.
- **Data:** `LaneGlance` due counts, recomputed at draw time; "n+" when a lane's list of due times
  ran out (more than 600 future cards).

### Your record
- **Shows** words held (`knownCount`) and the share of each lane's bubbles readable at the 95%
  threshold (`corpusCoverage`), the numbers the You screen shows. LARGE adds the glossary's
  definition of readable ("a bubble with at least 95% of its words held or presumed known"), not the
  cited comprehension claim the app prints with [1]. This widget leaves off "days you showed up",
  recall percentages and encounter counts (D-15, D-30's open question about streaks in disguise).
- **Stale:** every size prints when its values were true: "as of <date>" on the one-line tile,
  "as of <date time>" from SMALL up, on a line that is never the one cut.

### Last dip
- **Shows** `state.lastSession`: understood or recalled out of the session size, new words met,
  languages visited, with the session's date and time. **Empty:** "No dip yet".
- **For the maintainer (D-30):** its headline, "4/5 understood or recalled", is a recall result on
  the home screen. It repeats the in-app session summary's own numbers and words; whether a past
  result on a public surface sits with D-15 is the maintainer's call. Dropping it would leave new
  words met and languages visited as the headline.

### Word origins
- **Shows** one word a day from up to 14 candidates: words the learner has met (a rated or
  provisional card) with a recorded origin; when there are none yet, the commonest words of the
  active lanes that have one, labelled "a common word". Origins come from the etymology pack only
  and carry their provenance (D-5, R-10); where the graph is silent the widget says so instead of
  showing anything. The reading appears only when Settings keeps readings on. Arabic and other
  target-language forms inside an English line are wrapped in Unicode isolates (`Copy.isolate`).
- **Refresh:** the word changes at local midnight (the alarm). A publish later the same day keeps
  the word already shown (`WidgetSnapshots.keepTodaysOrigin` pins it for that day) as long as it is
  still a candidate; only the first met word replacing the common words can move it.

### Your languages
- **Shows** one pad per active lane in registry order, each with its identity dot (decoration: the
  name is always printed), and "● n ready" or "○ still".
- **Tap:** a session held in that lane; a lane with nothing to offer releases the hold as any held
  lane does.

## States every widget can reach

| State | What it says | Tap |
|---|---|---|
| First run (never published) | "Open Clavis once to fill this in." | opens the app |
| Unreadable or newer schema | "Clavis could not read what this widget shows. Opening the app writes it again." | opens the app |
| Not onboarded | "Finish choosing your languages in Clavis." | opens the app |
| Every lane resting | "Every language is resting — turn one on in Languages" | Languages |
| Empty | the widget's own words: "A still pool — nothing is overdue.", "No dip yet", "No word in your languages has a recorded origin yet." | as the widget |
| Stale | "as of <date time>" wherever a value decays (Your record); due counts and dates stay true without a redraw | — |

## Taps

Every tap is an explicit, immutable `PendingIntent.getActivity` to `MainActivity` with action
`io.github.mbaliga.clavis.action.OPEN_FROM_WIDGET` (no intent filter, no URL scheme), string extras
that `WidgetTap.parse` validates (lane codes `[a-z]{2,8}`, a fixed set of views, a bounded lemma),
request codes unique per widget id, size and slot, and `NEW_TASK | CLEAR_TOP | SINGLE_TOP` so a
running app takes the tap in `onNewIntent` with its one store instead of stacking a second activity
with a second copy of the document. `ClavisStore.openFromWidget` lands it: it waits for boot, never
interrupts onboarding, a baseline check or a live session (the app just comes forward), and replaces
a finished session's summary. Taps replayed from Recents are ignored.

## Privacy

A home screen is a public surface. The widgets show language names, counts, a comic line from the
public Pepper&Carrot corpus with one word gapped, and an etymology from the CC BY-SA packs — never
imported texts (Media), file names, settings or anything typed by the learner. The snapshot holds
learner state, so it exists only while a widget is placed (removing the last one deletes it) and
lives under `noBackupFilesDir`: out of cloud backup and device-to-device
transfer (the learner document's own rules exclude cloud backup only; the new phone's app writes a
fresh snapshot the first time it runs). The snapshot is a projection; it is never read back into the
learner document, and the D-12 state schema is unchanged.

## Look

The palette is `ClavisTokens` (light and dark, `values/` and `values-night/widget_colors.xml`), not
Hyle's field (D-11). Colour never carries meaning: due is "●" plus "ready", still is "○" plus
"still", and TalkBack descriptions drop the shapes and read the words. Lane colours are identity dots
beside printed names. Root views are `@android:id/background` with the launcher's radius on
Android 12+ (16 dp below). Text is in sp (secondary 12 sp, body 14 sp), ellipsized with fixed line
counts. Buttons are at least 48 dp tall, and pads are at least 48 dp tall from the least height of
the size class that draws them (4 dp padding, 2 dp margins: two rows from 112 dp, three from 164 dp;
`WidgetStoreTest` checks the layouts against `WidgetSize`) (D-29). The bundled Hind/Rozha faces are
Compose resources RemoteViews cannot load, so the widgets use the system sans-serif.

## Code and tests

| What | Where |
|---|---|
| Snapshot, builder, JSON | `shared/src/commonMain/.../core/widget/WidgetSnapshot.kt` |
| Faces per widget and size, taps, sizes, redraw time | `shared/src/commonMain/.../core/widget/WidgetFaces.kt` |
| Widget words (beside `Copy`, under D-18) | `shared/src/commonMain/.../core/widget/WidgetCopy.kt` |
| Lane-held session | `ClavisEngine.startLaneSession`, `ClavisStore.startLaneSession` / `openFromWidget` |
| Publishing, reading, alarm | `composeApp/src/androidMain/.../widget/ClavisWidgets.kt` |
| Providers, alarm receiver | `composeApp/src/androidMain/.../widget/ClavisWidgetProvider.kt` |
| RemoteViews binding, tap intents | `composeApp/src/androidMain/.../widget/WidgetViews.kt` |
| Layouts, previews, provider info | `composeApp/src/androidMain/res/layout/widget_*.xml`, `res/xml/widget_*_info.xml` |

JVM tests: `WidgetSnapshotTest`, `WidgetFacesTest` and `LaneSessionTest` in `:shared` (desktop and
both Android unit-test variants: due counting identical to `dueCards`, the gapped line never showing
its word, origins never invented, every state at every size, taps validated, the redraw time, and the
D-18 forbidden list over every string a widget can draw or speak), and `WidgetStoreTest` in
`:composeApp:desktopTest` (the snapshot from the real packs, where each tap lands, never interrupting,
the copy rules over the widget string resources, and the layout XML against `WidgetSize`: pads of
at least 48 dp, and an "as of" footer and buttons that are never the weighted, first-cut views). The RemoteViews binding has no JVM test: the
repo has no Robolectric, and it is device-only behaviour.

## Owner-verify (device only)

- [ ] Each of the six widgets appears in the picker of Pixel Launcher, One UI, Nova and Orrery Home,
      with its preview (Android 12+) or preview image (Android 8–11).
- [ ] Placing each before opening the app shows the first-run text; after onboarding and leaving the
      app, they fill in.
- [ ] Resizing through 2×1, 4×1, 2×2, 4×2, 4×3 and taller switches layouts by height as "The
      widgets" describes (a one-row strip is the one-line tile), on Android 12+ and on an Android
      8–11 device. At every font scale the label, the "as of" line and the buttons stay and nothing
      overlaps; near a class's least height, and more so at 1.3× and 2×, a long comic line, the
      lowest list rows and the card's detail lines may be cut.
- [ ] Light and dark follow the system; the corners follow the launcher on Android 12+.
- [ ] **Recall it** opens the session on the word shown; a lane pad opens that lane; taps while the
      app is open reuse it (no second Clavis in Recents); a tap during a live session or a baseline
      check only brings the app forward.
- [ ] A card falling due with the app closed changes "A still pool" to the word within minutes of its
      due time (subject to Doze); Word origins changes after midnight.
- [ ] An Arabic word or line reads right to left inside the English chrome.
- [ ] TalkBack reads each widget, button and pad by its words, never "black circle"; the gap is read
      as "blank".
- [ ] Removing the last Clavis widget deletes `widgets/glance.json`; placing one again shows
      "Open Clavis once to fill this in." until Clavis is opened and left.
- [ ] OEM battery managers (One UI, MIUI, ColorOS) may defer or drop the redraw alarm: counts stay
      right whenever the launcher redraws, but the switch from "still" to "ready" can lag.
- [ ] After a reboot, widgets redraw (launchers send an update; no boot receiver is declared, since
      that would need a permission).
