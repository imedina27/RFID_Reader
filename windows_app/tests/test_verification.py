import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from verification import build_alarms, is_exact_match, product_diffs


def test_product_diffs_exact_match():
    diffs = product_diffs({1: 4, 2: 2}, {1: 4, 2: 2})
    assert {d["product_id"]: d["diff"] for d in diffs} == {1: 0, 2: 0}


def test_product_diffs_missing():
    diffs = product_diffs({1: 4, 2: 2}, {1: 4, 2: 1})
    by_product = {d["product_id"]: d for d in diffs}
    assert by_product[2]["diff"] == -1
    assert by_product[2]["expected"] == 2
    assert by_product[2]["read"] == 1


def test_product_diffs_excess_on_unrequested_product():
    diffs = product_diffs({1: 4}, {1: 4, 8: 1})
    by_product = {d["product_id"]: d for d in diffs}
    assert by_product[8]["diff"] == 1
    assert by_product[8]["expected"] == 0


def test_is_exact_match_true_when_counts_match_and_no_problem_tags():
    assert is_exact_match({1: 4, 2: 2}, {1: 4, 2: 2}, []) is True


def test_is_exact_match_false_on_missing():
    assert is_exact_match({1: 4, 2: 2}, {1: 4, 2: 1}, []) is False


def test_is_exact_match_false_on_excess():
    assert is_exact_match({1: 4}, {1: 5}, []) is False


def test_is_exact_match_true_on_unknown_tag_if_counts_match():
    # Decision 2026-10-07: una etiqueta no registrada ya no bloquea si el
    # producto esperado por boleta esta completo.
    problem_tags = [{"epc": "E2...", "result": "unknown"}]
    assert is_exact_match({1: 4}, {1: 4}, problem_tags) is True


def test_is_exact_match_true_on_already_dispatched_tag_if_counts_match():
    # Decision 2026-10-07: una etiqueta ya despachada de mas tampoco
    # bloquea si el producto esperado por boleta esta completo (solo
    # queda registrada como alarma para revision, ver ALARM_READ_RESULTS).
    problem_tags = [{"epc": "E2...", "result": "already_dispatched"}]
    assert is_exact_match({1: 4}, {1: 4}, problem_tags) is True


def test_is_exact_match_still_false_on_missing_even_with_unknown_tag():
    # Lo que SI sigue bloqueando es que falte o sobre producto de verdad,
    # sin importar si ademas hubo una etiqueta rara de por medio.
    problem_tags = [{"epc": "E2...", "result": "unknown"}]
    assert is_exact_match({1: 4, 2: 2}, {1: 4, 2: 1}, problem_tags) is False


def test_is_exact_match_ignores_other_truck_tags():
    problem_tags = [{"epc": "E2...", "result": "other_truck"}]
    assert is_exact_match({1: 4}, {1: 4}, problem_tags) is True


def test_build_alarms_missing_and_excess():
    alarms = build_alarms({1: 4, 2: 2}, {1: 3, 2: 3}, [])
    types = {(a["type"], a["product_id"]) for a in alarms}
    assert ("missing", 1) in types
    assert ("excess", 2) in types


def test_build_alarms_unknown_and_already_dispatched_tags():
    problem_tags = [
        {"epc": "AAA", "result": "unknown"},
        {"epc": "BBB", "result": "already_dispatched"},
    ]
    alarms = build_alarms({1: 4}, {1: 4}, problem_tags)
    types = {a["type"] for a in alarms}
    assert types == {"unknown_tag", "already_dispatched"}


def test_build_alarms_empty_when_everything_matches():
    assert build_alarms({1: 4}, {1: 4}, []) == []
