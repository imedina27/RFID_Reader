import psycopg
from flask import Flask, g, jsonify, request
from psycopg.types.json import Json

import db
import live_state
from errors import ApiError, bad_request, conflict, not_found
from verification import build_alarms, is_exact_match, product_diffs

# Etiquetas no registrada / ya despachada: ya no bloquean el cierre de la
# salida (decision del usuario, 2026-10-07; ver verification.BLOCKING_READ_RESULTS),
# pero siguen quedando registradas en Alarmas para revision.
ALARM_READ_RESULTS = {"unknown", "already_dispatched"}


def normalize_epc(raw: str) -> str:
    if not isinstance(raw, str) or not raw.strip():
        raise bad_request("Se requiere el EPC.", "missing_epc")
    return raw.strip().upper()


def tiene_prefijo_valido(conn, epc: str) -> bool:
    """Lista blanca de prefijos de EPC (pantalla Prefijos): si no hay
    prefijos cargados, no se filtra nada (evita bloquear todo por error de
    configuracion)."""
    prefijos = [row["prefix"] for row in conn.execute("SELECT prefix FROM epc_prefixes").fetchall()]
    if not prefijos:
        return True
    return any(epc.startswith(prefijo) for prefijo in prefijos)


def create_app() -> Flask:
    app = Flask(__name__)

    @app.errorhandler(ApiError)
    def handle_api_error(err: ApiError):
        return jsonify(err.to_dict()), err.status_code

    @app.teardown_appcontext
    def close_connection(exception=None):
        conn = g.pop("db_conn", None)
        if conn is not None:
            if exception is None:
                conn.commit()
            else:
                conn.rollback()
            conn.close()

    def get_conn():
        if "db_conn" not in g:
            g.db_conn = db.get_connection()
        return g.db_conn

    # ---------------------------------------------------------- salud -----
    @app.get("/api/health")
    def health():
        try:
            get_conn().execute("SELECT 1")
        except Exception:
            return jsonify({"status": "error", "database": "down"}), 503
        return jsonify({"status": "ok", "database": "ok"})

    # ------------------------------------------------------- productos ----
    @app.get("/api/products")
    def list_products():
        rows = get_conn().execute(
            "SELECT id, code, name, presentation, active FROM products ORDER BY name"
        ).fetchall()
        return jsonify(rows)

    @app.post("/api/products")
    def create_product():
        body = request.get_json(force=True) or {}
        code = body.get("code")
        name = body.get("name")
        if not code or not name:
            raise bad_request("Se requieren 'code' y 'name'.")
        conn = get_conn()
        row = conn.execute(
            """
            INSERT INTO products (code, name, presentation)
            VALUES (%s, %s, %s)
            ON CONFLICT (code) DO NOTHING
            RETURNING id, code, name, presentation, active
            """,
            (code, name, body.get("presentation")),
        ).fetchone()
        if row is None:
            raise conflict(f"Ya existe un producto con code '{code}'.", "product_exists")
        return jsonify(row), 201

    @app.put("/api/products/<int:product_id>")
    def update_product(product_id: int):
        body = request.get_json(force=True) or {}
        conn = get_conn()
        row = conn.execute(
            """
            UPDATE products
            SET name = COALESCE(%(name)s, name),
                presentation = COALESCE(%(presentation)s, presentation),
                active = COALESCE(%(active)s, active)
            WHERE id = %(id)s
            RETURNING id, code, name, presentation, active
            """,
            {
                "id": product_id,
                "name": body.get("name"),
                "presentation": body.get("presentation"),
                "active": body.get("active"),
            },
        ).fetchone()
        if row is None:
            raise not_found(f"No existe el producto {product_id}.")
        return jsonify(row)

    # --------------------------------------------------------- camiones ---
    @app.get("/api/trucks")
    def list_trucks():
        rows = get_conn().execute(
            "SELECT id, unit_number, plate, driver, status, status_changed_at "
            "FROM trucks ORDER BY unit_number"
        ).fetchall()
        return jsonify(rows)

    @app.post("/api/trucks")
    def create_truck():
        body = request.get_json(force=True) or {}
        unit_number = body.get("unit_number")
        if not unit_number:
            raise bad_request("Se requiere 'unit_number'.")
        conn = get_conn()
        row = conn.execute(
            """
            INSERT INTO trucks (unit_number, plate, driver)
            VALUES (%s, %s, %s)
            ON CONFLICT (unit_number) DO NOTHING
            RETURNING id, unit_number, plate, driver, status, status_changed_at
            """,
            (unit_number, body.get("plate"), body.get("driver")),
        ).fetchone()
        if row is None:
            raise conflict(f"Ya existe un camión '{unit_number}'.", "truck_exists")
        return jsonify(row), 201

    @app.put("/api/trucks/<int:truck_id>")
    def update_truck(truck_id: int):
        body = request.get_json(force=True) or {}
        conn = get_conn()
        row = conn.execute(
            """
            UPDATE trucks
            SET plate = COALESCE(%(plate)s, plate),
                driver = COALESCE(%(driver)s, driver)
            WHERE id = %(id)s
            RETURNING id, unit_number, plate, driver, status, status_changed_at
            """,
            {"id": truck_id, "plate": body.get("plate"), "driver": body.get("driver")},
        ).fetchone()
        if row is None:
            raise not_found(f"No existe el camión {truck_id}.")
        return jsonify(row)

    @app.post("/api/trucks/<int:truck_id>/available")
    def mark_truck_available(truck_id: int):
        conn = get_conn()
        row = conn.execute(
            """
            UPDATE trucks SET status = 'available', status_changed_at = now()
            WHERE id = %s
            RETURNING id, unit_number, plate, driver, status, status_changed_at
            """,
            (truck_id,),
        ).fetchone()
        if row is None:
            raise not_found(f"No existe el camión {truck_id}.")
        return jsonify(row)

    # --------------------------------------------------------- etiquetas --
    @app.get("/api/tags/<epc>")
    def get_tag(epc: str):
        epc = normalize_epc(epc)
        row = _fetch_tag_view(get_conn(), epc)
        if row is None:
            return jsonify({"found": False, "epc": epc})
        row["found"] = True
        return jsonify(row)

    @app.get("/api/tags")
    def list_tags():
        kind = request.args.get("kind")
        product_id = request.args.get("product_id")
        status = request.args.get("status")
        clauses, params = [], {}
        if kind:
            clauses.append("kind = %(kind)s")
            params["kind"] = kind
        if product_id:
            clauses.append("product_id = %(product_id)s")
            params["product_id"] = product_id
        if status:
            clauses.append("status = %(status)s")
            params["status"] = status
        where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
        rows = get_conn().execute(
            f"""
            SELECT epc, kind, folio, product_id, truck_id, status, captured_at, last_read_at
            FROM tags
            {where}
            ORDER BY captured_at DESC
            """,
            params,
        ).fetchall()
        return jsonify(rows)

    @app.post("/api/tags/batch")
    def capture_tags_batch():
        body = request.get_json(force=True) or {}
        product_id = body.get("product_id")
        epcs = body.get("epcs")
        if not product_id or not epcs:
            raise bad_request("Se requieren 'product_id' y 'epcs'.")
        conn = get_conn()
        product = conn.execute(
            "SELECT id FROM products WHERE id = %s", (product_id,)
        ).fetchone()
        if product is None:
            raise not_found(f"No existe el producto {product_id}.")

        results = []
        for raw_epc in epcs:
            epc = normalize_epc(raw_epc)
            if not tiene_prefijo_valido(conn, epc):
                results.append({"epc": epc, "result": "invalid_prefix"})
                continue
            existing = conn.execute(
                "SELECT kind, status, product_id, folio FROM tags WHERE epc = %s", (epc,)
            ).fetchone()
            if existing is None:
                created = conn.execute(
                    """
                    INSERT INTO tags (epc, kind, folio, product_id)
                    VALUES (%s, 'pallet', 'PLT-' || lpad(nextval('pallet_folio_seq')::text, 6, '0'), %s)
                    RETURNING folio, captured_at
                    """,
                    (epc, product_id),
                ).fetchone()
                results.append({
                    "epc": epc,
                    "result": "created",
                    "folio": created["folio"],
                    "production_date": created["captured_at"].isoformat(),
                })
            elif existing["kind"] == "truck":
                results.append({"epc": epc, "result": "is_truck_tag"})
            elif existing["status"] == "dispatched":
                results.append({
                    "epc": epc, "result": "already_dispatched",
                    "folio": existing["folio"], "product_id": existing["product_id"],
                })
            elif existing["product_id"] == int(product_id):
                results.append({"epc": epc, "result": "already_captured", "product_id": existing["product_id"]})
            else:
                results.append({
                    "epc": epc, "result": "already_captured_other_product",
                    "product_id": existing["product_id"],
                })
        return jsonify({"product_id": product_id, "results": results})

    @app.post("/api/tags/truck")
    def capture_truck_tag():
        body = request.get_json(force=True) or {}
        truck_id = body.get("truck_id")
        epc = normalize_epc(body.get("epc", ""))
        if not truck_id:
            raise bad_request("Se requiere 'truck_id'.")
        conn = get_conn()
        truck = conn.execute("SELECT id FROM trucks WHERE id = %s", (truck_id,)).fetchone()
        if truck is None:
            raise not_found(f"No existe el camión {truck_id}.")

        existing = conn.execute(
            "SELECT kind, truck_id FROM tags WHERE epc = %s", (epc,)
        ).fetchone()
        if existing is not None:
            if existing["kind"] == "pallet":
                raise conflict("Esa etiqueta ya está asociada a un producto.", "epc_is_pallet_tag")
            if existing["truck_id"] != int(truck_id):
                raise conflict("Esa etiqueta ya está asociada a otro camión.", "tag_assigned_to_other_truck")
            return jsonify({"epc": epc, "truck_id": truck_id, "result": "already_captured"})

        already_has_tag = conn.execute(
            "SELECT epc FROM tags WHERE truck_id = %s AND kind = 'truck'", (truck_id,)
        ).fetchone()
        if already_has_tag is not None:
            raise conflict("Ese camión ya tiene una etiqueta de parabrisas.", "truck_already_has_tag")

        conn.execute(
            "INSERT INTO tags (epc, kind, truck_id) VALUES (%s, 'truck', %s)",
            (epc, truck_id),
        )
        return jsonify({"epc": epc, "truck_id": truck_id, "result": "created"}), 201

    @app.put("/api/tags/<epc>")
    def correct_tag(epc: str):
        epc = normalize_epc(epc)
        body = request.get_json(force=True) or {}
        product_id = body.get("product_id")
        if not product_id:
            raise bad_request("Se requiere 'product_id'.")
        conn = get_conn()
        tag = conn.execute("SELECT kind, status FROM tags WHERE epc = %s", (epc,)).fetchone()
        if tag is None:
            raise not_found(f"No existe la etiqueta {epc}.")
        if tag["kind"] != "pallet":
            raise bad_request("Solo se puede corregir el producto de una etiqueta de pallet.")
        if tag["status"] == "dispatched":
            raise conflict("Esa etiqueta ya fue despachada.", "already_dispatched")
        row = conn.execute(
            """
            UPDATE tags SET product_id = %s WHERE epc = %s
            RETURNING epc, kind, folio, product_id, status, captured_at
            """,
            (product_id, epc),
        ).fetchone()
        return jsonify(row)

    @app.delete("/api/tags/<epc>")
    def delete_tag(epc: str):
        epc = normalize_epc(epc)
        conn = get_conn()
        tag = conn.execute("SELECT status FROM tags WHERE epc = %s", (epc,)).fetchone()
        if tag is None:
            raise not_found(f"No existe la etiqueta {epc}.")
        if tag["status"] == "dispatched":
            raise conflict("No se puede eliminar una etiqueta ya despachada.", "already_dispatched")
        conn.execute("DELETE FROM tags WHERE epc = %s", (epc,))
        return "", 204

    @app.post("/api/tags/reset-delivered")
    def reset_delivered_tags():
        """'Limpiar Estado' (Windows, pantalla Pallets): para reutilizar los
        mismos pallets físicos entre ensayos de la demo. Regresa a
        'captured' solo los pallets cuya salida más reciente ya fue
        entregada (completed/completed_with_difference + delivered_at). Los
        que siguen en ruta o no han salido no se tocan; los de una salida
        cancelada nunca llegaron a 'dispatched', así que no aplica."""
        conn = get_conn()
        rows = conn.execute(
            """
            WITH ultimo_dispatch AS (
                SELECT DISTINCT ON (dr.epc) dr.epc, d.status, d.delivered_at
                FROM dispatch_reads dr
                JOIN dispatches d ON d.id = dr.dispatch_id
                WHERE dr.result = 'counted'
                ORDER BY dr.epc, dr.first_read_at DESC
            )
            UPDATE tags t
            SET status = 'captured'
            FROM ultimo_dispatch u
            WHERE t.epc = u.epc
              AND t.status = 'dispatched'
              AND u.status IN ('completed', 'completed_with_difference')
              AND u.delivered_at IS NOT NULL
            RETURNING t.epc
            """
        ).fetchall()
        return jsonify({"reset_count": len(rows), "epcs": [row["epc"] for row in rows]})

    # ---------------------------------------------------- boletas salida --
    @app.get("/api/exit-tickets")
    def list_exit_tickets():
        status = request.args.get("status")
        truck_id = request.args.get("truck_id")
        clauses, params = [], {}
        if status:
            clauses.append("status = %(status)s")
            params["status"] = status
        if truck_id:
            clauses.append("truck_id = %(truck_id)s")
            params["truck_id"] = truck_id
        where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
        conn = get_conn()
        tickets = conn.execute(
            f"""
            SELECT id, folio, customer, truck_id, status, created_at, dispatched_at
            FROM exit_tickets {where} ORDER BY created_at DESC
            """,
            params,
        ).fetchall()
        for ticket in tickets:
            ticket["lines"] = conn.execute(
                """
                SELECT l.product_id, p.name AS product_name, l.pallets
                FROM exit_ticket_lines l JOIN products p ON p.id = l.product_id
                WHERE l.ticket_id = %s
                """,
                (ticket["id"],),
            ).fetchall()
        return jsonify(tickets)

    @app.post("/api/exit-tickets")
    def create_exit_ticket():
        body = request.get_json(force=True) or {}
        customer = body.get("customer")
        lines = body.get("lines") or []
        if not customer or not lines:
            raise bad_request("Se requieren 'customer' y al menos una línea.")
        conn = get_conn()
        ticket = conn.execute(
            """
            INSERT INTO exit_tickets (folio, customer, truck_id)
            VALUES ('BOL-' || lpad(nextval('exit_ticket_folio_seq')::text, 6, '0'), %s, %s)
            RETURNING id, folio, customer, truck_id, status, created_at
            """,
            (customer, body.get("truck_id")),
        ).fetchone()
        for line in lines:
            conn.execute(
                "INSERT INTO exit_ticket_lines (ticket_id, product_id, pallets) VALUES (%s, %s, %s)",
                (ticket["id"], line["product_id"], line["pallets"]),
            )
        ticket["lines"] = lines
        return jsonify(ticket), 201

    @app.put("/api/exit-tickets/<int:ticket_id>")
    def update_exit_ticket(ticket_id: int):
        body = request.get_json(force=True) or {}
        conn = get_conn()
        ticket = conn.execute("SELECT status FROM exit_tickets WHERE id = %s", (ticket_id,)).fetchone()
        if ticket is None:
            raise not_found(f"No existe la boleta {ticket_id}.")
        if ticket["status"] != "active":
            raise conflict("Solo se puede editar una boleta activa.", "exit_ticket_not_active")
        if "customer" in body:
            conn.execute("UPDATE exit_tickets SET customer = %s WHERE id = %s", (body["customer"], ticket_id))
        if "lines" in body:
            conn.execute("DELETE FROM exit_ticket_lines WHERE ticket_id = %s", (ticket_id,))
            for line in body["lines"]:
                conn.execute(
                    "INSERT INTO exit_ticket_lines (ticket_id, product_id, pallets) VALUES (%s, %s, %s)",
                    (ticket_id, line["product_id"], line["pallets"]),
                )
        row = conn.execute(
            "SELECT id, folio, customer, truck_id, status, created_at FROM exit_tickets WHERE id = %s",
            (ticket_id,),
        ).fetchone()
        return jsonify(row)

    @app.post("/api/exit-tickets/<int:ticket_id>/assign-truck")
    def assign_truck(ticket_id: int):
        body = request.get_json(force=True) or {}
        truck_id = body.get("truck_id")
        if not truck_id:
            raise bad_request("Se requiere 'truck_id'.")
        conn = get_conn()
        row = conn.execute(
            """
            UPDATE exit_tickets SET truck_id = %s WHERE id = %s AND status = 'active'
            RETURNING id, folio, customer, truck_id, status
            """,
            (truck_id, ticket_id),
        ).fetchone()
        if row is None:
            raise not_found(f"No existe una boleta activa {ticket_id}.")
        return jsonify(row)

    @app.post("/api/exit-tickets/<int:ticket_id>/cancel")
    def cancel_exit_ticket(ticket_id: int):
        conn = get_conn()
        row = conn.execute(
            """
            UPDATE exit_tickets SET status = 'cancelled' WHERE id = %s AND status = 'active'
            RETURNING id, folio, status
            """,
            (ticket_id,),
        ).fetchone()
        if row is None:
            raise conflict(f"La boleta {ticket_id} no existe o no está activa.", "exit_ticket_not_active")
        return jsonify(row)

    # ------------------------------------------------------ salida a ruta -
    @app.get("/api/dispatch/lookup/<truck_epc>")
    def dispatch_lookup(truck_epc: str):
        epc = normalize_epc(truck_epc)
        conn = get_conn()
        tag = conn.execute(
            "SELECT truck_id FROM tags WHERE epc = %s AND kind = 'truck'", (epc,)
        ).fetchone()
        if tag is None:
            raise not_found("Esa etiqueta de parabrisas no está registrada.", "unknown_truck_tag")
        truck = conn.execute(
            "SELECT id, unit_number, plate, status FROM trucks WHERE id = %s", (tag["truck_id"],)
        ).fetchone()
        if truck["status"] == "en_route":
            live_state.marcar_escaneo(truck)  # panel en vivo del Tablero
            return jsonify({
                "truck": truck, "alarm": "truck_not_available",
                "message": "El camión ya está en ruta.",
            })
        tickets = conn.execute(
            "SELECT id, folio, customer FROM exit_tickets WHERE truck_id = %s AND status = 'active'",
            (truck["id"],),
        ).fetchall()
        if not tickets:
            live_state.marcar_escaneo(truck, sin_boletas=True)  # panel en vivo: entra, espera, sale
            return jsonify({
                "truck": truck, "alarm": "no_active_tickets",
                "message": "Esta unidad no tiene boletas asignadas.",
            })
        live_state.marcar_escaneo(truck)  # panel en vivo del Tablero
        for ticket in tickets:
            ticket["lines"] = conn.execute(
                """
                SELECT l.product_id, p.name AS product_name, l.pallets
                FROM exit_ticket_lines l JOIN products p ON p.id = l.product_id
                WHERE l.ticket_id = %s
                """,
                (ticket["id"],),
            ).fetchall()
        return jsonify({"truck": truck, "tickets": tickets})

    @app.post("/api/dispatches")
    def open_dispatch():
        body = request.get_json(force=True) or {}
        epc = normalize_epc(body.get("truck_epc", ""))
        ticket_ids = body.get("ticket_ids")
        if not ticket_ids:
            raise bad_request("Se requiere 'ticket_ids'.")
        conn = get_conn()
        tag = conn.execute(
            "SELECT truck_id FROM tags WHERE epc = %s AND kind = 'truck'", (epc,)
        ).fetchone()
        if tag is None:
            raise not_found("Esa etiqueta de parabrisas no está registrada.", "unknown_truck_tag")
        truck_id = tag["truck_id"]
        truck = conn.execute("SELECT status FROM trucks WHERE id = %s", (truck_id,)).fetchone()
        if truck["status"] != "available":
            raise conflict("El camión no está disponible.", "truck_not_available")

        try:
            dispatch = conn.execute(
                "INSERT INTO dispatches (truck_id) VALUES (%s) RETURNING id",
                (truck_id,),
            ).fetchone()
        except psycopg.errors.UniqueViolation as exc:
            raise conflict("El camión ya tiene una salida en proceso.", "truck_has_open_dispatch") from exc
        dispatch_id = dispatch["id"]

        for ticket_id in ticket_ids:
            ticket = conn.execute(
                "SELECT id FROM exit_tickets WHERE id = %s AND truck_id = %s AND status = 'active'",
                (ticket_id, truck_id),
            ).fetchone()
            if ticket is None:
                raise bad_request(f"La boleta {ticket_id} no es una boleta activa de este camión.")
            conn.execute(
                "INSERT INTO dispatch_tickets (dispatch_id, ticket_id) VALUES (%s, %s)",
                (dispatch_id, ticket_id),
            )

        expected = _expected_by_product(conn, dispatch_id)
        return jsonify({
            "dispatch_id": dispatch_id,
            "status": "in_progress",
            "products": [{"product_id": pid, "expected": qty, "read": 0, "diff": -qty}
                         for pid, qty in expected.items()],
        }), 201

    @app.post("/api/dispatches/<int:dispatch_id>/reads")
    def add_reads(dispatch_id: int):
        body = request.get_json(force=True) or {}
        epcs = body.get("epcs") or []
        conn = get_conn()
        dispatch = _require_open_dispatch(conn, dispatch_id)

        for raw_epc in epcs:
            epc = normalize_epc(raw_epc)
            if not tiene_prefijo_valido(conn, epc):
                continue  # ajena al proyecto (ver pantalla Prefijos): se ignora por completo

            conn.execute("INSERT INTO reads (epc, source) VALUES (%s, %s)", (epc, f"dispatch:{dispatch_id}"))

            tag = conn.execute(
                "SELECT kind, status, product_id, truck_id FROM tags WHERE epc = %s", (epc,)
            ).fetchone()
            if tag is None:
                result, product_id = "unknown", None
            elif tag["kind"] == "truck":
                result, product_id = "other_truck", None
            elif tag["status"] == "dispatched":
                result, product_id = "already_dispatched", tag["product_id"]
            else:
                result, product_id = "counted", tag["product_id"]
                conn.execute("UPDATE tags SET last_read_at = now() WHERE epc = %s", (epc,))

            inserted = conn.execute(
                """
                INSERT INTO dispatch_reads (dispatch_id, epc, result, product_id)
                VALUES (%s, %s, %s, %s)
                ON CONFLICT (dispatch_id, epc) DO NOTHING
                RETURNING epc
                """,
                (dispatch_id, epc, result, product_id),
            ).fetchone()
            if inserted is not None and result in ALARM_READ_RESULTS:
                alarm_type = "unknown_tag" if result == "unknown" else "already_dispatched"
                conn.execute(
                    """
                    INSERT INTO alarms (dispatch_id, type, message, details)
                    VALUES (%s, %s, %s, %s)
                    """,
                    (dispatch_id, alarm_type, f"Etiqueta {epc}: {result}", Json({"epc": epc})),
                )

        return jsonify(_dispatch_status_payload(conn, dispatch))

    @app.get("/api/dispatches/<int:dispatch_id>/status")
    def dispatch_status(dispatch_id: int):
        conn = get_conn()
        dispatch = conn.execute("SELECT id, status FROM dispatches WHERE id = %s", (dispatch_id,)).fetchone()
        if dispatch is None:
            raise not_found(f"No existe la salida {dispatch_id}.")
        return jsonify(_dispatch_status_payload(conn, dispatch))

    @app.post("/api/dispatches/<int:dispatch_id>/reset")
    def reset_dispatch(dispatch_id: int):
        conn = get_conn()
        dispatch = _require_open_dispatch(conn, dispatch_id)
        conn.execute("DELETE FROM dispatch_reads WHERE dispatch_id = %s", (dispatch_id,))
        return jsonify(_dispatch_status_payload(conn, dispatch))

    @app.post("/api/dispatches/<int:dispatch_id>/finish")
    def finish_dispatch(dispatch_id: int):
        conn = get_conn()
        dispatch = _require_open_dispatch(conn, dispatch_id)
        expected, read_counts, problem_tags = _dispatch_counts(conn, dispatch_id)

        if is_exact_match(expected, read_counts, problem_tags):
            _close_dispatch(conn, dispatch_id, dispatch["truck_id"], status="completed")
            ya_despachadas = [t["epc"] for t in problem_tags if t["result"] == "already_dispatched"]
            return jsonify({
                "result": "ok", "dispatch_status": "completed",
                "ya_despachadas": ya_despachadas,
            })

        alarms = build_alarms(expected, read_counts, problem_tags)
        alarm_rows = []
        for alarm in alarms:
            if alarm["type"] not in ("missing", "excess"):
                continue
            row = conn.execute(
                """
                INSERT INTO alarms (dispatch_id, type, message, details)
                VALUES (%s, %s, %s, %s)
                RETURNING id, type
                """,
                (
                    dispatch_id, alarm["type"],
                    f"Producto {alarm['product_id']}: diferencia {alarm['diff']}",
                    Json({"product_id": alarm["product_id"], "diff": alarm["diff"]}),
                ),
            ).fetchone()
            alarm_rows.append({"id": row["id"], "type": row["type"], "product_id": alarm["product_id"], "diff": alarm["diff"]})
        return jsonify({"result": "mismatch", "dispatch_status": "in_progress", "alarms": alarm_rows})

    @app.post("/api/dispatches/<int:dispatch_id>/authorize")
    def authorize_dispatch(dispatch_id: int):
        body = request.get_json(force=True) or {}
        authorized_by = body.get("authorized_by")
        reason = body.get("reason")
        if not authorized_by or not reason:
            raise bad_request("Se requieren 'authorized_by' y 'reason'.")
        conn = get_conn()
        dispatch = _require_open_dispatch(conn, dispatch_id)
        _close_dispatch(
            conn, dispatch_id, dispatch["truck_id"],
            status="completed_with_difference",
            authorized_by=authorized_by, reason=reason,
        )
        return jsonify({"result": "ok", "dispatch_status": "completed_with_difference"})

    @app.post("/api/dispatches/<int:dispatch_id>/cancel")
    def cancel_dispatch(dispatch_id: int):
        conn = get_conn()
        _require_open_dispatch(conn, dispatch_id)
        conn.execute(
            "UPDATE dispatches SET status = 'cancelled', finished_at = now() WHERE id = %s",
            (dispatch_id,),
        )
        return jsonify({"result": "ok", "dispatch_status": "cancelled"})

    @app.post("/api/dispatches/<int:dispatch_id>/deliver")
    def deliver_dispatch(dispatch_id: int):
        """'Unidad en planta' (Windows, pantalla Salidas a ruta): no cambia
        'status' (eso ya distingue completed/completed_with_difference, el
        "tipo de salida"), solo marca 'delivered_at' y libera el camión."""
        conn = get_conn()
        dispatch = conn.execute(
            """
            UPDATE dispatches SET delivered_at = now()
            WHERE id = %s AND status IN ('completed', 'completed_with_difference')
                  AND delivered_at IS NULL
            RETURNING id, truck_id, status, delivered_at
            """,
            (dispatch_id,),
        ).fetchone()
        if dispatch is None:
            raise conflict(
                f"La salida {dispatch_id} no existe, no ha salido o ya fue entregada.",
                "dispatch_not_deliverable",
            )
        conn.execute(
            "UPDATE trucks SET status = 'available', status_changed_at = now() WHERE id = %s",
            (dispatch["truck_id"],),
        )
        return jsonify({
            "id": dispatch["id"], "status": dispatch["status"],
            "delivered_at": dispatch["delivered_at"].isoformat(),
        })

    # ------------------------------------------------------------ alarmas -
    @app.get("/api/alarms")
    def list_alarms():
        status = request.args.get("status", "open")
        clause = {
            "open": "WHERE a.acknowledged_at IS NULL",
            "ack": "WHERE a.acknowledged_at IS NOT NULL",
        }.get(status, "")
        rows = get_conn().execute(
            f"""
            SELECT a.id, a.dispatch_id, a.type, a.message, a.details, a.created_at,
                   a.acknowledged_at, a.acknowledged_by, t.unit_number AS truck_unit_number
            FROM alarms a
            LEFT JOIN dispatches d ON d.id = a.dispatch_id
            LEFT JOIN trucks t ON t.id = d.truck_id
            {clause}
            ORDER BY a.created_at DESC
            """
        ).fetchall()
        return jsonify(rows)

    @app.post("/api/alarms/<int:alarm_id>/ack")
    def ack_alarm(alarm_id: int):
        body = request.get_json(force=True) or {}
        acknowledged_by = body.get("acknowledged_by")
        if not acknowledged_by:
            raise bad_request("Se requiere 'acknowledged_by'.")
        conn = get_conn()
        row = conn.execute(
            """
            UPDATE alarms SET acknowledged_at = now(), acknowledged_by = %s
            WHERE id = %s RETURNING id, type, acknowledged_at, acknowledged_by
            """,
            (acknowledged_by, alarm_id),
        ).fetchone()
        if row is None:
            raise not_found(f"No existe la alarma {alarm_id}.")
        return jsonify(row)

    # ------------------------------------------------------- prefijos EPC -
    @app.get("/api/epc-prefixes")
    def list_epc_prefixes():
        rows = get_conn().execute(
            "SELECT id, prefix, created_at FROM epc_prefixes ORDER BY prefix"
        ).fetchall()
        return jsonify(rows)

    @app.post("/api/epc-prefixes")
    def create_epc_prefix():
        body = request.get_json(force=True) or {}
        prefix = normalize_epc(body.get("prefix", ""))
        row = get_conn().execute(
            """
            INSERT INTO epc_prefixes (prefix) VALUES (%s)
            ON CONFLICT (prefix) DO NOTHING
            RETURNING id, prefix, created_at
            """,
            (prefix,),
        ).fetchone()
        if row is None:
            raise conflict(f"Ya existe el prefijo '{prefix}'.", "prefix_exists")
        return jsonify(row), 201

    @app.delete("/api/epc-prefixes/<int:prefix_id>")
    def delete_epc_prefix(prefix_id: int):
        conn = get_conn()
        row = conn.execute("SELECT id FROM epc_prefixes WHERE id = %s", (prefix_id,)).fetchone()
        if row is None:
            raise not_found(f"No existe el prefijo {prefix_id}.")
        conn.execute("DELETE FROM epc_prefixes WHERE id = %s", (prefix_id,))
        return "", 204

    return app


