"""Tests for enrich_etymology: template parsing, origin selection, hub guard, edge cap, provenance,
and an end-to-end run on a 20-line synthetic kaikki extract plus a mini EtymDB."""
import gzip
import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import enrich_common as ec  # noqa: E402
import enrich_etymology as ee  # noqa: E402


def tpl(n, **a):
    return {"n": n, "a": {k.strip("_"): v for k, v in a.items()}, "x": a.get("x")}


# ---------------------------------------------------------------- template parsing
def test_hops_chain_in_text_order_and_types():
    e = {"w": "livre", "l": "fr", "p": "noun", "tp": [
        tpl("inh", _1="fr", _2="frm", _3="livre"), tpl("inh+", _1="fr", _2="frm", _3="livre"),
        tpl("inh", _1="fr", _2="fro", _3="livre"), tpl("inh", _1="fr", _2="la", _3="lībra"),
        tpl("cog", _1="es", _2="libra")]}
    chain, cogs = ee.hops_of_entry(e, "fr", {})
    assert [(h["lang"], h["term"], h["type"]) for h in chain] == [("frm", "livre", "inh"), ("fro", "livre", "inh"), ("la", "lībra", "inh")]
    assert cogs == [{"lang": "es", "term": "libra", "tpl": "cog"}]


def test_der_with_dash_takes_following_link_template_and_sinitic_der_is_borrowing():
    e = {"w": "学生", "l": "ja", "tp": [tpl("der", _1="ja", _2="ltc", _3="-"), tpl("ltc-l", _1="學生")]}
    chain, _ = ee.hops_of_entry(e, "ja", {})
    assert chain == [{"type": "bor", "lang": "ltc", "term": "學生", "tpl": "der", "dist": 1}]


def test_etymon_chain_split_and_qualifiers_removed():
    e = {"w": "cavalier", "l": "en", "tp": [tpl("etymon", _1="en", _2=":bor", _3="frm:cavalier<id:horseman>>it:cavaliere>la:caballārius")]}
    chain, _ = ee.hops_of_entry(e, "en", {})
    assert [(h["lang"], h["term"]) for h in chain] == [("frm", "cavalier"), ("it", "cavaliere"), ("la", "caballārius")]
    assert chain[0]["type"] == "bor"


def test_ko_etym_sino_joins_odd_arguments():
    e = {"w": "여자", "l": "ko", "tp": [tpl("ko-etym-Sino", _1="女", _2="female", _3="子", _4="offspring")]}
    chain, _ = ee.hops_of_entry(e, "ko", {})
    assert chain == [{"type": "bor", "lang": "zh", "term": "女子", "tpl": "ko-etym-Sino", "dist": 1}]
    e2 = {"w": "단위", "l": "ko", "tp": [tpl("ko-etym-sino", _1="單位", _2="unit")]}
    assert ee.hops_of_entry(e2, "ko", {})[0][0]["term"] == "單位"


def test_noncognate_and_affixes_ignored_but_reconstructed_roots_kept():
    e = {"w": "x", "l": "en", "tp": [tpl("ncog", _1="de", _2="y"), tpl("inh", _1="en", _2="ine-pro", _3="*ped-"), tpl("der", _1="en", _2="la", _3="-tio")]}
    chain, cogs = ee.hops_of_entry(e, "en", {})
    assert cogs == []
    assert [(h["lang"], h["term"]) for h in chain] == [("ine-pro", "*ped-")]


def test_anc_key_unifies_han_namespace_and_strips_diacritics():
    assert ee.anc_key("ltc", "單位") == ee.anc_key("zh", "单位") == ("han", "單位")
    assert ee.anc_key("la", "lībra") == ("la", "libra")
    assert ee.anc_key("la", "-") is None


# ---------------------------------------------------------------- graph rules
def _graph(lexicon, matches, desc_paths=()):
    langs = list(lexicon)
    reg = ec.load_registry(langs)
    return ee.build_wiktionary(lexicon, langs, reg, matches, list(desc_paths), None)


