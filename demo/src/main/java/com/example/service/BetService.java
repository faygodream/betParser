package com.example.service;

import com.example.bot.TelegramBot;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

public class BetService {

    private static final Logger log = LoggerFactory.getLogger(BetService.class);

    /** Минимальная сумма ставки для уведомления. */
    private static final double MIN_AMOUNT = 1000;

    private final TelegramBot bot;

    // slug -> отображаемое название
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
            if (bet == null || !bet.has("amount") || !bet.has("outcomes")) return;

            double amount = bet.get("amount").getAsDouble();
            String currency = bet.has("currency") ? bet.get("currency").getAsString() : "unknown";

            JsonArray outcomes = bet.getAsJsonArray("outcomes");
            if (outcomes == null || outcomes.size() == 0) return;

            JsonObject outcome = outcomes.get(0).getAsJsonObject();

            double odds = outcome.has("odds") ? outcome.get("odds").getAsDouble() : 0;
            String match = outcome.has("fixtureName") ? outcome.get("fixtureName").getAsString() : "Unknown";

            // Исход, фора, тотал и т.д.
            String marketName = "—";
            if (outcome.has("market") && !outcome.get("market").isJsonNull()) {
                JsonObject market = outcome.getAsJsonObject("market");
                if (market.has("name")) {
                    marketName = market.get("name").getAsString();
                }
            }

            String outcomeName = "—";
            if (outcome.has("outcome") && !outcome.get("outcome").isJsonNull()) {
                JsonObject outcomeObj = outcome.getAsJsonObject("outcome");
                if (outcomeObj.has("name")) {
                    outcomeName = outcomeObj.get("name").getAsString();
                }
            }

            // Цепочка fixture -> tournament -> category -> sport может обрываться на любом уровне
            if (!outcome.has("fixture") || outcome.get("fixture").isJsonNull()) return;
            JsonObject fixture = outcome.getAsJsonObject("fixture");
            if (!fixture.has("tournament") || fixture.get("tournament").isJsonNull()) return;
            JsonObject tournament = fixture.getAsJsonObject("tournament");
            if (!tournament.has("category") || tournament.get("category").isJsonNull()) return;
            JsonObject category = tournament.getAsJsonObject("category");
            if (!category.has("sport") || category.get("sport").isJsonNull()) return;
            JsonObject sport = category.getAsJsonObject("sport");
            if (!sport.has("slug")) return;

            String sportSlug = sport.get("slug").getAsString();

            if (!SUPPORTED_SPORTS.containsKey(sportSlug)) return;

            if (amount < MIN_AMOUNT) return;

            String sportName = SUPPORTED_SPORTS.get(sportSlug);

            String message = "🔥 КРУПНАЯ СТАВКА (" + sportName + ")\n\n" +
                    "🎮 Матч: " + match + "\n" +
                    "📊 Тип: " + marketName + "\n" +
                    "🎯 Выбор: " + outcomeName + "\n" +
                    "💰 Сумма: " + String.format("%.2f", amount) + " " + currency.toUpperCase() + "\n" +
                    "📈 Кэф: " + odds;

            log.info("Ставка [{}]: {} / {} / {} — {} {}", sportSlug, match, marketName, outcomeName, amount, currency);
            bot.broadcastForSport(sportSlug, message);

        } catch (Exception e) {
            log.warn("Не удалось разобрать сообщение: {}", e.getMessage());
        }
    }
}
