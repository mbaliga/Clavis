# Personas

One real user, three derived ones, and an explicit list of people this is **not** for.

A caution before the list: personas 2–4 are *extrapolations from research segments*, not from interviews. No user research has been conducted for Clavis. They are written to make design trade-offs discussable, and every claim inside them is tagged with where it comes from. Persona 1 is the only one grounded in an actual person.

---

## P0 — The design stance that produces all of them

Clavis is built for people who find the *shape* of a language interesting. That is a real and small population. The app trades mass-market comprehensibility for density on purpose, and the personas below are all variants of "curious about structure," not "needs to order coffee in Lisbon."

The corollary: **any persona whose primary goal is fast functional competence in one language is better served by an existing app**, and the documentation should say so rather than pretending otherwise.

---

## P1 — Madhav (the N=1 case, real)

**Who.** Multidisciplinary designer and independent builder, Hyderabad. Native Hindi and Konkani; professional English; elementary Spanish and Japanese; limited working Urdu. Building a constellation of Android apps and tools full-time.

**Language situation.** Attempting Spanish, German, French, Italian, Dutch, Japanese, Korean, Chinese and Arabic simultaneously. Has lost touch with hiragana — script literacy cannot be assumed even where the language is nominally "elementary."

**What he wants from this.**
- Multiple languages kept *alive* rather than one language taken far.
- To understand how languages relate — borrowing, drift, families — as an end in itself, not as a mnemonic trick.
- Practice that does not require a scheduled block. His stated framing: bites small enough that language embeds itself rather than being studied.
- Culture, history and geography as part of the same object, not a separate tab.

**Constraints he brings.**
- Android and Linux first; macOS maybe; Windows last.
- Open source, and a licensing posture that keeps a paid tier possible.
- Expects academic rigour: in-app claims cited, with links to actual papers. This is why the colophon exists.
- Wants to be argued with. Explicitly asked for evidence-based debate rather than compliance — which is why `03-evidence.md` contradicts him in five places.

**What the design owes him specifically.**
- Script introductions with sounds and readings, because CJK and Arabic literacy is not assumed (this is the Letters view).
- A visible learning-progress signal, because he wants a finger on the pulse rather than a score.
- Onboarding, because the first prototype left him asking *where do I go, what am I learning* — and the jargon (fringe, coverage) was alien.
- Fluid, lively, low-contrast interface with a bottom dock; minimal and simple, with finesse in the interaction rather than ornament in the chrome.

**What the research says about him, uncomfortably.** He is exactly the configuration memo 01 calls risky: sibling languages at beginner level with productive goals. The lane split (one productive language per family) exists to keep him out of the failure mode his own instinct was heading toward. Whether it works is the open question of the whole project.

---

## P2 — The heritage speaker

*Extrapolated from memo 02 (Sarma 2025, IIT Bombay n = 1,375; Pune Marathi ethnography; Carreira & Kagan 2011 heritage pedagogy, n ≈ 1,700). Not interviewed.*

**Who.** Urban Indian professional, late twenties to thirties, English-medium educated. Declares a mother tongue on every form. Speaks it fluently at home and with elders. Cannot comfortably read it, has never written it since school, and has no vocabulary for their own working life in it.

**The profile, precisely.** Native phonology and listening. Everyday productive speech intact. Reduced productive vocabulary confined to home domains. Informal registers only. Frequently no literacy. This is the textbook heritage-speaker distribution, and the bimodal regional-language speaking ratings in the IIT Bombay data are its signature.

**Why they matter to this design.** They are the strongest case *for* the method, because they arrive with a real asset that every mainstream app discards — a whole phonology and a large receptive lexicon — and are then insulted by being placed in Unit 1 with *hello, my name is*.

**What the research says to do for them.**
- **Segment on day one.** A five-minute diagnostic separating heritage speaker from true beginner. Clavis implements this as the per-language baseline test, which sets a frequency threshold instead of a level.
- **Macro-based, not level-based.** Start from whole authentic texts; teach script and register explicitly; expand vocabulary by domain. **Avoid beginner curricula that bore heritage learners into quitting.**
- **Domain packs, not level packs.**

**Where the design currently fails them.** Clavis's nine languages are European and East Asian. The heritage persona's language is Marathi, Telugu, Kannada or Konkani — none of which are in the build, and for several of which memo 05 records that the content simply does not exist yet (Konkani: 1,252 Wiktionary entries, "essentially a seed list") and no offline permissive TTS voice exists at all. **This persona is currently aspirational.** Saying so is more useful than pretending the corpus covers them.