def test_origin_nearest_first_then_priority():
    lex = {"es": {"pie": [1, 1, "pie", ""]}}
    m = {"es": {"pie": [{"w": "pie", "l": "es", "p": "noun", "tp": [
        tpl("inh", _1="es", _2="osp", _3="pie"), tpl("inh", _1="es", _2="la", _3="pedem"),
        tpl("inh", _1="es", _2="ine-pro", _3="*pṓds")]}]}}
    origins, edges, sino, learned, _ = _graph(lex, m)
    assert origins[("es", "pie")]["o"] == "Old Spanish pie" and origins[("es", "pie")]["ot"] == "inh"
    # two ancestors at the same distance: priority list decides (la before grc)
    m2 = {"es": {"pie": [{"w": "pie", "l": "es", "p": "noun", "tp": [tpl("etymon", _1="es", _2=":bor", _3="grc:πούς", _4="la:pedem")]}]}}
    origins, *_ = _graph(lex, m2)
    assert origins[("es", "pie")]["o"] == "Latin pedem" and origins[("es", "pie")]["ot"] == "bor"


def test_shared_ancestor_gives_cognates_and_direct_link_gives_borrowing():
    lex = {"es": {"volar": [1, 1, "", ""]}, "it": {"volare": [1, 1, "", ""]}, "ja": {"エピソード": [1, 1, "", ""]}, "en": {"episode": [1, 1, "", ""]}}
    m = {"es": {"volar": [{"w": "volar", "l": "es", "tp": [tpl("inh", _1="es", _2="la", _3="volāre")]}]},
         "it": {"volare": [{"w": "volare", "l": "it", "tp": [tpl("inh", _1="it", _2="la", _3="volāre")]}]},
         "ja": {"エピソード": [{"w": "エピソード", "l": "ja", "tp": [tpl("bor", _1="ja", _2="en", _3="episode")]}]}}
    origins, edges, *_ = _graph(lex, m)
    assert edges[("es", "volar")][("it", "volare")][0] == "cog"
    assert edges[("ja", "エピソード")][("en", "episode")][0] == "bor"
    assert edges[("en", "episode")][("ja", "エピソード")][0] == "bor"
    assert origins[("ja", "エピソード")]["o"] == "English episode"


def test_hub_guard_skips_ancestors_with_more_than_40_descendants():
    lex = {"es": {f"w{i}": [1, 1, "", ""] for i in range(25)}, "it": {f"v{i}": [1, 1, "", ""] for i in range(25)}}
    m = {"es": {w: [{"w": w, "l": "es", "tp": [tpl("inh", _1="es", _2="la", _3="hub")]}] for w in lex["es"]},
         "it": {w: [{"w": w, "l": "it", "tp": [tpl("inh", _1="it", _2="la", _3="hub")]}] for w in lex["it"]}}
    _, edges, *_ = _graph(lex, m)
    assert sum(len(v) for v in edges.values()) == 0
    # 20 + 20 = 40 descendants is allowed
    lex2 = {"es": dict(list(lex["es"].items())[:20]), "it": dict(list(lex["it"].items())[:20])}
    m2 = {"es": {w: m["es"][w] for w in lex2["es"]}, "it": {w: m["it"][w] for w in lex2["it"]}}
    _, edges2, *_ = _graph(lex2, m2)
    assert len(edges2[("es", "w0")]) == 20


def test_combined_distance_cap():
    lex = {"es": {"a": [1, 1, "", ""]}, "it": {"b": [1, 1, "", ""]}}
    far = lambda l, w: [{"w": w, "l": l, "tp": [tpl("inh", _1=l, _2="x1", _3="t1" + l), tpl("inh", _1=l, _2="x2", _3="t2" + l), tpl("inh", _1=l, _2="la", _3="root")]}]
    _, edges, *_ = _graph(lex, {"es": {"a": far("es", "a")}, "it": {"b": far("it", "b")}})
    assert edges == {} or not edges.get(("es", "a"))   # 3 + 3 > 4


def test_descendants_list_links_ancestor_to_corpus_lemmas(tmp_path):
    desc = tmp_path / "Latin.descendants.jsonl"
    desc.write_text(json.dumps({"w": "thesaurus", "p": "noun", "l": "la", "de": [
        {"lang": "Spanish", "lang_code": "es", "word": "tesoro"},
        {"lang": "Korean", "lang_code": "ko", "word": "학생(學生)"},
        {"lang": "Old Galician-Portuguese", "lang_code": "roa-opt", "word": "tesouro",
         "descendants": [{"lang": "Portuguese", "lang_code": "pt", "word": "tesouro"}]},
        {"lang": "Italian", "lang_code": "it", "word": "tesoro", "raw_tags": ["borrowed"]}]}) + "\n")
    lex = {"es": {"tesoro": [1, 1, "", ""]}, "it": {"tesoro": [1, 1, "", ""]}, "pt": {"tesouro": [1, 1, "", ""]}}
    _, edges, _, _, anc = _graph(lex, {}, [desc])
    assert edges[("es", "tesoro")][("it", "tesoro")][0] == "cog"
    assert edges[("es", "tesoro")][("pt", "tesouro")][0] == "cog"     # depth 1 + depth 2 = 3 ≤ 4
    assert anc[("la", "thesaurus")][("it", "tesoro")][1] == "bor"
    assert "descendants" in edges[("es", "tesoro")][("it", "tesoro")][1]


