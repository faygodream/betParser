package com.example.bot;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TelegramBot extends TelegramLongPollingBot {

    // chatId -> выбранный sport slug
    private final Map<Long, String> subscribers = new ConcurrentHashMap<>();
    private final int startTime = (int) (System.currentTimeMillis() / 1000);

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

        // Игнорируем сообщения, отправленные до запуска бота
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
                subscribers.put(chatId, "dota-2");
                send(chatId, "✅ Ты подписан на крупные ставки Dota 2!");
                System.out.println("Подписчик " + chatId + " → dota-2");
                break;

            case "/cs":
                subscribers.put(chatId, "counter-strike");
                send(chatId, "✅ Ты подписан на крупные ставки CS2!");
                System.out.println("Подписчик " + chatId + " → counter-strike");
                break;

            case "/all":
                subscribers.put(chatId, "all");
                send(chatId, "✅ Ты подписан на крупные ставки Dota 2 и CS2!");
                System.out.println("Подписчик " + chatId + " → all");
                break;

            case "/stop":
                subscribers.remove(chatId);
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
        for (Map.Entry<Long, String> entry : subscribers.entrySet()) {
            String sub = entry.getValue();
            if ("all".equals(sub) || sportSlug.equals(sub)) {
                send(entry.getKey(), text);
            }
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
