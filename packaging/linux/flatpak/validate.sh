#!/usr/bin/env bash
# Static checks for the Flatpak metadata and manifest; runs anywhere appstreamcli, desktop-file-validate and python3
# with PyYAML exist. Does not build a Flatpak.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
id=io.github.mbaliga.Clavis
desktop-file-validate "$here/$id.desktop"
# --no-net: the homepage/bugtracker URLs are not fetched (the lane must not depend on the network for linting).
appstreamcli validate --no-net "$here/$id.metainfo.xml"
python3 - "$here/$id.yml" <<'PY'
import sys, yaml
m = yaml.safe_load(open(sys.argv[1]))
assert m['app-id'] == 'io.github.mbaliga.Clavis'
args = set(m['finish-args'])
# The sandbox holds exactly these permissions; widening it is a visible, PRIVACY.md-documented choice.
allowed = {'--socket=x11', '--share=ipc', '--device=dri', '--socket=pulseaudio', '--share=network'}
assert args == allowed, f'finish-args drifted: {sorted(args ^ allowed)}'
assert not any(a.startswith('--filesystem') or a.startswith('--talk-name') or a.startswith('--own-name') for a in args)
print('manifest ok:', m['app-id'], sorted(args))
PY