def test_sinographic_triples_from_headword_hanja_and_kyujitai():
    lex = {"ja": {"単位": [1, 1, "", ""]}, "ko": {"단위": [1, 1, "", ""]}, "zh": {"单位": [1, 1, "", ""]}}
    m = {"ja": {"単位": [{"w": "単位", "l": "ja", "f": [{"f": "單位", "t": ["alternative", "kanji", "kyūjitai"]}], "tp": [tpl("bor", _1="ja", _2="ltc", _3="單位")]}]},
         "ko": {"단위": [{"w": "단위", "l": "ko", "f": [{"f": "單位", "t": ["hanja"]}], "tp": [tpl("ko-etym-sino", _1="單位")]}]},
         "zh": {"单位": [{"w": "單位", "l": "zh", "f": [{"f": "单位", "t": ["Simplified-Chinese"]}], "tp": []}]}}
    origins, edges, sino, *_ = _graph(lex, m)
    assert [s for s in sino if s["han"] == "單位" and all(k in s for k in ("ja", "ko", "zh"))]
    assert edges[("ja", "単位")][("ko", "단위")][0] == "cog"
    assert edges[("ja", "単位")][("zh", "单位")][0] == "bor"
    assert origins[("ja", "単位")]["o"] == "Middle Chinese 單位" and origins[("ja", "単位")]["ot"] == "bor"


