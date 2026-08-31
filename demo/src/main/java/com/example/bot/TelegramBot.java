package com.example.bot;

import com.example.db.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TelegramBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(TelegramBot.class);

    private final Database database;
    private final String botUsername;
    private final String botToken;
    private final Set<Long> admins;
    private final int startTime = (int) (System.currentTimeMillis() / 1000);

    public TelegramBot(Database database, String botUsername, String botToken, Set<Long> admins) {
        this.database = database;
        this.botUsername = botUsername;
        this.botToken = botToken;
        this.admins = Set.copyOf(admins);
    }

    public void notifyAdminsOnStart() {
        for (Long adminId : admins) {
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            rows.add(List.of(btn("📋 Пользователи", "admin_users")));
            keyboard.setKeyboard(rows);

            sendWithKeyboard(adminId,
                    "🟢 Бот запущен, Вы — Админ\n\n" +
                    "👑 Админ-команды:\n" +
                    "/grant <id> — выдать подписку\n" +
                    "/revoke <id> — отозвать подписку\n" +
                    "/status <id> — статус пользователя",
                    keyboard);
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Обработка нажатий на кнопки
        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
            return;
        }

        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        int messageTime = update.getMessage().getDate();
        if (messageTime < startTime) return;

        Long chatId = update.getMessage().getChatId();
        String username = update.getMessage().getFrom().getUserName();
        String text = update.getMessage().getText().trim().toLowerCase();
        log.info("Сообщение от {}: [{}]", chatId, text);

        // Админские текстовые команды (grant/revoke/status требуют аргументов)
        if (admins.contains(chatId) && handleAdminCommand(chatId, text)) {
            return;
        }

        if (text.equals("/start")) {
            database.registerUser(chatId, username);
            showMainMenu(chatId);
        } else {
            send(chatId, "Нажми /start для начала работы.");
        }
    }

    private void handleCallback(CallbackQuery callback) {
        Long chatId = callback.getMessage().getChatId();
        String username = callback.getFrom().getUserName();
        String data = callback.getData();
        int messageId = callback.getMessage().getMessageId();

        log.info("Callback от {}: {}", chatId, data);

        // Подтверждаем нажатие кнопки
        answerCallback(callback.getId());

        switch (data) {
            case "subscribe" -> {
                database.registerUser(chatId, username);
                if (!database.isActive(chatId)) {
                    editMessage(chatId, messageId,
                            "⚠️ У тебя нет активной подписки.\n" +
                            "Оформи подписку, чтобы получать уведомления о крупных ставках.");
                    return;
                }
                // Активная подписка — показываем выбор спорта
                InlineKeyboardMarkup sportKb = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> sportRows = new ArrayList<>();
                sportRows.add(List.of(btn("🎮 CS2", "sport_cs"), btn("🧙 Dota 2", "sport_dota")));
                sportRows.add(List.of(btn("🎮🧙 Всё", "sport_all")));
                sportRows.add(List.of(btn("🔙 Назад", "back_menu")));
                sportKb.setKeyboard(sportRows);
                editMessageWithKeyboard(chatId, messageId, "Выбери игру для отслеживания:", sportKb);
            }
            case "sport_cs" -> {
                database.subscribe(chatId, username, "counter-strike");
                InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
                kb.setKeyboard(List.of(List.of(btn("🔙 Меню", "back_menu"))));
                editMessageWithKeyboard(chatId, messageId, "✅ Ты подписан на крупные ставки CS2!", kb);
                log.info("Подписка {} на counter-strike", chatId);
            }
            case "sport_dota" -> {
                database.subscribe(chatId, username, "dota-2");
                InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
                kb.setKeyboard(List.of(List.of(btn("🔙 Меню", "back_menu"))));
                editMessageWithKeyboard(chatId, messageId, "✅ Ты подписан на крупные ставки Dota 2!", kb);
                log.info("Подписка {} на dota-2", chatId);
            }
            case "sport_all" -> {
                database.subscribe(chatId, username, "all");
                InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
                kb.setKeyboard(List.of(List.of(btn("🔙 Меню", "back_menu"))));
                editMessageWithKeyboard(chatId, messageId, "✅ Ты подписан на крупные ставки CS2 и Dota 2!", kb);
                log.info("Подписка {} на все виды спорта", chatId);
            }
            case "stop" -> {
                database.unsubscribe(chatId);
                InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
                kb.setKeyboard(List.of(List.of(btn("🔙 Меню", "back_menu"))));
                editMessageWithKeyboard(chatId, messageId, "❌ Ты отписан от уведомлений.", kb);
                log.info("Отписка {}", chatId);
            }
            case "my_status" -> {
                String info = database.getUserInfo(chatId);
                editMessage(chatId, messageId, info != null ? info : "Ты не зарегистрирован. Нажми /start");
            }
            case "back_menu" -> {
                editMessageWithKeyboard(chatId, messageId,
                        "👋 Главное меню\nВыбери действие:", buildUserMenu());
            }
            // Админские кнопки
            case "admin_users" -> {
                if (!admins.contains(chatId)) return;
                List<String> users = database.getAllUsers();
                if (users.isEmpty()) {
                    editMessage(chatId, messageId, "Нет пользователей");
                } else {
                    StringBuilder sb = new StringBuilder("📋 Пользователи:\n\nID | Username | Статус\n");
                    for (String u : users) {
                        sb.append(u).append("\n");
                    }
                    InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
                    kb.setKeyboard(List.of(List.of(btn("🔙 Назад", "admin_menu"))));
                    editMessageWithKeyboard(chatId, messageId, sb.toString(), kb);
                }
            }
            case "admin_menu" -> {
                if (!admins.contains(chatId)) return;
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                keyboard.setKeyboard(List.of(
                        List.of(btn("📋 Пользователи", "admin_users"))
                ));
                editMessageWithKeyboard(chatId, messageId,
                        "👑 Админ-панель\n\n" +
                        "Текстовые команды:\n" +
                        "/grant <id> — выдать подписку\n" +
                        "/revoke <id> — отозвать подписку\n" +
                        "/status <id> — статус пользователя",
                        keyboard);
            }
        }
    }

    private boolean handleAdminCommand(long chatId, String text) {
        String[] parts = text.split("\\s+", 2);
        String cmd = parts[0];

        switch (cmd) {
            case "/grant" -> {
                if (parts.length < 2) { send(chatId, "Формат: /grant <chat_id>"); return true; }
                try {
                    long targetId = Long.parseLong(parts[1].trim());
                    if (database.setSubscriptionStatus(targetId, "active")) {
                        send(chatId, "✅ Подписка активирована для " + targetId);
                        send(targetId, "✅ Администратор выдал Вам подписку!");
                    } else {
                        send(chatId, "Пользователь " + targetId + " не найден в базе");
                    }
                } catch (NumberFormatException e) { send(chatId, "Неверный chat_id"); }
                return true;
            }
            case "/revoke" -> {
                if (parts.length < 2) { send(chatId, "Формат: /revoke <chat_id>"); return true; }
                try {
                    long targetId = Long.parseLong(parts[1].trim());
                    if (database.setSubscriptionStatus(targetId, "free")) {
                        send(chatId, "❌ Подписка деактивирована для " + targetId);
                        send(targetId, "❌ Ваша подписка была деактивирована.");
                    } else {
                        send(chatId, "Пользователь " + targetId + " не найден в базе");
                    }
                } catch (NumberFormatException e) { send(chatId, "Неверный chat_id"); }
                return true;
            }
            case "/status" -> {
                if (parts.length < 2) { send(chatId, "Формат: /status <chat_id>"); return true; }
                try {
                    long targetId = Long.parseLong(parts[1].trim());
                    String info = database.getUserInfo(targetId);
                    send(chatId, info != null ? info : "Пользователь не найден");
                } catch (NumberFormatException e) { send(chatId, "Неверный chat_id"); }
                return true;
            }
            case "/users" -> {
                List<String> users = database.getAllUsers();
                if (users.isEmpty()) {
                    send(chatId, "Нет пользователей");
                } else {
                    StringBuilder sb = new StringBuilder("📋 Пользователи:\n\nID | Username | Статус\n");
                    for (String u : users) sb.append(u).append("\n");
                    send(chatId, sb.toString());
                }
                return true;
            }
            default -> { return false; }
        }
    }

    private void showMainMenu(Long chatId) {
        sendWithKeyboard(chatId, "👋 Привет! Это бот для отслеживания крупных ставок.\nВыбери действие:", buildUserMenu());
    }

    private InlineKeyboardMarkup buildUserMenu() {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("📢 Подписаться", "subscribe"), btn("🔇 Отписаться", "stop")));
        rows.add(List.of(btn("📊 Мой статус", "my_status")));
        keyboard.setKeyboard(rows);
        return keyboard;
    }

    private InlineKeyboardButton btn(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

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
        try { execute(msg); }
        catch (TelegramApiException e) { log.warn("Не удалось отправить сообщение (chatId={}): {}", chatId, e.getMessage()); }
    }

    private void sendWithKeyboard(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text);
        msg.setReplyMarkup(keyboard);
        try { execute(msg); }
        catch (TelegramApiException e) { log.warn("Не удалось отправить сообщение (chatId={}): {}", chatId, e.getMessage()); }
    }

    private void editMessage(Long chatId, int messageId, String text) {
        EditMessageText edit = new EditMessageText();
        edit.setChatId(chatId.toString());
        edit.setMessageId(messageId);
        edit.setText(text);
        try { execute(edit); }
        catch (TelegramApiException e) { log.warn("Не удалось отредактировать сообщение: {}", e.getMessage()); }
    }

    private void editMessageWithKeyboard(Long chatId, int messageId, String text, InlineKeyboardMarkup keyboard) {
        EditMessageText edit = new EditMessageText();
        edit.setChatId(chatId.toString());
        edit.setMessageId(messageId);
        edit.setText(text);
        edit.setReplyMarkup(keyboard);
        try { execute(edit); }
        catch (TelegramApiException e) { log.warn("Не удалось отредактировать сообщение: {}", e.getMessage()); }
    }

    private void answerCallback(String callbackId) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackId);
        try { execute(answer); }
        catch (TelegramApiException e) { log.warn("Не удалось подтвердить callback: {}", e.getMessage()); }
    }
}
