"""Tests for enrich_pronunciations: IPA choice, Commons licence filtering (offline, seeded cache),
output shape and report."""
import gzip
import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import enrich_pronunciations as ep  # noqa: E402
from test_enrich_etymology import BY_LANG, MINI_LEX, SYNTH  # noqa: E402


def test_pick_ipa_prefers_untagged_broad_transcription():
    entries = [{"so": [{"ipa": "[ˈhɛlp]", "tags": ["US"]}, {"ipa": "/hɛlp/"}, {"ipa": "/hɛlp/", "tags": ["UK"]}]}]
    assert ep.pick_ipa(entries) == "/hɛlp/"
    assert ep.pick_ipa([{"so": [{"audio": "x.ogg"}]}]) is None


def test_audio_candidates_dedupe_and_prefer_standard():
    entries = [{"so": [{"audio": "En-us-key.ogg", "tags": ["US"]}, {"audio": "En-key.ogg"}, {"audio": "En-us-key.ogg", "tags": ["US"]}]}]
    c = ep.audio_candidates(entries, 5)
    assert [x["file"] for x in c] == ["En-key.ogg", "En-us-key.ogg"]


@pytest.mark.parametrize("rec,expected", [
    ({"LicenseShortName": "CC BY-SA 4.0", "Artist": "<a href='x'>Jeuwre</a>"}, ("CC BY-SA 4.0", "Jeuwre", "ok")),
    ({"LicenseShortName": "CC0", "Credit": "own work"}, ("CC0 1.0", "own work", "ok")),
    ({"LicenseShortName": "Public domain"}, ("Public domain", None, "ok")),
    ({"LicenseShortName": "CC BY-NC-SA 3.0", "Artist": "x"}, (None, "x", "excluded:CC BY-NC-SA 3.0")),
    ({"LicenseShortName": "GFDL"}, (None, None, "excluded:GFDL")),
    ({"missing": True}, (None, None, "missing")),
    ({"unverified": True}, (None, None, "unverified")),
    (None, (None, None, "missing")),
])
def test_licence_of(rec, expected):
    assert ep.licence_of(rec) == expected


def test_commons_title_normalisation():
    assert ep.commons_title("De-Schl%C3%BCssel.ogg") == "File:De-Schl%C3%BCssel.ogg"
    assert ep.commons_title("LL-Q9186_(zho)-x-電話.wav") == "File:LL-Q9186 (zho)-x-電話.wav"


@pytest.fixture(scope="module")
def run(tmp_path_factory):
    d = tmp_path_factory.mktemp("pron")
    kaikki = d / "kaikki"
    kaikki.mkdir()
    by = {}
    for e in SYNTH:
        by.setdefault(BY_LANG[e["l"]], []).append(e)
    for name, entries in by.items():
        with gzip.open(kaikki / f"{name}.jsonl.gz", "wt", encoding="utf-8") as f:
            for e in entries:
                f.write(json.dumps(e, ensure_ascii=False) + "\n")
    lex = d / "lexicon.json"
    lex.write_text(json.dumps(MINI_LEX, ensure_ascii=False), encoding="utf-8")
    out = d / "build"
    (out / "enrich").mkdir(parents=True)
    # seeded Commons cache: one permissive, one NC, one missing; zh file not cached → unverified offline
    (out / "enrich" / "commons_cache.json").write_text(json.dumps({
        "File:De-Schlüssel.ogg": {"LicenseShortName": "CC BY-SA 4.0", "Artist": "Jeuwre", "AttributionRequired": "true"},
        "File:It-chiave.ogg": {"LicenseShortName": "CC BY-NC-SA 3.0", "Artist": "someone"},
        "File:En-us-key.ogg": {"missing": True},
    }, ensure_ascii=False), encoding="utf-8")
    cov = ep.main(["--lexicon", str(lex), "--kaikki-dir", str(kaikki), "--out-dir", str(out), "--offline", "--workers", "1"])
    packs = {}
    for l in ("de", "it", "en", "zh", "es", "ko"):
        with gzip.open(out / "packs" / "pronunciations" / f"{l}.json.gz", "rt", encoding="utf-8") as f:
            packs[l] = json.load(f)
    frag = json.loads((out / "packs" / "pronunciations" / "manifest-fragment.json").read_text(encoding="utf-8"))
    return cov, packs, frag