# ---------------------------------------------------------------- end to end
SYNTH = [
    {"w": "clavis", "p": "noun", "l": "la", "tp": [{"n": "inh", "a": {"1": "la", "2": "itc-pro", "3": "*klāwis"}, "x": "Proto-Italic *klāwis"}], "so": [{"ipa": "/ˈkla.u̯is/"}]},
    {"w": "llave", "p": "noun", "l": "es", "tp": [{"n": "inh", "a": {"1": "es", "2": "osp", "3": "llave"}, "x": "Old Spanish llave"}, {"n": "inh", "a": {"1": "es", "2": "la", "3": "clāvem"}, "x": "Latin clāvem"}], "so": [{"ipa": "/ˈʝabe/"}], "f": [{"f": "llaves", "t": ["plural"]}]},
    {"w": "chiave", "p": "noun", "l": "it", "tp": [{"n": "inh", "a": {"1": "it", "2": "la", "3": "clāvem"}, "x": "Latin clāvem"}], "so": [{"ipa": "/ˈkja.ve/"}, {"audio": "It-chiave.ogg", "ogg_url": "https://upload.wikimedia.org/x/It-chiave.ogg", "mp3_url": "https://upload.wikimedia.org/x/It-chiave.ogg.mp3"}]},
    {"w": "clef", "p": "noun", "l": "fr", "tp": [{"n": "inh", "a": {"1": "fr", "2": "fro", "3": "clef"}, "x": "Old French clef"}, {"n": "inh", "a": {"1": "fr", "2": "la", "3": "clāvem"}, "x": "Latin clāvem"}]},
    {"w": "clé", "p": "noun", "l": "fr", "tp": [{"n": "inh", "a": {"1": "fr", "2": "fro", "3": "clef"}, "x": "Old French clef"}]},
    {"w": "Schlüssel", "p": "noun", "l": "de", "tp": [{"n": "inh", "a": {"1": "de", "2": "gmh", "3": "slüzzel"}, "x": "Middle High German slüzzel"}], "so": [{"ipa": "/ˈʃlʏsl̩/"}, {"audio": "De-Schlüssel.ogg", "ogg_url": "https://upload.wikimedia.org/x/De-Schl%C3%BCssel.ogg", "mp3_url": "https://upload.wikimedia.org/x/De-Schl%C3%BCssel.ogg.mp3"}]},
    {"w": "sleutel", "p": "noun", "l": "nl", "tp": [{"n": "inh", "a": {"1": "nl", "2": "dum", "3": "slotel"}, "x": "Middle Dutch slotel"}, {"n": "cog", "a": {"1": "de", "2": "Schlüssel"}, "x": "German Schlüssel"}]},
    {"w": "key", "p": "noun", "l": "en", "tp": [{"n": "inh", "a": {"1": "en", "2": "enm", "3": "keye"}, "x": "Middle English keye"}, {"n": "inh", "a": {"1": "en", "2": "ang", "3": "cǣġ"}, "x": "Old English cǣġ"}], "so": [{"ipa": "/kiː/"}, {"audio": "En-us-key.ogg", "ogg_url": "https://upload.wikimedia.org/x/En-us-key.ogg", "tags": ["US"]}]},
    {"w": "episode", "p": "noun", "l": "en", "tp": [{"n": "bor", "a": {"1": "en", "2": "fr", "3": "épisode"}, "x": "French épisode"}]},
    {"w": "エピソード", "p": "noun", "l": "ja", "tp": [{"n": "bor", "a": {"1": "ja", "2": "en", "3": "episode"}, "x": "English episode"}], "so": [{"ipa": "[e̞pʲiso̞ːdo̞]"}]},
    {"w": "単位", "p": "noun", "l": "ja", "tp": [{"n": "bor", "a": {"1": "ja", "2": "ltc", "3": "單位"}, "x": "Middle Chinese 單位"}], "f": [{"f": "單位", "t": ["alternative", "kanji", "kyūjitai"]}]},
    {"w": "단위", "p": "noun", "l": "ko", "tp": [{"n": "ko-etym-sino", "a": {"1": "單位", "2": "unit"}, "x": "Sino-Korean word from 單位"}], "f": [{"f": "單位", "t": ["hanja"]}]},
    {"w": "單位", "p": "noun", "l": "zh", "tp": [], "f": [{"f": "单位", "t": ["Simplified-Chinese"]}], "so": [{"ipa": "/tan⁵⁵ weɪ̯⁵¹/", "tags": ["Mandarin", "Sinological-IPA"]}, {"audio": "zh-dānwèi.ogg", "ogg_url": "https://upload.wikimedia.org/x/zh-d.ogg", "mp3_url": "https://upload.wikimedia.org/x/zh-d.mp3"}]},
    {"w": "마시다", "p": "verb", "l": "ko", "tp": [{"n": "ko-etym-native", "a": {}, "x": "native"}], "f": [{"f": "마셨어요", "t": ["past", "polite"]}], "so": [{"ipa": "[ma̠ɕida̠]"}]},
    {"w": "كتاب", "p": "noun", "l": "ar", "tp": [{"n": "ar-root", "a": {"1": "ك ت ب"}, "x": ""}, {"n": "der", "a": {"1": "ar", "2": "arc", "3": "כְּתָבָא"}, "x": "Aramaic כְּתָבָא"}], "so": [{"ipa": "/ki.taːb/"}]},
    {"w": "big", "p": "adj", "l": "en", "tp": [{"n": "ncog", "a": {"1": "de", "2": "dick"}, "x": "German dick"}]},
    {"w": "dick", "p": "adj", "l": "de", "tp": [{"n": "inh", "a": {"1": "de", "2": "gmh", "3": "dicke"}, "x": "Middle High German dicke"}]},
    {"w": "hotel", "p": "noun", "l": "es", "tp": [{"n": "bor", "a": {"1": "es", "2": "fr", "3": "hôtel"}, "x": "French hôtel"}]},
    {"w": "hôtel", "p": "noun", "l": "fr", "tp": [{"n": "inh", "a": {"1": "fr", "2": "la", "3": "hospitāle"}, "x": "Latin hospitāle"}]},
    {"w": "hospital", "p": "noun", "l": "en", "tp": [{"n": "bor", "a": {"1": "en", "2": "la", "3": "hospitāle"}, "x": "Latin hospitāle"}]},
]
BY_LANG = {"es": "Spanish", "fr": "French", "it": "Italian", "de": "German", "nl": "Dutch", "en": "English", "ja": "Japanese",
           "ko": "Korean", "zh": "Chinese", "ar": "Arabic", "la": "Latin"}
