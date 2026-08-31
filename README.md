# StakeBot

Telegram-бот, который в реальном времени отслеживает крупные ставки на киберспорт (CS2, Dota 2)
и рассылает уведомления подписчикам.

## Как это работает

1. `StakeWebSocket` держит постоянное GraphQL-over-WebSocket соединение с источником данных
   и переподключается при обрыве: перебирает домены, ротирует прокси, раз в 30 минут
   принудительно переустанавливает соединение для смены IP.
2. `BetService` разбирает входящий JSON, отфильтровывает экспрессы и неподдерживаемые виды спорта,
   отсекает ставки ниже порога и формирует текст уведомления.
3. `TelegramBot` рассылает уведомление тем подписчикам, кто выбрал соответствующую игру.
4. `Database` (PostgreSQL + HikariCP) хранит пользователей, выбранный спорт и статус подписки.

## Стек

Java 17, Telegram Bots API, OkHttp (WebSocket), Gson, PostgreSQL + HikariCP,
Conscrypt (TLS), SLF4J, Maven, Docker Compose.

## Конфигурация

Все настройки читаются из переменных окружения, значений по умолчанию нет —
при отсутствии обязательной переменной приложение падает на старте.

| Переменная | Обязательна | Описание |
|---|---|---|
| `BOT_TOKEN` | да | Токен Telegram-бота |
| `BOT_USERNAME` | да | Username бота |
| `ADMIN_IDS` | нет | Chat id администраторов через запятую |
| `DB_URL` | да | JDBC URL PostgreSQL |
| `DB_USER` | да | Пользователь БД |
| `DB_PASSWORD` | да | Пароль БД |
| `PROXY_FILE` | нет | Путь к файлу со списком прокси |

## Запуск в Docker

```bash
git clone https://github.com/faygodream/betParser.git
cd betParser
cp .env.example .env
# заполнить .env своими значениями
docker compose up -d
docker compose logs -f bot
```

## Локальный запуск

```bash
docker run -d --name stakebot-db \
  -e POSTGRES_USER=stakebot -e POSTGRES_PASSWORD=<пароль> -e POSTGRES_DB=stakebot \
  -p 5433:5432 postgres:16

cd demo
mvn package
java -jar target/stake-bot-1.0.jar
```

## Команды бота

Пользователь: `/start` и дальше inline-кнопки — подписка, выбор игры (CS2 / Dota 2 / всё),
статус подписки, отписка.

Администратор: `/grant <chat_id>`, `/revoke <chat_id>`, `/status <chat_id>`, `/users`.

## Прокси

Файл со списком прокси, по одному в строке:

```
socks5://host:port
socks5://user:pass@host:port
http://user:pass@host:port
```

Файл содержит учётные данные и в репозиторий не коммитится.
Если он пуст или отсутствует, используется прямое подключение.
