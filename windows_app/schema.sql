-- Esquema PostgreSQL — RFID_Reader (demo)
-- Ver docs/modelo_datos.md. Se ejecuta al arrancar la app (CREATE ... IF NOT EXISTS).

CREATE TABLE IF NOT EXISTS products (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code         TEXT NOT NULL UNIQUE,
    name         TEXT NOT NULL,
    presentation TEXT,
    active       BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS trucks (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    unit_number       TEXT NOT NULL UNIQUE,
    plate             TEXT,
    driver            TEXT,
    status            TEXT NOT NULL DEFAULT 'available'
                      CHECK (status IN ('available', 'en_route')),
    status_changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- consecutivo para el folio de los pallets
CREATE SEQUENCE IF NOT EXISTS pallet_folio_seq;

CREATE TABLE IF NOT EXISTS tags (
    epc          VARCHAR(48) PRIMARY KEY,
    kind         TEXT NOT NULL CHECK (kind IN ('pallet', 'truck')),
    folio        TEXT UNIQUE,
    product_id   BIGINT REFERENCES products(id),
    truck_id     BIGINT REFERENCES trucks(id),
    status       TEXT NOT NULL DEFAULT 'captured'
                 CHECK (status IN ('captured', 'dispatched')),
    captured_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_read_at TIMESTAMPTZ,
    CHECK (
        (kind = 'pallet' AND product_id IS NOT NULL AND truck_id IS NULL AND folio IS NOT NULL) OR
        (kind = 'truck'  AND truck_id  IS NOT NULL AND product_id IS NULL AND folio IS NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS one_tag_per_truck
    ON tags (truck_id) WHERE kind = 'truck';
CREATE INDEX IF NOT EXISTS idx_tags_product_status ON tags (product_id, status);

CREATE TABLE IF NOT EXISTS exit_tickets (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    folio         TEXT NOT NULL UNIQUE,
    customer      TEXT NOT NULL,
    truck_id      BIGINT REFERENCES trucks(id),
    status        TEXT NOT NULL DEFAULT 'active'
                  CHECK (status IN ('active', 'dispatched', 'cancelled')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    dispatched_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS exit_ticket_lines (
    ticket_id  BIGINT NOT NULL REFERENCES exit_tickets(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    pallets    INTEGER NOT NULL CHECK (pallets > 0),
    PRIMARY KEY (ticket_id, product_id)
);

CREATE TABLE IF NOT EXISTS dispatches (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    truck_id             BIGINT NOT NULL REFERENCES trucks(id),
    status               TEXT NOT NULL DEFAULT 'in_progress'
                         CHECK (status IN ('in_progress', 'completed',
                                           'completed_with_difference', 'cancelled')),
    started_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at          TIMESTAMPTZ,
    authorized_by        TEXT,
    authorization_reason TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS one_open_dispatch_per_truck
    ON dispatches (truck_id) WHERE status = 'in_progress';

CREATE TABLE IF NOT EXISTS dispatch_tickets (
    dispatch_id BIGINT NOT NULL REFERENCES dispatches(id) ON DELETE CASCADE,
    ticket_id   BIGINT NOT NULL REFERENCES exit_tickets(id),
    PRIMARY KEY (dispatch_id, ticket_id)
);

CREATE TABLE IF NOT EXISTS dispatch_reads (
    dispatch_id   BIGINT NOT NULL REFERENCES dispatches(id) ON DELETE CASCADE,
    epc           VARCHAR(48) NOT NULL,
    result        TEXT NOT NULL
                  CHECK (result IN ('counted', 'unknown', 'already_dispatched', 'other_truck')),
    product_id    BIGINT REFERENCES products(id),
    first_read_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (dispatch_id, epc)
);

CREATE TABLE IF NOT EXISTS alarms (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dispatch_id     BIGINT REFERENCES dispatches(id),
    type            TEXT NOT NULL
                    CHECK (type IN ('missing', 'excess', 'unknown_tag', 'already_dispatched',
                                    'no_active_tickets', 'truck_not_available')),
    message         TEXT NOT NULL,
    details         JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    acknowledged_at TIMESTAMPTZ,
    acknowledged_by TEXT
);
CREATE INDEX IF NOT EXISTS idx_alarms_open ON alarms (created_at) WHERE acknowledged_at IS NULL;

CREATE TABLE IF NOT EXISTS reads (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    epc     VARCHAR(48) NOT NULL,
    read_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    source  TEXT
);
CREATE INDEX IF NOT EXISTS idx_reads_epc_time ON reads (epc, read_at DESC);
