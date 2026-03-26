# StakeBot — Telegram-бот для мониторинга крупных ставок

Бот отслеживает крупные ставки на киберспорт (CS2, Dota 2) в реальном времени и отправляет уведомления подписчикам в Telegram.

## Возможности

- Мониторинг крупных ставок через WebSocket в реальном времени
- Уведомления в Telegram с фильтрацией по играм (CS2 / Dota 2 / всё)
- Система платных подписок с админ-управлением
- Inline-кнопки для удобного взаимодействия
- Поддержка SOCKS5/HTTP прокси с ротацией IP
- PostgreSQL для хранения пользователей
- Docker-деплой одной командой

## Стек

- Java 17
- Telegram Bots API
- OkHttp (WebSocket)
- PostgreSQL + HikariCP
- Conscrypt (TLS)
- Docker + Docker Compose

## Быстрый старт (Docker)

```bash
# 1. Клонировать
git clone https://github.com/your-username/betParser2.git
cd betParser2

# 2. Настроить окружение
cp .env.example .env
nano .env  # заполнить BOT_TOKEN

# 3. Настроить прокси
nano demo/proxies.txt
# Формат: socks5://user:pass@host:port

# 4. Запустить
docker compose up -d

# Логи
docker compose logs -f bot
```

## Локальный запуск (без Docker)

1. Запустить PostgreSQL (порт 5433):
```bash
docker run -d --name stakebot-db \
  -e POSTGRES_USER=stakebot \
  -e POSTGRES_PASSWORD=stakebot123 \
  -e POSTGRES_DB=stakebot \
  -p 5433:5432 postgres:16
```

2. Заполнить `demo/proxies.txt`

3. Собрать и запустить через IDE или Maven:
```bash
cd demo
mvn package
java -jar target/stake-bot-1.0.jar
```

## Переменные окружения

| Переменная | По умолчанию | Описание |
|------------|-------------|----------|
| `BOT_TOKEN` | — | Токен Telegram-бота (обязательно) |
| `BOT_USERNAME` | `WarningBets_bot` | Username бота |
| `DB_URL` | `jdbc:postgresql://localhost:5433/stakebot` | URL базы данных |
| `DB_USER` | `stakebot` | Пользователь БД |
| `DB_PASSWORD` | `stakebot123` | Пароль БД |

## Команды бота

**Пользователи:**
- `/start` — главное меню с кнопками
- Кнопка «Подписаться» → выбор игры (CS2 / Dota 2 / Всё)
- Кнопка «Мой статус» → информация о подписке

**Админы:**
- `/grant <chat_id>` — выдать подписку
- `/revoke <chat_id>` — отозвать подписку
- `/status <chat_id>` — статус пользователя
- `/users` — список всех пользователей

## Формат прокси (proxies.txt)

```
socks5://user:pass@host:port
http://user:pass@host:port
```
