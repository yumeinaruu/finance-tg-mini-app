-- month is stored as yyyy-MM; limits are in base currency minor units
CREATE TABLE budget (
    category_id INTEGER NOT NULL REFERENCES category (id),
    month       TEXT    NOT NULL,
    limit_minor INTEGER NOT NULL CHECK (limit_minor > 0),
    PRIMARY KEY (category_id, month)
);

-- each warning threshold is sent once per category and month
CREATE TABLE budget_alert (
    category_id INTEGER NOT NULL REFERENCES category (id),
    month       TEXT    NOT NULL,
    threshold   INTEGER NOT NULL,
    PRIMARY KEY (category_id, month, threshold)
);

-- schedule: monthly:<day> | weekly:<iso day of week> | yearly:<month>-<day>
CREATE TABLE recurring (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT    NOT NULL,
    category_id  INTEGER NOT NULL REFERENCES category (id),
    amount_minor INTEGER NOT NULL CHECK (amount_minor > 0),
    currency     TEXT    NOT NULL,
    schedule     TEXT    NOT NULL,
    next_run     TEXT    NOT NULL
);

ALTER TABLE operation ADD COLUMN recurring_id INTEGER REFERENCES recurring (id) ON DELETE SET NULL;

CREATE TABLE app_state (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
