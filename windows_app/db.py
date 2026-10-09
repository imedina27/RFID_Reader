import glob
import os
import sys
from pathlib import Path

if sys.platform == "win32":
    # Una politica WDAC de la empresa bloquea el .pyd compilado de
    # psycopg[binary] (DLL sin firma de Enterprise). Usamos la implementacion
    # pura Python de psycopg en su lugar, que habla con PostgreSQL via la
    # libpq.dll que ya viene con la instalacion local de PostgreSQL (esa si
    # pasa la politica).
    for _carpeta in glob.glob(r"C:\Program Files\PostgreSQL\*\bin"):
        if os.path.isfile(os.path.join(_carpeta, "libpq.dll")):
            os.add_dll_directory(_carpeta)
            os.environ["PATH"] = _carpeta + os.pathsep + os.environ.get("PATH", "")
            break

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
