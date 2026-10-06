"""Carga datos de ejemplo: productos de la demo (docs/modelo_datos.md).

Camiones y boletas de ejemplo se dan de alta desde la interfaz Windows (Fase 5)
o manualmente vIa la API una vez que haya numeros economicos y clientes reales
confirmados (ROADMAP.md, Fase 2).
"""

import db

PRODUCTS = [
    ("COCA-2L", "Coca Cola 2 L", "2 L"),
    ("COCA-600", "Coca Cola 600 ml", "600 ml"),
    ("SPRITE-600", "Sprite 600 ml", "600 ml"),
    ("CRISTAL-600", "Agua Cristal 600 ml", "600 ml"),
    ("BEVI-355", "Bevi 355 ml", "355 ml"),
]


def seed_products() -> None:
    with db.get_connection() as conn:
        with conn.cursor() as cur:
            cur.executemany(
                """
                INSERT INTO products (code, name, presentation)
                VALUES (%s, %s, %s)
                ON CONFLICT (code) DO NOTHING
                """,
                PRODUCTS,
            )


if __name__ == "__main__":
    db.init_schema()
    seed_products()
    print("Productos cargados.")
