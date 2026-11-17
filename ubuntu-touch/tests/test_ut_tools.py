"""Tests for the Ubuntu Touch staging and layout tools. Run: python3 -m unittest discover -s ubuntu-touch/tests"""
import json
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

UT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(UT / 'tools'))
import check_click_layout as chk  # noqa: E402
import stage_www as sw  # noqa: E402


class StripTests(unittest.TestCase):
    def test_removes_only_font_links(self):
        head = ('<title>x</title>\n<link rel="preconnect" href="https://fonts.googleapis.com">\n'
                '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>\n'
                '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=A&display=swap">\n'
                '<link rel="stylesheet" href="local.css">\n<a href="https://doi.org/x">c</a>')
        out, n = sw.strip_font_links(head)
        self.assertEqual(n, 3)
        self.assertIn('local.css', out)
        self.assertIn('https://doi.org/x', out)
        self.assertNotIn('fonts.', out)


class StagedPageTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = Path(tempfile.mkdtemp())
        cls.out = cls.tmp / 'index.html'
        sw.stage(sw.PACKS, cls.out)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp)

    def test_only_difference_from_pack_web_is_the_three_links(self):
        with tempfile.TemporaryDirectory() as t:
            subprocess.run([sys.executable, str(sw.REPO / 'pipeline' / 'pack_web.py'), '--packs', str(sw.PACKS),
                            '--out', f'{t}/c.html'], check=True, stdout=subprocess.DEVNULL)
            orig = Path(t, 'c-standalone.html').read_text(encoding='utf-8')
        staged = self.out.read_text(encoding='utf-8')
        self.assertEqual(len(re.findall(r'^<link .*fonts\.g', orig, re.M)), 3)
        # the gzip header inside the base64 payload carries an mtime, so two packs differ in those 4 chars
        norm = lambda h: re.sub(r'H4sI[A-Za-z0-9+/]{4}', 'H4sI', h)
        self.assertEqual(norm(staged), norm(sw.FONT_LINK.sub('', orig)))
        self.assertEqual(len(orig.splitlines()) - len(staged.splitlines()), 3)

    def test_page_makes_no_request_of_its_own(self):
        html = self.out.read_text(encoding='utf-8')
        self.assertNotIn('fonts.googleapis', html)
        self.assertNotRegex(html, r'\.src\s*=\s*[\'"]https?:')
        # The only XMLHttpRequest mentions are G6's feature detection (`new XMLHttpRequest).responseType` and
        # `withCredentials`); nothing opens a request, fetches, beacons or opens a socket.
        self.assertEqual(len(re.findall(r'\bfetch\(|sendBeacon|new WebSocket|new EventSource', html)), 0)
        self.assertEqual(len(re.findall(r'XMLHttpRequest', html)), 2)
        self.assertNotRegex(html, r'\.open\(\s*[\'"](GET|POST)')


class LayoutTests(unittest.TestCase):
    def make_stage(self, tmp, policy=('webview',), arch='all', page=None):
        (tmp / 'qml').mkdir()
        (tmp / 'assets').mkdir()
        (tmp / 'www').mkdir()
        (tmp / 'qml/Main.qml').write_text('x')
        (tmp / 'assets/icon.png').write_bytes(b'p')
        (tmp / 'www/index.html').write_text(page or ('<!doctype html>' + 'a' * 1_100_000))
        (tmp / 'manifest.json').write_text(json.dumps({
            'name': 'a.b', 'version': '1', 'framework': 'f', 'architecture': arch, 'maintainer': 'm',
            'title': 'Clavis (reference build)', 'description': 'd',
            'hooks': {'clavis': {'apparmor': 'c.apparmor', 'desktop': 'c.desktop'}}}))
        (tmp / 'c.apparmor').write_text(json.dumps({'policy_groups': list(policy), 'policy_version': 16.04}))
        (tmp / 'c.desktop').write_text('[Desktop Entry]\nExec=qmlscene $@ qml/Main.qml\nIcon=assets/icon.png\n')

    def test_good(self):
        with tempfile.TemporaryDirectory() as t:
            self.make_stage(Path(t))
            self.assertEqual(chk.check(Path(t)), [])

    def test_networking_rejected(self):
        with tempfile.TemporaryDirectory() as t:
            self.make_stage(Path(t), policy=('webview', 'networking'))
            self.assertTrue(any('policy_groups' in e for e in chk.check(Path(t))))

    def test_fonts_link_rejected(self):
        with tempfile.TemporaryDirectory() as t:
            self.make_stage(Path(t), page='<!doctype html>fonts.googleapis.com' + 'a' * 1_100_000)
            self.assertTrue(any('Google Fonts' in e for e in chk.check(Path(t))))

    def test_arch_rejected(self):
        with tempfile.TemporaryDirectory() as t:
            self.make_stage(Path(t), arch='arm64')
            self.assertTrue(any('architecture' in e for e in chk.check(Path(t))))


class SourceFilesTests(unittest.TestCase):
    def test_checked_in_policy_and_manifest(self):
        self.assertEqual(json.loads((UT / 'clavis.apparmor').read_text())['policy_groups'], ['webview'])
        m = (UT / 'manifest.json.in').read_text()
        self.assertIn('"architecture": "all"', m)
        self.assertIn('reference build', m)


if __name__ == '__main__':
    unittest.main()
