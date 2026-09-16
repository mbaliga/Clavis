"""Lexicon hygiene (D-7): attestation of lemmas against wordfreq and the compacted Wiktionary
(kaikki) extracts.

Rule (applied by annotate.py unless --no-hygiene):
    a lemma is DROPPED when  zipf == 0  (wordfreq has never seen lemma nor surface)
                        AND neither the lemma nor its first surface is a headword or an
                            inflected form in the kaikki extract for that language.
    CJK languages (script CJK/HANGUL) are exempt — wordfreq reports no zeros there, and the
    rule would only remove real words.  Languages without a kaikki file are exempt too
    (nothing to attest against); annotate reports them as "skipped".

The kaikki files are big (up to 100 MB gzip for English) so the set of attested word forms is
cached once per language as a sorted, gzipped word list under <build>/cache/kaikki-words/.
The cache is keyed by the source file's size+mtime, so a refreshed extract rebuilds it.

Can also be run stand-alone to (re)build the caches:
    python hygiene.py --kaikki DIR --cache DIR Spanish French ...
"""
from __future__ import annotations

import argparse
import gzip
import json
import os
import re
import sys
import unicodedata
from pathlib import Path
from typing import Iterable

AR_DIAC = re.compile('[\u0610-\u061A\u064B-\u0652\u0670\u0640\u06D6-\u06ED]')
AR_FORMAT = re.compile('[\u200B-\u200F\u202A-\u202E\u2060\uFEFF]')


def ar_norm(w: str) -> str:
    """Same normalisation as annotate.tok_ar, plus removal of invisible format characters."""
    w = AR_FORMAT.sub('', w)
    w = AR_DIAC.sub('', w)
    return re.sub('[إأآٱ]', 'ا', w)


CACHE_VERSION = 'v2'   # bump when norm_word changes so stale word caches are rebuilt
LENGTH_MARKS = re.compile('[̄̆]')   # combining macron / breve: dictionary vowel-length marks (Latin)


def norm_word(script: str, w: str) -> str:
    """Normalise a word for attestation lookup. `script` is the registry Script enum value.
    Latin-script words lose dictionary vowel-length marks (Latin headwords in Wiktionary carry macrons,
    the comic does not) and the dotted capital I folds to plain i (Turkish)."""
    w = w.strip()
    if script == 'ARABIC':
        return ar_norm(w)
    if script in ('CJK', 'HANGUL'):
        return unicodedata.normalize('NFKC', w)
    w = unicodedata.normalize('NFKC', w).replace('İ', 'i').lower()
    w = LENGTH_MARKS.sub('', unicodedata.normalize('NFD', w))
    return unicodedata.normalize('NFC', w)


def kaikki_path(kaikki_dir: Path, kaikki_name: str | None) -> Path | None:
    if not kaikki_name:
        return None
    p = kaikki_dir / f'{kaikki_name}.jsonl.gz'
    return p if p.exists() else None


def _cache_file(cache_dir: Path, src: Path) -> Path:
    st = src.stat()
    return cache_dir / f'{src.name[:-len(".jsonl.gz")]}.{st.st_size}.{int(st.st_mtime)}.{CACHE_VERSION}.words.gz'


def build_word_cache(src: Path, cache_dir: Path, script: str, log=print) -> Path:
    """Stream one compacted kaikki file and write the set of attested forms (headwords + forms)."""
    cache_dir.mkdir(parents=True, exist_ok=True)
    dst = _cache_file(cache_dir, src)
    if dst.exists():
        return dst
    words: set[str] = set()
    n = 0
    with gzip.open(src, 'rt', encoding='utf-8') as f:
        for line in f:
            n += 1
            try:
                e = json.loads(line)
            except json.JSONDecodeError:
                continue
            w = e.get('w')
            if w:
                words.add(norm_word(script, w))
            for fo in e.get('f') or ():
                fw = fo.get('f') if isinstance(fo, dict) else None
                if fw:
                    words.add(norm_word(script, fw))
    tmp = dst.with_suffix('.tmp')
    with gzip.open(tmp, 'wt', encoding='utf-8', compresslevel=6) as out:
        for w in sorted(words):
            out.write(w + '\n')
    os.replace(tmp, dst)
    log(f'kaikki cache: {src.name}: {n} entries -> {len(words)} attested forms -> {dst.name}')
    return dst


def load_word_set(src: Path, cache_dir: Path, script: str, log=print) -> set[str]:
    cache = build_word_cache(src, cache_dir, script, log)
    with gzip.open(cache, 'rt', encoding='utf-8') as f:
        return {line.rstrip('\n') for line in f}


class Attestor:
    """Lazy per-language attestation sets."""

    def __init__(self, kaikki_dir: Path, cache_dir: Path, log=print):
        self.kaikki_dir = Path(kaikki_dir)
        self.cache_dir = Path(cache_dir)
        self.log = log
        self._sets: dict[str, set[str] | None] = {}

    def available(self, kaikki_name: str | None) -> bool:
        return kaikki_path(self.kaikki_dir, kaikki_name) is not None

    def words(self, kaikki_name: str | None, script: str) -> set[str] | None:
        if kaikki_name not in self._sets:
            p = kaikki_path(self.kaikki_dir, kaikki_name)
            self._sets[kaikki_name] = load_word_set(p, self.cache_dir, script, self.log) if p else None
        return self._sets[kaikki_name]

    def attested(self, kaikki_name: str | None, script: str, forms: Iterable[str]) -> bool | None:
        """True/False, or None when no extract exists for the language."""
        ws = self.words(kaikki_name, script)
        if ws is None:
            return None
        return any(norm_word(script, f) in ws for f in forms if f)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--kaikki', required=True, type=Path)
    ap.add_argument('--cache', required=True, type=Path)
    ap.add_argument('names', nargs='+', help='kaikki language names, e.g. Spanish')
    a = ap.parse_args(argv)
    for name in a.names:
        src = kaikki_path(a.kaikki, name)
        if not src:
            print('missing', name, file=sys.stderr)
            continue
        script = 'ARABIC' if name == 'Arabic' else 'LATIN'
        build_word_cache(src, a.cache, script)


if __name__ == '__main__':
    main()
