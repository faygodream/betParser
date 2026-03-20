package com.example.bot;

import com.example.db.Database;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

public class TelegramBot extends TelegramLongPollingBot {

    private final Database database;
    private final int startTime = (int) (System.currentTimeMillis() / 1000);

    public TelegramBot(Database database) {
        this.database = database;
    }

    @Override
    public String getBotUsername() {
        return "WarningBets_bot";
    }

    @Override
    public String getBotToken() {
        return "REDACTED_BOT_TOKEN";
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        int messageTime = update.getMessage().getDate();
        if (messageTime < startTime) {
            System.out.println("Пропущено старое сообщение: " + update.getMessage().getText());
            return;
        }

        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim().toLowerCase();
        System.out.println("Получено от " + chatId + ": [" + text + "]");

        switch (text) {
            case "/start":
                send(chatId, "👋 Привет! Выбери игру для отслеживания крупных ставок:\n\n" +
                        "/dota — Dota 2\n" +
                        "/cs — Counter-Strike 2\n" +
                        "/all — Обе игры\n" +
                        "/stop — Отписаться");
                break;

            case "/dota":
                database.subscribe(chatId, "dota-2");
                send(chatId, "✅ Ты подписан на крупные ставки Dota 2!");
                System.out.println("Подписчик " + chatId + " → dota-2");
                break;

            case "/cs":
                database.subscribe(chatId, "counter-strike");
                send(chatId, "✅ Ты подписан на крупные ставки CS2!");
                System.out.println("Подписчик " + chatId + " → counter-strike");
                break;

            case "/all":
                database.subscribe(chatId, "all");
                send(chatId, "✅ Ты подписан на крупные ставки Dota 2 и CS2!");
                System.out.println("Подписчик " + chatId + " → all");
                break;

            case "/stop":
                database.unsubscribe(chatId);
                send(chatId, "❌ Ты отписан от уведомлений.");
                System.out.println("Отписался: " + chatId);
                break;

            default:
                send(chatId, "Используй команды:\n/dota — Dota 2\n/cs — CS2\n/all — Обе\n/stop — Отписаться");
                break;
        }
    }

    /**
     * Отправляет сообщение только подписчикам, которые выбрали этот спорт.
     */
    public void broadcastForSport(String sportSlug, String text) {
        List<Long> chatIds = database.getSubscribers(sportSlug);
        for (Long chatId : chatIds) {
            send(chatId, text);
        }
    }

    private void send(Long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text);

        try {
            execute(msg);
        } catch (TelegramApiException e) {
            System.err.println("Ошибка отправки в Telegram (chatId=" + chatId + "): " + e.getMessage());
        }
    }
}
