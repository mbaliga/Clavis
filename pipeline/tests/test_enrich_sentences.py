"""Tests for enrich_sentences: coverage gate, English linking, audio licence filtering, set cover,
attribution — on mini Tatoeba CSVs."""
import gzip
import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import enrich_sentences as es  # noqa: E402


def test_greedy_cover_covers_every_coverable_lemma_then_fills_by_rank():
    cands = [
        {"id": 1, "lemmas": ["a", "b"]},          # rank 0
        {"id": 2, "lemmas": ["a"]},               # redundant
        {"id": 3, "lemmas": ["c", "d", "e"]},     # biggest gain
        {"id": 4, "lemmas": ["f"]},
        {"id": 5, "lemmas": ["b"]},
    ]
    kept = es.greedy_cover(cands, cap=10)
    covered = set()
    for c in kept:
        covered.update(c["lemmas"])
    assert covered == {"a", "b", "c", "d", "e", "f"}
    assert [c["id"] for c in kept] == [1, 2, 3, 4, 5]          # rank order preserved, fill adds the rest
    kept3 = es.greedy_cover(cands, cap=3)
    assert [c["id"] for c in kept3] == [1, 3, 4]               # set cover first: 3 sentences cover all six lemmas
    # the stale-gain bug: a lower-bound entry must not stop the cover early
    cands2 = [{"id": i, "lemmas": ["x"]} for i in range(5)] + [{"id": 9, "lemmas": ["y"]}]
    assert {c["id"] for c in es.greedy_cover(cands2, cap=2)} == {0, 9}


def test_prefilter():
    assert es.prefilter("es", "Hola, ¿cómo estás?")
    assert not es.prefilter("es", "Hola")                      # one token
    assert not es.prefilter("es", " ".join(["a"] * 30))
    assert es.prefilter("ja", "私は学生です。")
    assert not es.prefilter("zh", "abc")


@pytest.fixture(scope="module")
def mini(tmp_path_factory):
    d = tmp_path_factory.mktemp("tatoeba")
    rows = [
        (1, "spa", "La poción de vuelo está lista."),          # all lexicon words
        (2, "spa", "La gata bebe la poción."),                 # gata not in lexicon → 5/6 = 83 %
        (3, "spa", "El astrofísico calibra el interferómetro."),  # far below 80 %
        (4, "spa", "Poción lista."),                           # too short
        (5, "spa", "La poción está lista."),                   # audio NC → excluded audio, still a candidate
        (6, "spa", "El gato bebe la poción de la bruja."),     # audio CC BY → ranked first
        (7, "spa", "La bruja está lista."),
        (10, "eng", "The flying potion is ready."),
        (11, "eng", "The cat drinks the potion."),
        (12, "eng", "The witch's cat drinks the potion."),
        (20, "deu", "Der Trank ist fertig."),
        (30, "fra", "La potion est prête."),
    ]
    (d / "sentences.csv").write_text("".join(f"{i}\t{l}\t{t}\n" for i, l, t in rows), encoding="utf-8")
    (d / "links.csv").write_text("1\t10\n10\t1\n2\t11\n11\t2\n6\t12\n12\t6\n1\t30\n30\t1\n")
    (d / "sentences_with_audio.csv").write_text(
        "5\t501\tsomeone\tCC BY-NC-ND 3.0\t\n6\t601\tspeaker\tCC BY 4.0\thttps://example.org/speaker\n7\t701\tnobody\t\t\n1\t101\tx\t\\N\t\n")
    (d / "sentences_detailed.csv").write_text("".join(f"{i}\t{l}\t{t}\tuser{i}\t\\N\t\\N\n" for i, l, t in rows), encoding="utf-8")
    lex = d / "lexicon.json"
    # simplemma lemmas: la → el, está → estar, lista → lista, bruja → brujo, gata → gato
    words = ["el", "poción", "de", "vuelo", "estar", "lista", "gato", "beber", "brujo", "yo"]
    lex.write_text(json.dumps({"es": {w: [1, 4.0, w, ""] for w in words}, "en": {"the": [1, 7, "the", ""]}}, ensure_ascii=False), encoding="utf-8")
    out = d / "build"
    cov = es.main(["--lexicon", str(lex), "--tatoeba-dir", str(d), "--out-dir", str(out), "--workers", "1", "--cap", "3"])
    with gzip.open(out / "packs" / "sentences" / "es.json.gz", "rt", encoding="utf-8") as f:
        sents = json.load(f)
    attr = json.loads((out / "packs" / "sentences" / "ATTRIBUTION-tatoeba.json").read_text(encoding="utf-8"))
    frag = json.loads((out / "packs" / "sentences" / "manifest-fragment.json").read_text(encoding="utf-8"))
    return cov, sents, attr, frag, out


def test_candidates_and_cap(mini):
    cov, sents, attr, frag, out = mini
    p = cov["per_language"]["es"]
    assert p["candidates"] == 5                     # 1, 2, 5, 6, 7 (3 fails coverage, 4 too short)
    assert p["kept"] == 3 and len(sents) == 3
    assert "en" not in cov["per_language"]          # bridge language skipped by default


def test_ranking_audio_first_and_licence_filter(mini):
    cov, sents, attr, frag, out = mini
    by_id = {s["id"]: s for s in sents}
    assert sents[0]["id"] == 6                       # permissive audio ranks first
    assert by_id[6]["audioUrl"] == "https://audio.tatoeba.org/sentences/spa/6.mp3"
    assert by_id[6]["audioLicence"] == "CC BY 4.0" and by_id[6]["audioBy"] == "speaker"
    assert all("audioUrl" not in s for s in sents if s["id"] != 6)   # NC / empty / \N never referenced
    assert cov["per_language"]["es"]["with_audio"] == 1


def test_english_and_lemmas_and_shape(mini):
    cov, sents, attr, frag, out = mini
    by_id = {s["id"]: s for s in sents}
    assert by_id[6]["en"] == "The witch's cat drinks the potion."
    assert by_id[6]["lang"] == "es" and set(by_id[6]["lemmas"]) >= {"gato", "beber", "poción", "brujo"}
    for s in sents:
        assert set(s) <= {"id", "lang", "text", "en", "lemmas", "audioUrl", "audioLicence", "audioBy"}
        assert isinstance(s["id"], int) and s["lemmas"]


def test_set_cover_reaches_every_coverable_lemma(mini):
    cov, sents, attr, frag, out = mini
    covered = set()
    for s in sents:
        covered.update(s["lemmas"])
    assert "vuelo" in covered                        # only sentence 1 has it; set cover keeps it despite the cap
    p = cov["per_language"]["es"]
    assert p["lemmas_covered"] == p["lemmas_in_any_candidate"]


def test_attribution_and_manifest(mini):
    cov, sents, attr, frag, out = mini
    a = attr["sentences"]["es"]["6"]
    assert a["by"] == "user6" and a["en"]["by"] == "user12" and a["audio"]["licence"] == "CC BY 4.0"
    assert attr["sentence_url_scheme"].format(sid=6).endswith("/6") and "CC BY 2.0 FR" in attr["text_licence"]
    assert frag["kind"] == "SENTENCES" and "CC BY 2.0 FR" in frag["licence"]
    assert frag["stats"]["es"]["sentences"] == 3 and "es.json.gz" in frag["files"]
