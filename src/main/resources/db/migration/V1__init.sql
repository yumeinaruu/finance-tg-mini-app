CREATE TABLE category (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL,
    emoji      TEXT    NOT NULL,
    type       TEXT    NOT NULL CHECK (type IN ('EXPENSE', 'INCOME')),
    archived   INTEGER NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0
);

-- alias is stored normalized: lowercase, 'ё' replaced with 'е'
CREATE TABLE category_alias (
    alias       TEXT PRIMARY KEY,
    category_id INTEGER NOT NULL REFERENCES category (id)
);

-- amounts are stored in minor units (kopecks/cents); rate is BYN per 1 unit of currency
CREATE TABLE operation (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    category_id       INTEGER NOT NULL REFERENCES category (id),
    amount_minor      INTEGER NOT NULL CHECK (amount_minor > 0),
    currency          TEXT    NOT NULL,
    rate              TEXT    NOT NULL,
    amount_base_minor INTEGER NOT NULL,
    occurred_on       TEXT    NOT NULL,
    note              TEXT,
    created_at        TEXT    NOT NULL
);

CREATE INDEX ix_operation_occurred_on ON operation (occurred_on);

-- operations waiting for the user to pick a category
CREATE TABLE pending_operation (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    type         TEXT    NOT NULL,
    amount_minor INTEGER NOT NULL,
    currency     TEXT    NOT NULL,
    occurred_on  TEXT    NOT NULL,
    note         TEXT,
    created_at   TEXT    NOT NULL
);

CREATE TABLE fx_rate (
    date     TEXT NOT NULL,
    currency TEXT NOT NULL,
    rate     TEXT NOT NULL,
    source   TEXT NOT NULL,
    PRIMARY KEY (date, currency)
);

INSERT INTO category (name, emoji, type, sort_order)
VALUES ('Еда', '🍔', 'EXPENSE', 1),
       ('Транспорт', '🚕', 'EXPENSE', 2),
       ('Жильё', '🏠', 'EXPENSE', 3),
       ('Подписки и связь', '📱', 'EXPENSE', 4),
       ('Здоровье', '💊', 'EXPENSE', 5),
       ('Покупки', '🛍', 'EXPENSE', 6),
       ('Развлечения', '🎉', 'EXPENSE', 7),
       ('Подарки', '🎁', 'EXPENSE', 8),
       ('Прочее', '📦', 'EXPENSE', 9),
       ('Зарплата', '💰', 'INCOME', 1),
       ('Прочий доход', '➕', 'INCOME', 2);

WITH a(alias, name) AS (
    VALUES ('кофе', 'Еда'), ('кафе', 'Еда'), ('обед', 'Еда'), ('ужин', 'Еда'), ('завтрак', 'Еда'),
           ('продукты', 'Еда'), ('магазин', 'Еда'), ('шаурма', 'Еда'), ('пицца', 'Еда'),
           ('доставка', 'Еда'), ('ресторан', 'Еда'), ('перекус', 'Еда'),
           ('такси', 'Транспорт'), ('метро', 'Транспорт'), ('автобус', 'Транспорт'),
           ('бензин', 'Транспорт'), ('проезд', 'Транспорт'), ('парковка', 'Транспорт'),
           ('аренда', 'Жильё'), ('квартира', 'Жильё'), ('коммуналка', 'Жильё'), ('жкх', 'Жильё'),
           ('подписка', 'Подписки и связь'), ('интернет', 'Подписки и связь'), ('связь', 'Подписки и связь'),
           ('телефон', 'Подписки и связь'), ('netflix', 'Подписки и связь'), ('spotify', 'Подписки и связь'),
           ('youtube', 'Подписки и связь'),
           ('аптека', 'Здоровье'), ('врач', 'Здоровье'), ('лекарства', 'Здоровье'), ('стоматолог', 'Здоровье'),
           ('анализы', 'Здоровье'),
           ('одежда', 'Покупки'), ('обувь', 'Покупки'), ('техника', 'Покупки'), ('озон', 'Покупки'),
           ('wildberries', 'Покупки'),
           ('кино', 'Развлечения'), ('бар', 'Развлечения'), ('концерт', 'Развлечения'), ('игры', 'Развлечения'),
           ('театр', 'Развлечения'),
           ('подарок', 'Подарки'), ('цветы', 'Подарки'),
           ('другое', 'Прочее'),
           ('зп', 'Зарплата'), ('аванс', 'Зарплата'), ('премия', 'Зарплата'),
           ('фриланс', 'Прочий доход'), ('кэшбэк', 'Прочий доход'), ('кешбэк', 'Прочий доход'),
           ('возврат', 'Прочий доход'), ('доход', 'Прочий доход')
)
INSERT INTO category_alias (alias, category_id)
SELECT a.alias, c.id
FROM a JOIN category c ON c.name = a.name;
