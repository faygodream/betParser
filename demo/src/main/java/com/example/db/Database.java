package com.example.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {

    private final HikariDataSource dataSource;

    public Database(String url, String user, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);

        this.dataSource = new HikariDataSource(config);
        createTable();
        System.out.println("PostgreSQL подключен: " + url);
    }

    private void createTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS users (
                    chat_id            BIGINT PRIMARY KEY,
                    sport              VARCHAR(50),
                    subscription_status VARCHAR(20) NOT NULL DEFAULT 'free',
                    created_at         TIMESTAMP NOT NULL DEFAULT NOW()
                )
                """;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось создать таблицу users: " + e.getMessage(), e);
        }
    }

    /**
     * Добавить или обновить подписку пользователя.
     */
    public void subscribe(long chatId, String sport) {
        String sql = """
                INSERT INTO users (chat_id, sport)
                VALUES (?, ?)
                ON CONFLICT (chat_id) DO UPDATE SET sport = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            ps.setString(2, sport);
            ps.setString(3, sport);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Ошибка subscribe: " + e.getMessage());
        }
    }

    /**
     * Удалить подписку (sport = null, но пользователь остаётся в БД).
     */
    public void unsubscribe(long chatId) {
        String sql = "UPDATE users SET sport = NULL WHERE chat_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Ошибка unsubscribe: " + e.getMessage());
        }
    }

    /**
     * Получить chatId всех пользователей, подписанных на данный спорт (или "all").
     */
    public List<Long> getSubscribers(String sportSlug) {
        String sql = "SELECT chat_id FROM users WHERE sport = ? OR sport = 'all'";
        List<Long> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sportSlug);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getLong("chat_id"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Ошибка getSubscribers: " + e.getMessage());
        }
        return result;
    }

    public void close() {
        dataSource.close();
    }
}
