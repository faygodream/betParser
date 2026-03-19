package com.example.service;

import com.google.gson.*;
import com.example.bot.TelegramBot;

import java.util.Map;
import java.util.Set;

public class BetService {

    private final TelegramBot bot;

    // Поддерживаемые игры: slug -> отображаемое название
    private static final Map<String, String> SUPPORTED_SPORTS = Map.of(
            "dota-2", "DOTA 2",
            "counter-strike", "CS2"
    );

    public BetService(TelegramBot bot) {
        this.bot = bot;
    }

    public void handleMessage(String text) {

        if (!text.contains("highrollerSportBets")) return;

        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();

            if (!json.has("type") || !"next".equals(json.get("type").getAsString())) return;

            JsonObject payload = json.getAsJsonObject("payload");
            if (payload == null) return;

            JsonObject data = payload.getAsJsonObject("data");
            if (data == null || !data.has("highrollerSportBets")) return;

            JsonObject highrollerBets = data.getAsJsonObject("highrollerSportBets");
            if (highrollerBets == null) return;

            JsonObject bet = highrollerBets.getAsJsonObject("bet");
            if (bet == null) return;

            double amount = bet.get("amount").getAsDouble();
            String currency = bet.has("currency") ? bet.get("currency").getAsString() : "unknown";

            JsonArray outcomes = bet.getAsJsonArray("outcomes");
            if (outcomes == null || outcomes.size() == 0) return;

            JsonObject outcome = outcomes.get(0).getAsJsonObject();

            double odds = outcome.has("odds") ? outcome.get("odds").getAsDouble() : 0;
            String match = outcome.has("fixtureName") ? outcome.get("fixtureName").getAsString() : "Unknown";

            // Тип ставки (исход, фора, тотал и т.д.)
            String marketName = "—";
            if (outcome.has("market") && !outcome.get("market").isJsonNull()) {
                JsonObject market = outcome.getAsJsonObject("market");
                if (market.has("name")) {
                    marketName = market.get("name").getAsString();
                }
            }

            // На кого/что поставлено
            String outcomeName = "—";
            if (outcome.has("outcome") && !outcome.get("outcome").isJsonNull()) {
                JsonObject outcomeObj = outcome.getAsJsonObject("outcome");
                if (outcomeObj.has("name")) {
                    outcomeName = outcomeObj.get("name").getAsString();
                }
            }

            String sportSlug = outcome
                    .getAsJsonObject("fixture")
                    .getAsJsonObject("tournament")
                    .getAsJsonObject("category")
                    .getAsJsonObject("sport")
                    .get("slug").getAsString();

            if (!SUPPORTED_SPORTS.containsKey(sportSlug)) return;

            if (amount < 1000) return;

            String sportName = SUPPORTED_SPORTS.get(sportSlug);

            String message = "🔥 КРУПНАЯ СТАВКА (" + sportName + ")\n\n" +
                    "🎮 Матч: " + match + "\n" +
                    "📊 Тип: " + marketName + "\n" +
                    "🎯 Выбор: " + outcomeName + "\n" +
                    "💰 Сумма: " + String.format("%.2f", amount) + " " + currency.toUpperCase() + "\n" +
                    "📈 Кэф: " + odds;

            System.out.println("Ставка [" + sportSlug + "]: " + match + " — " + marketName + " → " + outcomeName + " — " + amount + " " + currency);
            bot.broadcastForSport(sportSlug, message);

        } catch (Exception e) {
            System.err.println("Ошибка парсинга сообщения: " + e.getMessage());
        }
    }
}
