#!/usr/bin/env bash
# Render the committed manifest into a LOCAL build manifest that consumes a tarball from disk instead of a Release URL.
#   render-manifest.sh <tarball> <version> <out.yml>
# The output is written next to the source manifest's directory so the relative `path:` sources resolve; the tarball
# is referenced by absolute path. The committed manifest is never modified.
set -euo pipefail
tar_path="$(readlink -f "$1")"; version="$2"; out="$3"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[ -f "$tar_path" ] || { echo "no such tarball: $tar_path" >&2; exit 1; }
sha="$(sha256sum "$tar_path" | cut -d' ' -f1)"
python3 - "$here/io.github.mbaliga.Clavis.yml" "$tar_path" "$sha" "$version" "$out" <<'PY'
import re, sys
src, tar, sha, ver, out = sys.argv[1:]
s = open(src).read()
s = re.sub(r'url: "https://[^"]*@VERSION@[^"]*"', 'path: ' + tar, s)
s = s.replace('@SHA256@', sha).replace('@VERSION@', ver)
assert '@' not in re.sub(r'#.*', '', s).replace('io.github', ''), 'unfilled placeholder'
open(out, 'w').write(s)
PY
echo "rendered $out (sha256 $sha)"
