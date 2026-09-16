"""Unit tests for enrich_common: normalisation, candidate generation, licence classification."""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import enrich_common as ec  # noqa: E402


def test_norm_key_latin_is_nfkc_lowercase():
    assert ec.norm_key("es", "Ｃasa ") == "casa"
    assert ec.norm_key("de", "Straße") == "straße"


def test_norm_key_cjk_keeps_case_and_folds_width():
    assert ec.norm_key("ja", "ＡＢＣ") == "ABC"
    assert ec.norm_key("zh", "飞行") == "飞行"


def test_ar_norm_strips_diacritics_and_folds_alef_and_format_chars():
    assert ec.ar_norm("جُرعةُ") == "جرعة"
    assert ec.ar_norm("أنا‏") == "انا"
    assert ec.ar_norm("إِسْلَام") == "اسلام"


def test_ar_candidates_strip_clitics_but_keep_exact_first():
    c = ec.ar_candidates("والكتاب")
    assert c[0] == "والكتاب"
    assert "كتاب" in c and "الكتاب" in c
    assert ec.ar_candidates("اخرى؟")[0] == "اخرى"


def test_ko_candidates_generate_dictionary_forms_without_inventing():
    assert "마시다" in ec.ko_candidates("마셨")
    assert "가다" in ec.ko_candidates("갔")
    assert "먹다" in ec.ko_candidates("먹었")
    assert "하다" in ec.ko_candidates("했")
    assert ec.ko_candidates("비행")[0] == "비행"          # exact key always first
    assert "단다" in ec.ko_candidates("단어")              # derived candidates are verb headwords only …
    assert "단" not in ec.ko_candidates("단어")            # … never a bare stripped stem
    assert "하" not in ec.ko_candidates("하고") and "하다" in ec.ko_candidates("하고")
    assert ec.ko_candidates("단어")[0] == "단어"           # exact key ranks first


def test_zh_keys_include_traditional_and_simplified():
    keys = ec.lexicon_keys("zh", "飞行")
    assert keys[0] == "飞行" and "飛行" in keys
    keys = ec.lexicon_keys("zh", "單位")
    assert "单位" in keys


def test_latin_keys_add_diacritic_stripped_secondary():
    assert ec.lexicon_keys("fr", "Épée") == ["épée", "epee"]


@pytest.mark.parametrize("short,expected", [
    ("CC BY-SA 4.0", "CC BY-SA 4.0"),
    ("CC BY 4.0", "CC BY 4.0"),
    ("CC BY 2.0 FR", "CC BY 2.0 FR"),
    ("CC0", "CC0 1.0"),
    ("CC0 1.0", "CC0 1.0"),
    ("Public domain", "Public domain"),
    ("CC BY-NC 4.0", None),
    ("CC BY-NC-ND 3.0", None),
    ("CC BY-ND 4.0", None),
    ("", None),
    ("\\N", None),
    (None, None),
    ("GFDL", None),
    ("Attribution-NonCommercial", None),
])
def test_permissive_licence(short, expected):
    assert ec.permissive_licence(short) == expected


def test_strip_html():
    assert ec.strip_html('<a href="x">Jean  Dupont</a>') == "Jean Dupont"
    assert ec.strip_html(None) is None


def test_registry_builtin_has_enrichment_fields():
    reg = ec.load_registry(["es", "zh", "ko"])
    for iso in ("es", "zh", "ko"):
        assert reg[iso]["kaikkiName"] and reg[iso]["tatoebaCode"] and reg[iso]["etymdbCodes"]
    assert "ltc" in reg["zh"]["etymdbCodes"]
    assert reg["ko"]["tatoebaCode"] == "kor"


def test_latin_tokenizer_lemmatises_and_memoises():
    tok = ec.Tokenizer("es", "latin")
    toks = tok.tokens("Las pociones vuelan.")
    assert [s for s, _ in toks] == ["Las", "pociones", "vuelan"]
    assert toks[1][1] == "poción"
    assert "pociones" in tok._cache
