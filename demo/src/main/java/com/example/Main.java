package com.example;

import com.example.bot.TelegramBot;
import com.example.service.BetService;
import com.example.ws.StakeWebSocket;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {

    public static void main(String[] args) throws Exception {

        TelegramBot bot = new TelegramBot();

        // Сбрасываем старые сообщения, накопившиеся пока бот был выключен
        DeleteWebhook deleteWebhook = new DeleteWebhook();
        deleteWebhook.setDropPendingUpdates(true);
        bot.execute(deleteWebhook);
        System.out.println("Старые сообщения очищены");

        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(bot);

        BetService service = new BetService(bot);

        StakeWebSocket ws = new StakeWebSocket(service);
        ws.connect();

        System.out.println("🚀 Бот запущен и слушает WebSocket...");

        // Держим основной поток живым
        Thread.currentThread().join();
    }
}