def test_permissive_audio_referenced_with_licence_and_author(run):
    cov, packs, frag = run
    r = packs["de"]["schlüssel"]
    assert r["lemma"] == "schlüssel" and r["ipa"] == "/ˈʃlʏsl̩/"
    assert r["audioUrl"].endswith(".mp3") and r["audioLicence"] == "CC BY-SA 4.0" and r["audioBy"] == "Jeuwre"


def test_nc_missing_and_unverified_audio_excluded(run):
    cov, packs, frag = run
    assert "audioUrl" not in packs["it"]["chiave"] and packs["it"]["chiave"]["ipa"] == "/ˈkja.ve/"
    assert "audioUrl" not in packs["en"]["key"]
    assert "audioUrl" not in packs["zh"]["单位"]
    assert cov["per_language"]["it"]["audio_excluded"] == 1
    assert cov["excluded_reasons"].get("excluded:CC BY-NC-SA 3.0") == 1
    assert cov["excluded_reasons"].get("unverified") == 1
    assert cov["commons_api"]["requests"] == 0 and cov["commons_api"]["offline"]


def test_ipa_only_records_and_shape(run):
    cov, packs, frag = run
    assert packs["es"]["llave"] == {"lemma": "llave", "ipa": "/ˈʝabe/"}
    assert packs["ko"]["마셨"]["ipa"] == "[ma̠ɕida̠]"           # matched through the inflected form
    for l in packs:
        for lemma, r in packs[l].items():
            assert set(r) <= {"lemma", "ipa", "audioUrl", "audioLicence", "audioBy", "audioFile"}
            assert r["lemma"] == lemma and ("ipa" in r or "audioUrl" in r)


def test_report_and_manifest(run):
    cov, packs, frag = run
    assert cov["per_language"]["de"] == {"lemmas": 2, "kaikki_matched": 2, "records": 1, "ipa": 1, "audio_ok": 1, "audio_excluded": 0,
                                         "pct_ipa": 50.0, "pct_audio": 50.0, "audio_by_source": {"kaikki": 1}}
    assert frag["kind"] == "PRONUNCIATIONS" and frag["stats"]["de"]["audio"] == 1
    assert any("Commons" in a["name"] for a in frag["attribution"])


# ---------------------------------------------------------------- second audio source (offline, no network)

@pytest.mark.parametrize("lang,filename,expected", [
    ("es", "Es-amor.ogg", "amor"),
    ("es", "Es-am-lat-actividad.ogg", "actividad"),
    ("es", "(spa)-Saviourofthe-a posteriori.flac", "a posteriori"),
    ("es", "LL-Q1321 (spa)-JOAN-Calígine.wav", "calígine"),
    ("it", "It-Signorina.oga", "signorina"),
    ("ja", "LL-Q5287 (jpn)-葵心-虹.wav", "虹"),
    ("ko", "(kor)-Speaker-그리고.ogg", "그리고"),
    ("es", "Zz-nope.ogg", None),        # wrong language prefix
    ("es", "not-a-pronunciation-file.png", None),  # not an audio extension -> no pattern matches
])
def test_category_word_parses_every_filename_convention(lang, filename, expected):
    assert ep.category_word(lang, filename) == expected


def test_category_word_normalises_nfc_and_case():
    import unicodedata
    nfd_name = "Es-" + unicodedata.normalize("NFD", "adiós") + ".oga"
    assert ep.category_word("es", nfd_name) == "adiós"
    assert unicodedata.is_normalized("NFC", ep.category_word("es", nfd_name))


def test_lexicon_word_index_covers_lemma_and_surface():
    idx = ep.lexicon_word_index({"episodio": [1, 1, "Episodio", ""], "y": [1, 1, "y", ""]})
    assert idx == {"episodio": "episodio", "y": "y"}


def test_esc_sparql_string_escapes_quotes_and_backslashes():
    assert ep.esc_sparql_string('a"b\\c') == '"a\\"b\\\\c"'


