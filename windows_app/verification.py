"""Verificacion de una salida a ruta: esperado contra leido, por producto.

Logica aislada y sin dependencias de base de datos (docs/funcional.md seccion 4,
docs/modelo_datos.md seccion "Calculo de la verificacion"). Se prueba en
tests/test_verification.py.
"""

BLOCKING_READ_RESULTS = {"unknown", "already_dispatched"}


def product_diffs(expected: dict[int, int], read_counts: dict[int, int]) -> list[dict]:
    """Une los productos esperados y leidos y calcula la diferencia por producto."""
    product_ids = set(expected) | set(read_counts)
    diffs = []
    for product_id in product_ids:
        exp = expected.get(product_id, 0)
        read = read_counts.get(product_id, 0)
        diffs.append({
            "product_id": product_id,
            "expected": exp,
            "read": read,
            "diff": read - exp,
        })
    return diffs


def is_exact_match(expected: dict[int, int], read_counts: dict[int, int],
                    problem_tags: list[dict]) -> bool:
    """La salida es correcta solo si leido = esperado para todos los productos
    y no hay etiquetas desconocidas ni ya despachadas (docs/funcional.md regla 6)."""
    if any(d["diff"] != 0 for d in product_diffs(expected, read_counts)):
        return False
    if any(tag["result"] in BLOCKING_READ_RESULTS for tag in problem_tags):
        return False
    return True


def build_alarms(expected: dict[int, int], read_counts: dict[int, int],
                  problem_tags: list[dict]) -> list[dict]:
    """Arma la lista de alarmas a registrar cuando la salida no cuadra."""
    alarms = []
    for d in product_diffs(expected, read_counts):
        if d["diff"] < 0:
            alarms.append({"type": "missing", "product_id": d["product_id"], "diff": d["diff"]})
        elif d["diff"] > 0:
            alarms.append({"type": "excess", "product_id": d["product_id"], "diff": d["diff"]})
    for tag in problem_tags:
        if tag["result"] == "unknown":
            alarms.append({"type": "unknown_tag", "epc": tag["epc"]})
        elif tag["result"] == "already_dispatched":
            alarms.append({"type": "already_dispatched", "epc": tag["epc"]})
    return alarms
