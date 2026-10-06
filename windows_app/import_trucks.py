"""Importa camiones y su etiqueta de parabrisas desde docs/vehicleInfo_43.xlsx.

Fuente: hoja 'unidades' (columnas corporativo, no_economico, tag_id, nombre, tipo).
Solo se cargan filas con un tag_id valido (24 caracteres hexadecimales): el resto
son notas, pruebas o unidades sin etiquetar ("S/N", "0", "SIN TAG", etc.).

Reglas de limpieza acordadas con el usuario:
- Se cargan TODOS los tipos de vehiculo que traigan tag valido (no solo 'Camiones').
- 'no_economico' debe ser unico (UNIQUE en trucks.unit_number). Cuando un mismo
  no_economico real se repite con tags distintos, se asume re-etiquetado y se
  conserva solo el registro con 'corporativo' mas alto (el mas reciente).
- 'no_economico' = 'S/N' no identifica un camion real: se le agrega el id
  'corporativo' como sufijo para volverlo unico sin perder ninguna unidad.

No escribe en tags.product_id ni tags.folio (son etiquetas de camion, kind='truck').
"""

import re
import sys
from pathlib import Path

import openpyxl

import db

XLSX_PATH = Path(__file__).resolve().parent.parent / "docs" / "vehicleInfo_43.xlsx"
TAG_RE = re.compile(r"^[0-9A-Fa-f]{24}$")


def load_rows() -> list[dict]:
    wb = openpyxl.load_workbook(XLSX_PATH, data_only=True)
    ws = wb["unidades"]
    rows = []
    for corporativo, no_economico, tag_id, _nombre, tipo in ws.iter_rows(min_row=2, values_only=True):
        if not tag_id or not TAG_RE.match(str(tag_id).strip()):
            continue
        rows.append({
            "corporativo": corporativo,
            "no_economico": (no_economico or "").strip(),
            "epc": str(tag_id).strip().upper(),
            "tipo": tipo,
        })
    return rows


def resolve_unit_numbers(rows: list[dict]) -> list[dict]:
    """Deja un solo registro por no_economico real (el de 'corporativo' mas alto)
    y vuelve unico cada 'S/N' con el id 'corporativo' como sufijo."""
    by_number: dict[str, list[dict]] = {}
    for row in rows:
        by_number.setdefault(row["no_economico"], []).append(row)

    resolved = []
    for no_economico, group in by_number.items():
        if no_economico == "S/N":
            for row in group:
                row["unit_number"] = f"S/N-{row['corporativo']}"
                resolved.append(row)
        elif len(group) == 1:
            group[0]["unit_number"] = no_economico
            resolved.append(group[0])
        else:
            latest = max(group, key=lambda r: int(r["corporativo"]))
            latest["unit_number"] = no_economico
            resolved.append(latest)
            for dropped in group:
                if dropped is not latest:
                    print(f"  omitido por re-etiquetado: {no_economico} "
                          f"(corporativo {dropped['corporativo']}, tag {dropped['epc']})")
    return resolved


def import_trucks(rows: list[dict]) -> None:
    created_trucks = created_tags = skipped = 0
    with db.get_connection() as conn:
        for row in rows:
            truck = conn.execute(
                """
                INSERT INTO trucks (unit_number)
                VALUES (%s)
                ON CONFLICT (unit_number) DO NOTHING
                RETURNING id
                """,
                (row["unit_number"],),
            ).fetchone()
            if truck is None:
                skipped += 1
                continue
            created_trucks += 1
            tag = conn.execute(
                """
                INSERT INTO tags (epc, kind, truck_id)
                VALUES (%s, 'truck', %s)
                ON CONFLICT (epc) DO NOTHING
                RETURNING epc
                """,
                (row["epc"], truck["id"]),
            ).fetchone()
            if tag is not None:
                created_tags += 1
        conn.commit()
    print(f"Camiones creados: {created_trucks} | etiquetas creadas: {created_tags} | "
          f"omitidos (ya existian): {skipped}")


if __name__ == "__main__":
    db.init_schema()
    all_rows = load_rows()
    print(f"Filas con tag valido en el archivo: {len(all_rows)}")
    resolved_rows = resolve_unit_numbers(all_rows)
    print(f"Camiones a importar tras resolver duplicados: {len(resolved_rows)}")
    import_trucks(resolved_rows)