MINI_LEX = {
    "es": {"llave": [3, 4.0, "llave", ""], "hotel": [1, 4.5, "hotel", ""], "zzz": [1, 0, "zzz", ""]},
    "it": {"chiave": [2, 4.0, "chiave", ""]}, "fr": {"clé": [2, 4.0, "clé", ""], "hôtel": [1, 4.0, "hôtel", ""]},
    "de": {"schlüssel": [1, 4.0, "Schlüssel", ""], "dick": [1, 4.0, "dick", ""]}, "nl": {"sleutel": [1, 4.0, "sleutel", ""]},
    "en": {"key": [5, 5.0, "key", ""], "episode": [3, 4.9, "Episode", ""], "big": [1, 5.0, "big", ""], "hospital": [1, 4.0, "hospital", ""]},
    "ja": {"エピソード": [2, 4.3, "エピソード", "episoodo"], "単位": [1, 4.0, "単位", "tan'i"]},
    "ko": {"단위": [1, 4.0, "단위", "danwi"], "마셨": [1, 3.0, "마셨", "masyeoss"]},
    "zh": {"单位": [1, 4.0, "单位", "dān wèi"]}, "ar": {"الكتاب": [1, 4.0, "الكتاب", "alktab"]},
}


@pytest.fixture(scope="module")
def synth(tmp_path_factory):
    d = tmp_path_factory.mktemp("synth")
    kaikki = d / "kaikki"
    kaikki.mkdir()
    (kaikki / "raw").mkdir()
    by = {}
    for e in SYNTH:
        by.setdefault(BY_LANG[e["l"]], []).append(e)
    for name, entries in by.items():
        with gzip.open(kaikki / f"{name}.jsonl.gz", "wt", encoding="utf-8") as f:
            for e in entries:
                f.write(json.dumps(e, ensure_ascii=False) + "\n")
    (kaikki / "raw" / "Latin.descendants.jsonl").write_text(json.dumps({"w": "clavis", "p": "noun", "l": "la", "de": [
        {"lang": "Spanish", "lang_code": "es", "word": "llave"}, {"lang": "Italian", "lang_code": "it", "word": "chiave"},
        {"lang": "French", "lang_code": "fr", "word": "clé"}, {"lang": "German", "lang_code": "de", "word": "Klavier", "raw_tags": ["borrowed"]}]}) + "\n")
    etymdb = d / "etymdb"
    etymdb.mkdir()
    (etymdb / "etymdb_values.csv").write_text(
        "1\tes\t0\tllave\tkey\n2\tla\t0\tclavis\tkey\n3\tit\t0\tchiave\tkey\n4\tnl\t0\tsleutel\tkey\n5\tdum\t0\tslotel\tkey\n"
        "6\tde\t0\tSchlüssel\tkey\n7\tgmh\t0\tslüzzel\tkey\n8\tgem-pro\t0\t*slutilaz\tkey\n9\tja\t0\tエピソード\t\n10\ten\t0\tepisode\t\n", encoding="utf-8")
    (etymdb / "etymdb_links_info.csv").write_text("inh\t1\t2\ninh\t3\t2\ninh\t4\t5\ninh\t6\t7\ninh\t5\t8\ninh\t7\t8\nbor\t9\t10\n")
    (etymdb / "etymdb_links_index.csv").write_text("")
    lex = d / "lexicon.json"
    lex.write_text(json.dumps(MINI_LEX, ensure_ascii=False), encoding="utf-8")
    out = d / "build"
    return {"dir": d, "kaikki": kaikki, "etymdb": etymdb, "lexicon": lex, "out": out}


@pytest.fixture(scope="module")
def run(synth):
    cov = ee.main(["--lexicon", str(synth["lexicon"]), "--kaikki-dir", str(synth["kaikki"]), "--etymdb-dir", str(synth["etymdb"]),
                   "--desc-dir", str(synth["kaikki"] / "raw"), "--out-dir", str(synth["out"]), "--workers", "1"])
    with gzip.open(synth["out"] / "packs" / "etymology" / "etym.json.gz", "rt", encoding="utf-8") as f:
        etym = json.load(f)
    prov = [json.loads(l) for l in (synth["out"] / "enrich" / "etym_provenance.jsonl").read_text(encoding="utf-8").splitlines()]
    return cov, etym, prov


