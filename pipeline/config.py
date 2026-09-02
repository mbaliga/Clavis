"""Shared configuration for the Clavis corpus pipeline: paths, the language registry, corpus layout.

Every path can be set by CLI flag, by environment variable, or falls back to a repo-relative default:

    flag          env var          default
    --corpus      CLAVIS_CORPUS    <repo>/corpus              (framagit sparse clone or 2017 snapshot)
    --build       CLAVIS_BUILD     <repo>/pipeline/build
    --work        CLAVIS_WORK      <build>/work               (stage intermediates + logs)
    --packs       CLAVIS_PACKS     <build>/packs              (pack_assets output)
    --kaikki      CLAVIS_KAIKKI    <repo>/data/kaikki         (compacted Wiktionary extracts)
    --registry    CLAVIS_REGISTRY  <repo>/pipeline/languages.json

Two corpus layouts are auto-detected per episode directory:
    framagit:  epNN_<Title>/lang/<code>/ENNPMM.svg   +  epNN_<Title>/lang/gfx_*_ENNPMM.png  + lang/<code>/info.json
    snapshot:  epNN/<code>/*ENNPMM.svg               +  epNN/gfx_*_ENNPMM.png
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
PIPE = Path(__file__).resolve().parent
CORE_LANGS = ['es', 'fr', 'it', 'de', 'nl', 'ja', 'ko', 'zh', 'ar', 'en']   # the frozen web build's ten
BRIDGE = 'en'
MAX_WORKERS = 2   # shared 4-CPU box (brief)


# ----------------------------------------------------------------------------- logging

def log(*parts, file=sys.stdout):
    print(time.strftime('%H:%M:%S'), *parts, file=file, flush=True)


def warn(*parts):
    log('WARN', *parts, file=sys.stderr)


def read_json(path: Path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)


def write_json(path: Path, obj, indent=None):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + '.tmp')
    with open(tmp, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=indent, separators=None if indent else (',', ':'))
    os.replace(tmp, path)
    return path


# ----------------------------------------------------------------------------- registry

@dataclass
class Language:
    iso: str
    pcFolder: str
    name: str
    nativeName: str
    family: str
    script: str
    rtl: bool
    defaultLane: str
    siblings: list[str]
    readingSystem: str | None
    ttsLocale: str
    colour: str
    spaceSeparated: bool
    enabledByDefault: bool
    needsScriptPrimer: bool
    role: str = 'target'
    enabled: bool = False
    tokenizer: str = 'whitespace'
    lemmatizer: str = 'none'
    caseFolding: str | None = None      # "turkic" -> İ/I fold to i/ı before lower-casing
    wordfreqLang: str | None = None
    freqSource: str | None = None
    kaikkiName: str | None = None
    etymdbCode: str | None = None
    tatoebaCode: str | None = None
    episodes: int = 0
    notes: str = ''
    raw: dict = field(default_factory=dict, repr=False)

    @property
    def is_cjk(self) -> bool:
        return self.script in ('CJK', 'HANGUL')

    @property
    def is_bridge(self) -> bool:
        return self.role == 'bridge'


class Registry:
    def __init__(self, path: Path):
        self.path = Path(path)
        data = read_json(self.path)
        self.doc = data
        self.languages: list[Language] = []
        known = {f.name for f in Language.__dataclass_fields__.values()} - {'raw'}
        for d in data['languages']:
            kw = {k: v for k, v in d.items() if k in known}
            self.languages.append(Language(raw=d, **kw))
        self.by_iso = {l.iso: l for l in self.languages}
        self.by_folder = {l.pcFolder: l for l in self.languages}

    def __getitem__(self, iso: str) -> Language:
        return self.by_iso[iso]

    def get(self, iso: str) -> Language | None:
        return self.by_iso.get(iso)

    def enabled(self) -> list[Language]:
        return [l for l in self.languages if l.enabled]

    def select(self, spec: str | None) -> list[Language]:
        """Resolve --languages: 'all'/'enabled' (default), 'core' (the frozen ten), or a comma list of ISO
        codes. The bridge language is always included. Order is registry order."""
        if not spec or spec in ('all', 'enabled'):
            want = {l.iso for l in self.enabled()}
        elif spec == 'core':
            want = set(CORE_LANGS)
        else:
            want = {s.strip() for s in spec.split(',') if s.strip()}
            unknown = want - set(self.by_iso)
            if unknown:
                raise SystemExit(f'unknown language codes in --languages: {sorted(unknown)}')
        want.add(BRIDGE)
        return [l for l in self.languages if l.iso in want]

    def script_of(self, iso: str) -> str:
        l = self.by_iso.get(iso)
        return l.script if l else 'OTHER'

    def to_pack_json(self) -> dict:
        """The registry as shipped in the pack: identical document (the app ignores pipeline-only keys)."""
        return self.doc


# ----------------------------------------------------------------------------- corpus layout

@dataclass
class Episode:
    num: int
    dir: Path          # episode directory
    lang_root: Path    # directory holding the per-language folders and the gfx_*.png pages
    layout: str        # 'framagit' | 'snapshot'
    pages: list[tuple[str, Path]]   # (page code 'E01P03', png path)

    def svg_for(self, folder: str, page: str) -> Path | None:
        hits = sorted(glob.glob(str(self.lang_root / folder / f'*{page}.svg')))
        return Path(hits[0]) if hits else None

    def info_json(self, folder: str) -> Path | None:
        p = self.lang_root / folder / 'info.json'
        return p if p.exists() else None

    def has_folder(self, folder: str) -> bool:
        return (self.lang_root / folder).is_dir()


def parse_episodes(spec: str | None) -> set[int] | None:
    """'1-23,25' -> {1..23, 25}; None -> None (all)."""
    if not spec:
        return None
    out: set[int] = set()
    for part in spec.split(','):
        part = part.strip()
        if not part:
            continue
        if '-' in part:
            a, b = part.split('-', 1)
            out.update(range(int(a), int(b) + 1))
        else:
            out.add(int(part))
    return out


def episodes(corpus: Path, only: set[int] | None = None) -> list[Episode]:
    corpus = Path(corpus)
    out = []
    for d in sorted(corpus.iterdir()):
        m = re.match(r'ep(\d\d)', d.name)
        if not m or not d.is_dir():
            continue
        num = int(m.group(1))
        if only is not None and num not in only:
            continue
        lang_root = d / 'lang' if (d / 'lang').is_dir() else d
        layout = 'framagit' if lang_root != d else 'snapshot'
        pages = []
        for png in sorted(glob.glob(str(lang_root / 'gfx_*P[0-9][0-9].png'))):
            pm = re.search(r'(E\d+P\d+)', png)
            if pm:
                pages.append((pm.group(1), Path(png)))
        out.append(Episode(num, d, lang_root, layout, pages))
    return out


# ----------------------------------------------------------------------------- CLI plumbing

def add_common_args(ap: argparse.ArgumentParser, *, languages=True, episodes=True):
    env = os.environ.get
    ap.add_argument('--corpus', type=Path, default=Path(env('CLAVIS_CORPUS', REPO / 'corpus')))
    ap.add_argument('--build', type=Path, default=Path(env('CLAVIS_BUILD', PIPE / 'build')))
    ap.add_argument('--work', type=Path, default=Path(env('CLAVIS_WORK')) if env('CLAVIS_WORK') else None,
                    help='stage intermediates dir (default <build>/work)')
    ap.add_argument('--packs', type=Path, default=Path(env('CLAVIS_PACKS')) if env('CLAVIS_PACKS') else None,
                    help='pack output dir (default <build>/packs)')
    ap.add_argument('--kaikki', type=Path, default=Path(env('CLAVIS_KAIKKI', REPO / 'data' / 'kaikki')))
    ap.add_argument('--registry', type=Path, default=Path(env('CLAVIS_REGISTRY', PIPE / 'languages.json')))
    ap.add_argument('--workers', type=int, default=int(env('CLAVIS_WORKERS', MAX_WORKERS)))
    if languages:
        ap.add_argument('--languages', default='all', help="'all' (every enabled language), 'core' (the frozen ten) or a comma list of ISO codes")
    if episodes:
        ap.add_argument('--episodes', default=None, help="episode filter, e.g. '1-23' or '1,2,5'")


@dataclass
class Config:
    corpus: Path
    build: Path
    work: Path
    packs: Path
    kaikki: Path
    registry_path: Path
    workers: int
    languages_spec: str
    episodes_spec: str | None
    registry: Registry = field(init=False, repr=False)
    languages: list[Language] = field(init=False, repr=False)

    def __post_init__(self):
        self.registry = Registry(self.registry_path)
        self.languages = self.registry.select(self.languages_spec)
        self.workers = max(1, min(MAX_WORKERS, self.workers))

    @classmethod
    def from_args(cls, a) -> 'Config':
        build = Path(a.build)
        return cls(corpus=Path(a.corpus), build=build, work=Path(a.work) if a.work else build / 'work',
                   packs=Path(a.packs) if a.packs else build / 'packs', kaikki=Path(a.kaikki),
                   registry_path=Path(a.registry), workers=a.workers,
                   languages_spec=getattr(a, 'languages', 'all'), episodes_spec=getattr(a, 'episodes', None))

    @property
    def isos(self) -> list[str]:
        return [l.iso for l in self.languages]

    @property
    def episode_filter(self) -> set[int] | None:
        return parse_episodes(self.episodes_spec)

    def episodes(self) -> list[Episode]:
        eps = episodes(self.corpus, self.episode_filter)
        if not eps:
            raise SystemExit(f'no episode directories (epNN*) under {self.corpus}')
        return eps

    # intermediate files
    @property
    def scenes_raw(self): return self.work / 'scenes_raw.json'
    @property
    def credits(self): return self.work / 'credits.json'
    @property
    def scenes(self): return self.work / 'scenes.json'
    @property
    def lexicon(self): return self.work / 'lexicon.json'
    @property
    def pseudo(self): return self.work / 'pseudo.json'
    @property
    def hygiene_report(self): return self.work / 'hygiene-report.json'
    @property
    def cache(self): return self.build / 'cache'

    def describe(self) -> str:
        return (f'corpus={self.corpus} work={self.work} packs={self.packs} languages={self.isos} '
                f'episodes={self.episodes_spec or "all"} workers={self.workers}')
