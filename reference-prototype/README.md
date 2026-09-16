# Clavis prototype — corpus pipeline

Reproducible generator for the single-file prototype. Nothing here is the app's final architecture;
it is the "authoritative spec" build: one HTML page that native ports must reproduce.

Seeded from the original build's `clavis-pipeline.zip`. Paths in this README and in the scripts
below (`/home/claude/...`) are from that original environment, not this repo — adjust `ROOT` /
working directory when you actually run the pipeline. Neither the corpus (Pepper&Carrot SVGs,
EtymDB CSVs) nor any built `clavis.html` output shipped with the seed data: this directory holds
the *generator*, not the generated artifacts. See [`../docs/10-build-plan.md`](../docs/10-build-plan.md)
for how this fits into building an actual prototype, native or otherwise.

## Inputs
- Pepper&Carrot translation repos (CC BY 4.0, David Revoy + translators). The 2017 GitHub snapshot
  `Deevad/peppercarrot_epNN_translation` (NN = 01..23) contains, per episode, `gfx_*ENNPMM.png`
  (text-free page art) and `<lang>/*.svg` text layers with stable element ids across languages.
  Current sources live at https://framagit.org/peppercarrot/webcomics (same layout; more languages).
- wordfreq (CC BY-SA data) for Zipf frequencies; simplemma / UniDic (fugashi) / jieba / mecab-ko for lemmas;
  OpenCC for Simplified→Traditional (ja↔zh character bridges).

## Steps
```
python -m venv venv && . venv/bin/activate
pip install "wordfreq[cjk]" simplemma fugashi unidic-lite mecab-python3 mecab-ko-dic opencc-python-reimplemented pillow
for i in $(seq -w 1 23); do git clone --depth 1 https://github.com/Deevad/peppercarrot_ep${i}_translation.git corpus/ep$i; done
python extract.py     # -> scenes_raw.json  (aligned bubbles + geometry)
python annotate.py    # -> scenes.json, lexicon.json (tokens, lemmas, Zipf, heuristic bridges)
python pseudo.py      # (inline in the session: builds pseudo.json for the yes/no tests)
python pack.py        # -> clavis.html (gzip+base64 corpus, WebP page art, template)
```
Paths are absolute to /home/claude in the scripts; adjust ROOT.

## Licences
App code: yours. Corpus pack: CC BY 4.0 (Pepper&Carrot) + CC BY-SA 4.0 (wordfreq-derived frequencies) —
keep the pack separately licensed from the app, with ATTRIBUTION listing David Revoy and the translators.
