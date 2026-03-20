package com.example;

import com.example.bot.TelegramBot;
import com.example.db.Database;
import com.example.service.BetService;
import com.example.ws.ProxyManager;
import com.example.ws.StakeWebSocket;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {

    public static void main(String[] args) throws Exception {

        // PostgreSQL — настройки через переменные окружения
        String dbUrl = env("DB_URL", "jdbc:postgresql://localhost:5433/stakebot");
        String dbUser = env("DB_USER", "stakebot");
        String dbPassword = env("DB_PASSWORD", "stakebot123");

        Database database = new Database(dbUrl, dbUser, dbPassword);

        TelegramBot bot = new TelegramBot(database);

        // Сбрасываем старые сообщения, накопившиеся пока бот был выключен
        DeleteWebhook deleteWebhook = new DeleteWebhook();
        deleteWebhook.setDropPendingUpdates(true);
        bot.execute(deleteWebhook);
        System.out.println("Старые сообщения очищены");

        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(bot);

        BetService service = new BetService(bot);

        // Загружаем прокси из файла proxies.txt
        String proxyFile = System.getProperty("proxy.file");
        if (proxyFile == null) {
            java.io.File f = new java.io.File("proxies.txt");
            if (!f.exists()) {
                f = new java.io.File("demo/proxies.txt");
            }
            proxyFile = f.getAbsolutePath();
        }
        System.out.println("Путь к proxies.txt: " + proxyFile);
        ProxyManager proxyManager = new ProxyManager(proxyFile);

        StakeWebSocket ws = new StakeWebSocket(service, proxyManager);
        ws.connect();

        System.out.println("🚀 Бот запущен и слушает WebSocket...");

        Thread.currentThread().join();
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}
