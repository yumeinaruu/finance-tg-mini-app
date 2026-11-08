# Finance bot

Личный трекер финансов: Telegram-бот на Kotlin + Ktor, SQLite. Спецификация — [docs/spec.md](docs/spec.md).

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

## Деплой

Сборка идёт локально, на сервер уходит готовый Docker-образ (Gradle не влезет в память VPS).

Один раз на сервере:

```bash
mkdir -p ~/finance && cd ~/finance
cat > .env <<'EOF'
BOT_TOKEN=...
OWNER_ID=...
EOF
```

Каждый деплой с локальной машины (нужен запущенный Docker):

```bash
DEPLOY_HOST=user@server ./deploy.sh
```

Приложение ограничено 320 МБ (`mem_limit`), JVM с `-Xmx160m`.

## Тесты

```bash
./gradlew test
```
