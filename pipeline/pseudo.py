"""Stage 3 — pseudoword foils for the yes/no baseline test.

Reads <work>/lexicon.json -> writes <work>/pseudo.json  {iso: [foil, ...]}

For every selected target language written in a Latin or Cyrillic script, splice two real corpus
lemmas (length >= 5, alphabetic, frequency > 0) into a candidate of 5-10 letters that is not in the
lexicon and unknown to wordfreq for both that language and English. Same algorithm and seed as the
reference pseudo.py, with the 4-field lexicon row fix ([count, zipf, surface, reading]) and a
per-language RNG so adding a language never changes another language's foils.
Languages without a wordfreq list (freqSource "corpus", e.g. Latin) skip the wordfreq check for
their own language; the English check still applies.
"""
from __future__ import annotations

import argparse
import json
import random
import time

import wordfreq

from config import Config, add_common_args, log, write_json


def pseudo(lex_lang: dict, lang: str, wordfreq_lang: str | None, n: int = 10, seed: int = 7) -> list[str]:
    rng = random.Random(seed)
    words = [w for w, (c, z, s, *_) in lex_lang.items() if len(w) >= 5 and z > 0 and w.isalpha()]
    out = set(); tries = 0
    if len(words) < 2:
        return []
    while len(out) < n and tries < 5000:
        tries += 1; a, b = rng.sample(words, 2)
        cand = a[:rng.randint(2, len(a) - 2)] + b[rng.randint(2, len(b) - 2):]
        if not (5 <= len(cand) <= 10) or cand in lex_lang:
            continue
        if wordfreq_lang and wordfreq.zipf_frequency(cand, wordfreq_lang) != 0:
            continue
        if wordfreq.zipf_frequency(cand, 'en') != 0:
            continue
        out.add(cand)
    return sorted(out)


def run(cfg: Config) -> dict:
    t0 = time.time()
    lex = json.load(open(cfg.lexicon, encoding='utf-8'))
    out = {}
    for L in cfg.languages:
        if L.role != 'target' or L.script not in ('LATIN', 'CYRILLIC') or L.iso not in lex:
            continue
        out[L.iso] = pseudo(lex[L.iso], L.iso, L.wordfreqLang)
    write_json(cfg.pseudo, out)
    counts = {l: len(v) for l, v in out.items()}
    log('pseudo:', counts, f'| {time.time() - t0:.1f} s')
    return {'pseudo': counts, 'seconds': round(time.time() - t0, 1)}


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    add_common_args(ap, episodes=False)
    run(Config.from_args(ap.parse_args(argv)))


if __name__ == '__main__':
    main()
