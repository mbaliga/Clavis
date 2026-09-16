"""Pipeline orchestrator: runs the stages in order with per-stage logs, timings and cached skipping.

    python run.py [--corpus DIR] [--languages all|core|es,fr,..] [--episodes 1-23] [--no-hygiene]
                  [--check golden.json] [--write-golden golden.json] [--stages extract,annotate,...]
                  [--force] [--version 2026.09.01] [--install] [--work DIR] [--packs DIR]

Stages (default: all, in this order)
    extract      SVGs -> <work>/scenes_raw.json (+ credits.json)
    annotate     -> scenes.json, lexicon.json, hygiene-report.json
    pseudo       -> pseudo.json
    pack_assets  -> <packs>/ (manifest.json, comics/, lexicon/, languages.json, ATTRIBUTION.md)
    pack_web     -> <build>/clavis.html + clavis-standalone.html (frozen nine-language web build)
    validate     -> <work>/validate.json (+ --check / --write-golden)
    smoke        Playwright boot test of clavis-standalone.html (needs node + playwright + Chromium)

A stage is skipped when its stamp (<work>/.stamps/<stage>.json) matches the current signature:
the stage script's hash, the corpus/language/episode/hygiene settings and its input files' mtimes.
--force reruns everything. Every stage's stdout+stderr is teed into <work>/logs/<stage>.log and the
wall-clock times land in <work>/timings.json.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

from config import PIPE, Config, add_common_args, log, read_json, write_json

STAGES = ['extract', 'annotate', 'pseudo', 'enrich_etymology', 'enrich_pronunciations', 'enrich_sentences',
          'pack_assets', 'pack_web', 'validate', 'smoke']
ENRICH = ['enrich_etymology', 'enrich_pronunciations', 'enrich_sentences']   # opt-in: --enrich (see ENRICHMENT.md)
PY = sys.executable
NODE_PATH_DEFAULT = '/opt/node22/lib/node_modules'
PW_BROWSERS_DEFAULT = '/opt/pw-browsers'


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()[:16]


def mtimes(paths) -> dict:
    return {str(p): (p.stat().st_mtime if p.exists() else None) for p in paths}


def tee_run(cmd: list[str], logfile: Path, env=None) -> int:
    logfile.parent.mkdir(parents=True, exist_ok=True)
    with open(logfile, 'w', encoding='utf-8') as lf:
        lf.write('$ ' + ' '.join(cmd) + '\n')
        p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, env=env, cwd=str(PIPE))
        for line in p.stdout:
            if 'Prefix dict' in line or 'Loading model' in line or 'Building prefix dict' in line:
                lf.write(line); continue   # jieba chatter: log only
            sys.stdout.write(line); sys.stdout.flush(); lf.write(line)
        return p.wait()


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    add_common_args(ap)
    ap.add_argument('--no-hygiene', action='store_true')
    ap.add_argument('--check', type=Path, default=None, help='golden JSON for validate --check')
    ap.add_argument('--write-golden', type=Path, default=None)
    ap.add_argument('--stages', default=','.join(STAGES))
    ap.add_argument('--force', action='store_true', help='ignore cached stamps')
    ap.add_argument('--version', default=None, help='pack version (default today)')
    ap.add_argument('--install', action='store_true', help='pack_assets --install after packing')
    ap.add_argument('--web-languages', default='core', help="languages for the web build (default 'core' = the frozen ten)")
    ap.add_argument('--enrich', action='store_true', help='also run the enrichment stages (enrich_*.py; need kaikki/EtymDB/Tatoeba data, see ENRICHMENT.md)')
    ap.add_argument('--network', action='store_true', help='let enrich_pronunciations query Wikimedia Commons (default: --offline, cache only)')
    ap.add_argument('--lingualibre', action='store_true', help='enrich_pronunciations: also query the Lingua Libre SPARQL endpoint for a second audio source (needs --network)')
    ap.add_argument('--commons-categories', action='store_true', help='enrich_pronunciations: also scan small Commons pronunciation categories for a second audio source (needs --network)')
    a = ap.parse_args(argv)
    cfg = Config.from_args(a)
    stages = [s.strip() for s in a.stages.split(',') if s.strip()]
    if not a.enrich and a.stages == ','.join(STAGES):
        stages = [s for s in stages if s not in ENRICH]
    unknown = set(stages) - set(STAGES)
    if unknown:
        raise SystemExit(f'unknown stages {sorted(unknown)}; choose from {STAGES}')
    cfg.work.mkdir(parents=True, exist_ok=True)
    stamps = cfg.work / '.stamps'; stamps.mkdir(exist_ok=True)
    logs = cfg.work / 'logs'
    common = ['--corpus', str(cfg.corpus), '--build', str(cfg.build), '--work', str(cfg.work), '--packs', str(cfg.packs),
              '--kaikki', str(cfg.kaikki), '--registry', str(cfg.registry_path), '--workers', str(cfg.workers)]
    lang_args = ['--languages', cfg.languages_spec]
    ep_args = ['--episodes', cfg.episodes_spec] if cfg.episodes_spec else []
    base_sig = {'corpus': str(cfg.corpus), 'languages': cfg.isos, 'episodes': cfg.episodes_spec, 'registry': sha(cfg.registry_path)}
    web_html = cfg.build / 'clavis.html'
    standalone = cfg.build / 'clavis-standalone.html'

    plan = {
        'extract': dict(cmd=[PY, 'extract.py', *common, *lang_args, *ep_args], inputs=[], outputs=[cfg.scenes_raw, cfg.credits]),
        'annotate': dict(cmd=[PY, 'annotate.py', *common, *lang_args, *ep_args] + (['--no-hygiene'] if a.no_hygiene else []),
                         inputs=[cfg.scenes_raw, PIPE / 'hygiene.py'], outputs=[cfg.scenes, cfg.lexicon, cfg.hygiene_report],
                         extra={'hygiene': not a.no_hygiene}),
        'pseudo': dict(cmd=[PY, 'pseudo.py', *common, *lang_args], inputs=[cfg.lexicon], outputs=[cfg.pseudo]),
        # enrichment stages (owned by ENRICHMENT.md): read the lexicon, write <build>/packs/<kind>/ + <build>/enrich/
        'enrich_etymology': dict(cmd=[PY, 'enrich_etymology.py', '--lexicon', str(cfg.lexicon), '--out-dir', str(cfg.build), '--kaikki-dir', str(cfg.kaikki)],
                                 inputs=[cfg.lexicon], outputs=[cfg.packs / 'etymology' / 'etym.json.gz']),
        'enrich_pronunciations': dict(cmd=[PY, 'enrich_pronunciations.py', '--lexicon', str(cfg.lexicon), '--out-dir', str(cfg.build), '--kaikki-dir', str(cfg.kaikki)]
                                      + ([] if a.network else ['--offline'])
                                      + (['--lingualibre'] if a.lingualibre else [])
                                      + (['--commons-categories'] if a.commons_categories else []),
                                      inputs=[cfg.lexicon], outputs=[cfg.packs / 'pronunciations' / 'manifest-fragment.json'],
                                      extra={'network': a.network, 'lingualibre': a.lingualibre, 'commons_categories': a.commons_categories}),
        'enrich_sentences': dict(cmd=[PY, 'enrich_sentences.py', '--lexicon', str(cfg.lexicon), '--out-dir', str(cfg.build)],
                                 inputs=[cfg.lexicon], outputs=[cfg.packs / 'sentences' / 'manifest-fragment.json']),
        'pack_assets': dict(cmd=[PY, 'pack_assets.py', *common, *lang_args] + (['--version', a.version] if a.version else []) + (['--install'] if a.install else []),
                            inputs=[cfg.scenes, cfg.lexicon, cfg.pseudo, cfg.credits, cfg.hygiene_report,
                                    *[cfg.packs / d / 'manifest-fragment.json' for d in ('etymology', 'sentences', 'pronunciations')]],
                            outputs=[cfg.packs / 'manifest.json', cfg.packs / 'comics' / 'scenes.json.gz'], extra={'install': a.install, 'version': a.version}),
        'pack_web': dict(cmd=[PY, 'pack_web.py', '--packs', str(cfg.packs), '--out', str(web_html), '--languages', a.web_languages, '--registry', str(cfg.registry_path)],
                         inputs=[cfg.packs / 'comics' / 'scenes.json.gz', cfg.packs / 'lexicon' / 'lexicon.json.gz', cfg.packs / 'lexicon' / 'pseudo.json',
                                 cfg.packs / 'etymology' / 'etym.json.gz'], outputs=[web_html, standalone], extra={'web_languages': a.web_languages}),
        'validate': dict(cmd=[PY, 'validate.py', *common, *lang_args] + (['--check', str(a.check)] if a.check else []) + (['--write-golden', str(a.write_golden)] if a.write_golden else []),
                         inputs=[], outputs=[], never_skip=True),
        'smoke': dict(cmd=['node', str(PIPE / 'web' / 'smoke.js'), str(standalone)], inputs=[standalone], outputs=[cfg.build / 'smoke-result.json'],
                      env={'NODE_PATH': os.environ.get('NODE_PATH', NODE_PATH_DEFAULT),
                           'PLAYWRIGHT_BROWSERS_PATH': os.environ.get('PLAYWRIGHT_BROWSERS_PATH', PW_BROWSERS_DEFAULT)}),
    }

    timings = read_json(cfg.work / 'timings.json') if (cfg.work / 'timings.json').exists() else {}
    log('run:', cfg.describe())
    failed = None
    for st in STAGES:
        if st not in stages:
            continue
        p = plan[st]
        script = PIPE / (p['cmd'][1] if p['cmd'][0] == PY else 'web/smoke.js')
        sig = {**base_sig, 'stage': st, 'script': sha(script) if script.exists() else None,
               'inputs': mtimes(p['inputs']), **p.get('extra', {})}
        stamp = stamps / f'{st}.json'
        cached = (not a.force and not p.get('never_skip') and stamp.exists() and read_json(stamp).get('sig') == sig
                  and all(o.exists() for o in p['outputs']))
        if cached:
            log(f'== {st}: cached (stamp matches), skipping')
            continue
        if st == 'smoke' and not shutil.which('node'):
            log('== smoke: node not found, skipping'); timings[st] = None; continue
        log(f'== {st}: start')
        t0 = time.time()
        env = None
        if p.get('env'):
            env = {**os.environ, **p['env']}
        rc = tee_run(p['cmd'], logs / f'{st}.log', env)
        dt = round(time.time() - t0, 1)
        timings[st] = dt
        write_json(cfg.work / 'timings.json', timings, indent=1)
        if rc != 0:
            log(f'== {st}: FAILED (exit {rc}) after {dt} s — see {logs / (st + ".log")}')
            failed = st
            if st in ('validate', 'smoke'):
                continue   # report, but still run what follows
            break
        write_json(stamp, {'sig': sig, 'seconds': dt, 'at': time.strftime('%Y-%m-%dT%H:%M:%S')}, indent=1)
        log(f'== {st}: done in {dt} s')
    log('run: timings', json.dumps(timings))
    if failed:
        sys.exit(1)


if __name__ == '__main__':
    main()