---

## P3 — The intercomprehension reader

*Extrapolated from memo 01 (MICReLa, Gooskens et al. 2017, 1,833 listeners; Marx 2012) and memo 04's market read. Not interviewed.*

**Who.** Someone who already has one Romance or Germanic language to a decent standard and wants to *read* the others without committing three years each. The EuroComRom audience, which currently has a book and a GitHub repo with 26 stars and no product.

**What they want.** To open an Italian page and get most of it because they know Spanish. To know which of their existing languages will help and which will trip them. Explicitly *not* to speak five languages.

**Why they are the strategically interesting persona.** Memo 04's recommended repositioning for the whole product is aimed at them: **"learn one, *understand* its siblings, to widen the funnel."** Multi-language learners are a minority everywhere; intercomprehension readers are a larger and more reachable group, and reception is the half of the method the evidence actually supports.

**What the design already gives them.** The receptive lanes (French, Italian, German, Japanese, Korean) are exactly this feature. Whole-page lettering in any of the nine languages means an Italian page can be read with Spanish knowledge and checked against English on tap. The etymology graph shows *why* a guess worked.

**What it does not give them yet.** Measured per-pair expectations. MICReLa provides real numbers (Spanish→Portuguese listening 37.2% but reading much higher; Czech↔Slovak ~93%), and the honest version of this feature would tell the user which pairs are cheap and which are not, per skill, instead of implying uniform transfer.

---

## P4 — The structure-curious generalist

*Composite. The person the app's tone is written for.*

**Who.** Reads about etymology for pleasure. Has a browser tab open on Wiktionary more often than is reasonable. Might be a linguist, might be a programmer, might be neither. Has probably bounced off Duolingo — not because it was hard but because it was *boring in a specific way*: it never explained anything, and progress was measured in a currency they didn't want.

**What they want.** The map. Why `libro`, `livre` and `book` diverged. Why Japanese has three scripts. Whether Arabic really shares nothing with the rest — and if so, what that says about the ones that do.

**What they will not tolerate.** Points, leagues, mascots, streak guilt, a cartoon owl expressing disappointment. Memo 04 lists all of these under "already commoditised — do not compete on," and memo 06 gives the evidential reason (expected tangible rewards, d = −0.36 on intrinsic motivation).

**How they use it.** Non-linearly. They will open Cosmos before Learn, follow a word's web for twenty minutes, and never finish a session. The app has to be *worth wandering in* even when nothing is due — which is why the pool sits still when there is nothing overdue and says so, rather than manufacturing urgency.

---

## Who this is not for

Stated as a design constraint, not an apology.

- **Someone who needs functional Spanish in six weeks for a trip.** Use Babbel or Pimsleur. Clavis optimises for breadth and structure and will be slower on any single language.
- **Someone who wants to be told what to do next and to be right about it.** The Paths view offers a fan of branches with honest reasons and no guarantee that the ordering is optimal — because the evidence that any adaptive branching beats a fixed syllabus does not exist (`02-hypothesis.md`, H8).
- **Someone who needs speaking practice or pronunciation scoring.** There is no ASR. Memo 05 is explicit that offline phoneme-level scoring is not solvable with permissive models for several target languages, and the project will not promise it.
- **A child, or a classroom.** No safeguarding model, no teacher view, no age-appropriate content review of the corpus.
- **Someone who wants their data synced across devices.** No account, no server, no sync. State lives in `localStorage` on one device. This is a deliberate trade and it costs something real.

---

## What would change these personas

The honest next step is that **P2 and P3 have never been shown the app.** The three cheapest things that would replace extrapolation with evidence:

1. Put the build in front of five heritage speakers of any language and watch where the baseline test lands them. If it puts a fluent-but-illiterate speaker in the beginner band, the diagnostic is wrong.
2. Give an intercomprehension reader an Italian page with Spanish knowledge and no Italian, and measure what they actually get. MICReLa's figure for that direction is **45.7%** (Spanish listeners hearing Italian) — the often-quoted 65.7% is the reverse direction, Italian listeners hearing Spanish, and the asymmetry is the point. The corpus can test this directly.
3. Ask P4 to explain, unprompted, what the Cosmos view is showing. If they can't, the visualisation is decorative — which is the exact criticism the builder already levelled at the first graph tools.
