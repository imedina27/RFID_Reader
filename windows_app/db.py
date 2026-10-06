import os
from pathlib import Path

import psycopg
from dotenv import load_dotenv
from psycopg.rows import dict_row

load_dotenv(Path(__file__).resolve().parent / ".env")

SCHEMA_PATH = Path(__file__).resolve().parent / "schema.sql"


def get_connection() -> psycopg.Connection:
    database_url = os.environ["DATABASE_URL"]
    return psycopg.connect(database_url, row_factory=dict_row)


def init_schema() -> None:
    with get_connection() as conn:
        conn.execute(SCHEMA_PATH.read_text(encoding="utf-8"))