def test_e2e_origins_and_sources(run):
    cov, etym, prov = run
    assert etym["es"]["llave"]["o"] == "Old Spanish llave" and etym["es"]["llave"]["ot"] == "inh"
    assert etym["es"]["llave"]["src"] == "wiktionary+etymdb"
    assert etym["es"]["llave"]["ipa"] == "/ˈʝabe/"
    assert etym["de"]["schlüssel"]["o"] == "Middle High German slüzzel"
    assert etym["ja"]["エピソード"]["o"] == "English episode" and etym["ja"]["エピソード"]["ot"] == "bor"
    assert etym["ko"]["단위"]["o"] == "Chinese 單位" and etym["ko"]["단위"]["ot"] == "bor"
    assert etym["ar"]["الكتاب"]["o"] == "Aramaic כְּתָבָא"          # matched through the stripped clitic ال
    assert etym["es"]["hotel"]["o"] == "French hôtel" and etym["es"]["hotel"]["ot"] == "bor"   # nearest wins
    assert "zzz" not in etym["es"]
    assert "마셨" not in etym["ko"] or "o" not in etym["ko"]["마셨"]     # native word: matched via a form, no origin


def test_e2e_edges(run):
    cov, etym, prov = run
    e = {tuple(x[:2]): x[2] for x in etym["es"]["llave"]["e"]}
    assert e[("it", "chiave")] == "cog" and e[("fr", "clé")] == "cog"
    assert ("de", "klavier") not in e                        # descendant not in the lexicon → nothing
    assert {tuple(x[:2]): x[2] for x in etym["nl"]["sleutel"]["e"]}[("de", "schlüssel")] == "cog"   # cog template
    assert {tuple(x[:2]): x[2] for x in etym["ja"]["エピソード"]["e"]}[("en", "episode")] == "bor"
    assert {tuple(x[:2]): x[2] for x in etym["ja"]["単位"]["e"]} == {("ko", "단위"): "cog", ("zh", "单位"): "bor"}
    assert {tuple(x[:2]): x[2] for x in etym["zh"]["单位"]["e"]} == {("ja", "単位"): "bor", ("ko", "단위"): "bor"}
    assert "big" not in etym["en"] or not etym["en"]["big"].get("e")   # ncog is not a cognate
    hosp = {tuple(x[:2]): x[2] for x in etym["en"]["hospital"]["e"]}
    assert hosp[("fr", "hôtel")] == "cog"                      # shared Latin hospitāle, distance 1 + 1
    assert hosp.get(("es", "hotel")) == "cog"                  # es hotel → fr hôtel → la hospitāle: distance 2 + 1


def test_e2e_every_fact_has_provenance(run):
    cov, etym, prov = run
    facts = set()
    for l in etym:
        for w, r in etym[l].items():
            if "o" in r:
                facts.add((l, w, "origin", r["o"]))
            for x in r.get("e", []):
                facts.add((l, w, "edge", tuple(x)))
    logged = {(p["lang"], p["lemma"], p["fact"], p["value"] if p["fact"] == "origin" else tuple(p["value"])) for p in prov}
    assert facts <= logged
    assert all(p["via"] and p["src"] in ("wiktionary", "etymdb") for p in prov)


def test_e2e_reports_and_manifest(run, synth):
    cov, etym, prov = run
    assert cov["per_language"]["es"]["with_origin"] == 2
    assert cov["sinographic"]["triples_ja_ko_zh"] == 1
    frag = json.loads((synth["out"] / "packs" / "etymology" / "manifest-fragment.json").read_text(encoding="utf-8"))
    assert frag["kind"] == "ETYMOLOGY" and frag["licence"] == "CC BY-SA 4.0"
    assert {a["name"].split(" ")[0] for a in frag["attribution"]} == {"Wiktionary", "EtymDB"}
    assert "etym.json.gz" in frag["files"] and frag["stats"]["es"]["origins"] == 2


def test_edge_cap_is_eight(run):
    # synthetic: one lemma with 12 direct partners
    lex = {"en": {"hub": [1, 1, "", ""]}, "fr": {f"f{i}": [1, 1, "", ""] for i in range(6)}, "es": {f"s{i}": [1, 1, "", ""] for i in range(6)}}
    m = {"en": {"hub": [{"w": "hub", "l": "en", "tp": [{"n": "cog", "a": {"1": "fr", "2": f"f{i}"}} for i in range(6)] + [{"n": "cog", "a": {"1": "es", "2": f"s{i}"}} for i in range(6)]}]}}
    origins, edges, *_ = _graph(lex, m)
    assert len(edges[("en", "hub")]) == 12
    # the emit step caps at 8 deterministically (sorted by lang, lemma)
    ordered = sorted(edges[("en", "hub")].items(), key=lambda kv: (kv[0][0], kv[0][1]))[:ee.EDGE_CAP]
    assert len(ordered) == ee.EDGE_CAP and ordered[0][0] == ("es", "s0")