def test_group_lingualibre_rows_dedupes_and_filters_to_lexicon():
    rows = [
        {"file": "http://commons.wikimedia.org/wiki/Special:FilePath/LL-Q386%20%28spa%29-A-casa.wav", "transcription": "casa"},
        {"file": "http://commons.wikimedia.org/wiki/Special:FilePath/LL-Q386%20%28spa%29-B-casa.wav", "transcription": "casa"},
        {"file": "http://commons.wikimedia.org/wiki/Special:FilePath/LL-Q386%20%28spa%29-A-casa.wav", "transcription": "casa"},  # dup
        {"file": "http://commons.wikimedia.org/wiki/Special:FilePath/LL-Q386%20%28spa%29-A-perro.wav", "transcription": "perro"},
        {"file": "http://commons.wikimedia.org/wiki/Special:FilePath/x-fuera.wav", "transcription": "fuera"},  # not in lexicon
    ]
    out = ep.group_lingualibre_rows(rows, {"casa": [1, 1, "casa", ""], "perro": [1, 1, "perro", ""]})
    assert out == {"casa": ["LL-Q386 (spa)-A-casa.wav", "LL-Q386 (spa)-B-casa.wav"], "perro": ["LL-Q386 (spa)-A-perro.wav"]}


def test_add_extra_candidates_appends_after_kaikki_deduped_and_capped():
    cands = {"es": {"llave": {"ipa": "/x/", "audio": [{"file": "Es-llave.ogg", "ogg": "u", "mp3": None, "tags": [], "penalty": 0, "source": "kaikki"}]}}}
    added = ep.add_extra_candidates(cands, "es", {"llave": ["LL-a-llave.wav", "LL-b-llave.wav"], "nueva": ["LL-c-nueva.wav"]},
                                    "lingualibre", cap=1, max_total=2)
    assert added == 2
    # kaikki candidate stays first; only one Lingua Libre candidate appended (cap=1) even though two were offered
    assert [a["file"] for a in cands["es"]["llave"]["audio"]] == ["Es-llave.ogg", "LL-a-llave.wav"]
    assert cands["es"]["llave"]["audio"][1]["source"] == "lingualibre"
    # a lemma kaikki never matched gets a fresh entry
    assert cands["es"]["nueva"] == {"ipa": None, "audio": [{"file": "LL-c-nueva.wav", "ogg": None, "mp3": None, "tags": [], "penalty": 0, "source": "lingualibre"}]}
    # re-running with an overlapping file is a no-op for that file (dedup) and respects max_total
    added2 = ep.add_extra_candidates(cands, "es", {"llave": ["LL-a-llave.wav", "LL-b-llave.wav"]}, "category", cap=1, max_total=2)
    assert added2 == 0 and len(cands["es"]["llave"]["audio"]) == 2


def test_audio_url_for_prefers_transcode_then_falls_back_to_commons_url():
    class FakeCommons:
        cache = {"File:LL-a-casa.wav": {"url": "https://upload.wikimedia.org/x/LL-a-casa.wav"}}
    assert ep.audio_url_for({"file": "x.ogg", "mp3": "m.mp3", "ogg": "o.ogg"}, FakeCommons()) == "m.mp3"
    assert ep.audio_url_for({"file": "x.ogg", "mp3": None, "ogg": "o.ogg"}, FakeCommons()) == "o.ogg"
    assert ep.audio_url_for({"file": "LL-a-casa.wav", "mp3": None, "ogg": None}, FakeCommons()) == "https://upload.wikimedia.org/x/LL-a-casa.wav"
    assert ep.audio_url_for({"file": "missing.wav", "mp3": None, "ogg": None}, FakeCommons()) is None


def test_langs_from_iso_and_label_rows():
    iso_rows = [{"lang": "https://lingualibre.org/entity/Q386", "iso": "spa"}, {"lang": "https://lingualibre.org/entity/Qbad", "iso": "zzz"}]
    assert ep._langs_from_iso_rows(iso_rows, {"spa": "es", "ita": "it"}) == {"es": "Q386"}
    label_rows = [{"lang": "https://lingualibre.org/entity/Q385", "label": "Italian"}]
    assert ep._langs_from_label_rows(label_rows, {"Italian": "it", "Polish": "pl"}) == {"it": "Q385"}


def test_lingualibre_records_for_offline_returns_nothing_without_network():
    ll = ep.LinguaLibre(interval=0, offline=True)
    rows, failed = ll.records_for("Q386", ["casa", "perro"])
    assert rows == [] and failed == 0 and ll.requests == 0
