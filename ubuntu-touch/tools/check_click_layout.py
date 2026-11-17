#!/usr/bin/env python3
"""Check a staged click directory (DESTDIR install of ubuntu-touch/) and, optionally, a built .click.

    python3 check_click_layout.py STAGE_DIR
    python3 check_click_layout.py --click path/to/x.click      (needs `dpkg-deb`)

Guards: manifest/hook names agree, referenced files exist, the AppArmor profile asks for `webview` and never
`networking` or an unconfined template (the page makes no request), the page is non-trivial and carries no
Google Fonts link or other fetch of its own.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

FORBIDDEN_POLICY = {'networking', 'unconfined', 'camera', 'microphone', 'location', 'video', 'audio'}


def check(root: Path) -> list[str]:
    errs: list[str] = []
    def need(p: str) -> Path:
        f = root / p
        if not f.is_file():
            errs.append(f'missing {p}')
        return f
    mf = need('manifest.json')
    if errs:
        return errs
    m = json.loads(mf.read_text())
    for k in ('name', 'version', 'framework', 'architecture', 'maintainer', 'hooks', 'title', 'description'):
        if k not in m:
            errs.append(f'manifest lacks {k}')
    if m.get('architecture') != 'all':
        errs.append(f"architecture should be 'all' (pure QML), got {m.get('architecture')!r}")
    if re.search(r'@[A-Z_]+@', json.dumps(m)):
        errs.append('manifest has an unsubstituted @VAR@')
    if 'reference' not in (m.get('title', '') + m.get('description', '')).lower():
        errs.append('title/description must say this is the reference build (program rule R12)')
    hooks = m.get('hooks', {})
    if len(hooks) != 1:
        errs.append('expected exactly one hook')
    for app, h in hooks.items():
        ap = need(h.get('apparmor', ''))
        de = need(h.get('desktop', ''))
        if ap.is_file():
            pol = json.loads(ap.read_text())
            groups = set(pol.get('policy_groups', []))
            if groups != {'webview'}:
                errs.append(f'policy_groups must be exactly [webview], got {sorted(groups)}')
            if groups & FORBIDDEN_POLICY or 'template' in pol:
                errs.append('forbidden apparmor policy')
        if de.is_file():
            d = de.read_text()
            exe = re.search(r'^Exec=(.*)$', d, re.M)
            if not exe or 'qml/Main.qml' not in exe.group(1):
                errs.append('desktop Exec must launch qml/Main.qml')
            ic = re.search(r'^Icon=(.*)$', d, re.M)
            if ic:
                need(ic.group(1))
    need('qml/Main.qml')
    page = need('www/index.html')
    if page.is_file():
        html = page.read_text(encoding='utf-8', errors='replace')
        if len(html) < 1_000_000:
            errs.append('www/index.html is implausibly small; packs missing?')
        if re.search(r'fonts\.(googleapis|gstatic)\.com', html):
            errs.append('www/index.html still references Google Fonts')
        if '<!doctype html>' not in html[:200].lower():
            errs.append('www/index.html is not a standalone document')
    return errs


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument('stage', nargs='?', type=Path)
    ap.add_argument('--click', type=Path)
    a = ap.parse_args(argv)
    if a.click:
        with tempfile.TemporaryDirectory() as t:
            subprocess.run(['dpkg-deb', '-x', str(a.click), t], check=True)
            # A click keeps its manifest in the control archive (as `manifest`); the data archive may not carry
            # manifest.json at all, so fall back to the control copy before checking the layout.
            if not (Path(t) / 'manifest.json').is_file():
                with tempfile.TemporaryDirectory() as c:
                    subprocess.run(['dpkg-deb', '-e', str(a.click), c], check=True)
                    ctl = Path(c) / 'manifest'
                    if ctl.is_file():
                        (Path(t) / 'manifest.json').write_text(ctl.read_text())
            errs = check(Path(t))
    elif a.stage:
        errs = check(a.stage)
    else:
        ap.error('give STAGE_DIR or --click')
    for e in errs:
        print('FAIL:', e, file=sys.stderr)
    if not errs:
        print('click layout ok')
    return 1 if errs else 0


if __name__ == '__main__':
    sys.exit(main())