# ------------------------------------------------------------- helpers ----

def _fetch_tag_view(conn, epc: str) -> dict | None:
    tag = conn.execute(
        "SELECT epc, kind, folio, product_id, truck_id, status, captured_at FROM tags WHERE epc = %s",
        (epc,),
    ).fetchone()
    if tag is None:
        return None
    if tag["kind"] == "pallet":
        product = conn.execute(
            "SELECT id, name FROM products WHERE id = %s", (tag["product_id"],)
        ).fetchone()
        return {
            "epc": tag["epc"], "kind": "pallet", "folio": tag["folio"],
            "product": product, "status": tag["status"],
            "production_date": tag["captured_at"].isoformat(),
        }
    return {
        "epc": tag["epc"], "kind": "truck", "truck_id": tag["truck_id"],
    }


def _expected_by_product(conn, dispatch_id: int) -> dict[int, int]:
    rows = conn.execute(
        """
        SELECT l.product_id, SUM(l.pallets) AS expected
        FROM dispatch_tickets dt
        JOIN exit_ticket_lines l ON l.ticket_id = dt.ticket_id
        WHERE dt.dispatch_id = %s
        GROUP BY l.product_id
        """,
        (dispatch_id,),
    ).fetchall()
    return {row["product_id"]: row["expected"] for row in rows}


