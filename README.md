# Finance bot

Личный трекер финансов: Telegram-бот на Kotlin + Ktor, SQLite, и Mini App на React (`webapp/`). Спецификация — [docs/spec.md](docs/spec.md).

## Локальный запуск

1. Создай бота у [@BotFather](https://t.me/BotFather) и скопируй токен.
2. Узнай свой Telegram ID (например, у [@userinfobot](https://t.me/userinfobot)).
3. Запусти:

```bash
BOT_TOKEN=... OWNER_ID=... ./gradlew run
```

База создаётся в `data/finance.db`.

## Переменные окружения

| Переменная | По умолчанию | Описание |
|---|---|---|
| `BOT_TOKEN` | — | токен бота |
| `OWNER_ID` | — | Telegram ID владельца, остальным бот не отвечает |
| `DB_PATH` | `data/finance.db` | путь к SQLite |
| `TIMEZONE` | `Europe/Minsk` | часовой пояс для «сегодня» и отчётов |
| `HTTP_PORT` | `8080` | порт для `/health` (позже — API Mini App) |
| `NOTIFY_TIME` | `10:00` | когда добавляются регулярные платежи и приходит сводка 1-го числа |
| `DOMAIN` | — | домен Mini App, Caddy выпускает для него сертификат |
| `MINI_APP_URL` | — | публичный адрес Mini App; если задан, бот ставит кнопку меню и отвечает на `/app` |

## Деплой

Jar собирается локально (Gradle не влезет в память VPS), на сервере из него собирается тонкий образ. Docker локально не нужен.

Один раз на сервере:

```bash
mkdir -p /opt/finance && cd /opt/finance
cat > .env <<'EOF'
BOT_TOKEN=...
OWNER_ID=...
EOF
```

Каждый деплой с локальной машины:

```bash
DEPLOY_HOST=vpn ./deploy.sh
```

Приложение ограничено 280 МБ (`mem_limit`), JVM с `-Xmx128m`.

## Mini App

Фронтенд в `webapp/` (React + Vite, графики на SVG). Запросы идут в `/api` с заголовком `Authorization: tma <initData>`; сервер проверяет подпись Telegram и пускает только `OWNER_ID`.

Локальная разработка:

```bash
BOT_TOKEN=000:fake OWNER_ID=42 ./gradlew run          # API на :8080
cd webapp && VITE_INIT_DATA='<подписанный initData>' npm run dev
```

На сервере Caddy отдаёт статику из `webapp/` и проксирует `/api` в бота, сертификат Let's Encrypt выпускается сам.

## Бэкапы

Раз в неделю (в `NOTIFY_TIME`) бот присылает в Telegram файл `finance-YYYY-MM-DD.db` — полную копию базы. По запросу — командой `/backup`.

Восстановление на сервере:

```bash
scp finance-2026-11-15.db vpn:/opt/finance/finance-restore.db
ssh vpn 'cd /opt/finance && docker compose stop \
  && rm -f data/finance.db-wal data/finance.db-shm \
  && mv finance-restore.db data/finance.db \
  && docker compose start'
```

## Тесты

```bash
./gradlew test
```
