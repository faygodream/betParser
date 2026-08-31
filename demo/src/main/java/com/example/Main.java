package com.example;

import com.example.bot.TelegramBot;
import com.example.db.Database;
import com.example.service.BetService;
import com.example.ws.ProxyManager;
import com.example.ws.StakeWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {

        String dbUrl = required("DB_URL");
        String dbUser = required("DB_USER");
        String dbPassword = required("DB_PASSWORD");
        String botUsername = required("BOT_USERNAME");
        String botToken = required("BOT_TOKEN");
        Set<Long> admins = parseAdmins(System.getenv("ADMIN_IDS"));

        Database database = new Database(dbUrl, dbUser, dbPassword);
        TelegramBot bot = new TelegramBot(database, botUsername, botToken, admins);

        // Сбрасываем апдейты, накопившиеся пока бот был выключен
        try {
            DeleteWebhook deleteWebhook = new DeleteWebhook();
            deleteWebhook.setDropPendingUpdates(true);
            bot.execute(deleteWebhook);
            log.info("Очередь необработанных апдейтов сброшена");
        } catch (Exception e) {
            log.warn("Не удалось сбросить очередь апдейтов: {}", e.getMessage());
        }

        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(bot);
        bot.notifyAdminsOnStart();

        BetService service = new BetService(bot);
        ProxyManager proxyManager = new ProxyManager(resolveProxyFile());

        StakeWebSocket ws = new StakeWebSocket(service, proxyManager);
        ws.connect();

        log.info("Бот запущен");

        Thread.currentThread().join();
    }

    private static String resolveProxyFile() {
        String configured = System.getenv("PROXY_FILE");
        if (configured == null) {
            configured = System.getProperty("proxy.file");
        }
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        File file = new File("proxies.txt");
        if (!file.exists()) {
            file = new File("demo/proxies.txt");
        }
        return file.getAbsolutePath();
    }

    private static Set<Long> parseAdmins(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Не задана переменная окружения " + name);
        }
        return value;
    }
}