def _dispatch_counts(conn, dispatch_id: int):
    expected = _expected_by_product(conn, dispatch_id)
    read_rows = conn.execute(
        """
        SELECT product_id, COUNT(*) AS read
        FROM dispatch_reads
        WHERE dispatch_id = %s AND result = 'counted'
        GROUP BY product_id
        """,
        (dispatch_id,),
    ).fetchall()
    read_counts = {row["product_id"]: row["read"] for row in read_rows}
    problem_tags = conn.execute(
        """
        SELECT epc, result FROM dispatch_reads
        WHERE dispatch_id = %s AND result IN ('unknown', 'already_dispatched')
        """,
        (dispatch_id,),
    ).fetchall()
    return expected, read_counts, problem_tags


def _dispatch_status_payload(conn, dispatch: dict) -> dict:
    expected, read_counts, problem_tags = _dispatch_counts(conn, dispatch["id"])
    diffs = product_diffs(expected, read_counts)
    product_ids = [d["product_id"] for d in diffs]
    names = {}
    if product_ids:
        rows = conn.execute(
            "SELECT id, name FROM products WHERE id = ANY(%s)", (product_ids,)
        ).fetchall()
        names = {row["id"]: row["name"] for row in rows}
    products = [{**d, "name": names.get(d["product_id"], "?")} for d in diffs]
    total_read = sum(read_counts.values())
    return {
        "dispatch_id": dispatch["id"],
        "status": dispatch["status"],
        "products": products,
        "problem_tags": problem_tags,
        "total_read": total_read,
    }


