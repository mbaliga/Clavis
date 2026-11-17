#!/usr/bin/env python3
"""Stage ubuntu-touch/www/index.html from the committed packs (standard library only).

Runs pipeline/pack_web.py (the same frozen ten-language build release.yml publishes as clavis-<v>-web.html),
takes its standalone wrapper, and removes exactly the three Google Fonts <link> tags from the page head so the
click makes no request at all (binding rule 1: no phoning home). That removal is the ONLY change; template.html
is untouched, and tools/tests assert the output equals the packed page minus those lines.

    python3 ubuntu-touch/tools/stage_www.py [--packs DIR] [--out ubuntu-touch/www/index.html]
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
PACKS = REPO / 'composeApp' / 'src' / 'commonMain' / 'composeResources' / 'files' / 'packs'
FONT_LINK = re.compile(
    r'<link\b[^>]*\bhref="https://fonts\.(?:googleapis|gstatic)\.com[^"]*"[^>]*>\n?')


def strip_font_links(html: str) -> tuple[str, int]:
    """Return (html without the Google Fonts <link> tags, number removed)."""
    return FONT_LINK.subn('', html)


def stage(packs: Path, out: Path) -> dict:
    with tempfile.TemporaryDirectory() as tmp:
        built = Path(tmp) / 'clavis.html'
        subprocess.run([sys.executable, str(REPO / 'pipeline' / 'pack_web.py'), '--packs', str(packs),
                        '--out', str(built)], check=True, stdout=subprocess.DEVNULL)
        html = built.with_name('clavis-standalone.html').read_text(encoding='utf-8')
    html, removed = strip_font_links(html)
    if removed != 3:
        raise SystemExit(f'expected to remove exactly 3 Google Fonts <link> tags, removed {removed}; '
                         'the template changed, review this tool')
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(html, encoding='utf-8')
    return {'bytes': out.stat().st_size, 'removed_links': removed}


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--packs', type=Path, default=PACKS)
    ap.add_argument('--out', type=Path, default=REPO / 'ubuntu-touch' / 'www' / 'index.html')
    a = ap.parse_args(argv)
    if not a.packs.is_dir():
        print(f'no packs directory: {a.packs}', file=sys.stderr)
        return 1
    print(stage(a.packs, a.out))
    return 0


if __name__ == '__main__':
    sys.exit(main())