def _require_open_dispatch(conn, dispatch_id: int) -> dict:
    dispatch = conn.execute(
        "SELECT id, truck_id, status FROM dispatches WHERE id = %s", (dispatch_id,)
    ).fetchone()
    if dispatch is None:
        raise not_found(f"No existe la salida {dispatch_id}.")
    if dispatch["status"] != "in_progress":
        raise conflict(f"La salida {dispatch_id} ya no está en proceso.", "dispatch_not_in_progress")
    return dispatch


def _close_dispatch(conn, dispatch_id: int, truck_id: int, status: str,
                     authorized_by: str | None = None, reason: str | None = None) -> None:
    conn.execute(
        """
        UPDATE dispatches
        SET status = %s, finished_at = now(), authorized_by = %s, authorization_reason = %s
        WHERE id = %s
        """,
        (status, authorized_by, reason, dispatch_id),
    )
    conn.execute(
        """
        UPDATE exit_tickets SET status = 'dispatched', dispatched_at = now()
        WHERE id IN (SELECT ticket_id FROM dispatch_tickets WHERE dispatch_id = %s)
        """,
        (dispatch_id,),
    )
    conn.execute(
        """
        UPDATE tags SET status = 'dispatched'
        WHERE epc IN (
            SELECT epc FROM dispatch_reads WHERE dispatch_id = %s AND result = 'counted'
        )
        """,
        (dispatch_id,),
    )
    conn.execute(
        "UPDATE trucks SET status = 'en_route', status_changed_at = now() WHERE id = %s",
        (truck_id,),
    )
