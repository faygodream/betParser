package com.example.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {

    private static final Logger log = LoggerFactory.getLogger(Database.class);

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
        log.info("Подключение к PostgreSQL установлено");
    }

    private void createTable() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    chat_id             BIGINT PRIMARY KEY,
                    username            VARCHAR(100),
                    sport               VARCHAR(50),
                    subscription_status VARCHAR(20) NOT NULL DEFAULT 'free',
                    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
                )
            """);
            // Миграция: добавить username если таблица уже существует без него
            stmt.execute("""
                DO $$ BEGIN
                    ALTER TABLE users ADD COLUMN username VARCHAR(100);
                EXCEPTION WHEN duplicate_column THEN NULL;
                END $$
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось создать таблицу users: " + e.getMessage(), e);
        }
    }

    /** Зарегистрировать пользователя (без выбора спорта). */
    public void registerUser(long chatId, String username) {
        String sql = """
                INSERT INTO users (chat_id, username)
                VALUES (?, ?)
                ON CONFLICT (chat_id) DO UPDATE SET username = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            ps.setString(2, username);
            ps.setString(3, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("registerUser: {}", e.getMessage());
        }
    }

    /** Получить всех активных подписчиков (без фильтра по спорту). */
    public List<Long> getActiveSubscribers() {
        String sql = "SELECT chat_id FROM users WHERE subscription_status = 'active'";
        List<Long> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getLong("chat_id"));
            }
        } catch (SQLException e) {
            log.error("getActiveSubscribers: {}", e.getMessage());
        }
        return result;
    }

    /** Добавить или обновить подписку пользователя. */
    public void subscribe(long chatId, String username, String sport) {
        String sql = """
                INSERT INTO users (chat_id, username, sport)
                VALUES (?, ?, ?)
                ON CONFLICT (chat_id) DO UPDATE SET username = ?, sport = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            ps.setString(2, username);
            ps.setString(3, sport);
            ps.setString(4, username);
            ps.setString(5, sport);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("subscribe: {}", e.getMessage());
        }
    }

    /** Удалить подписку пользователя. */
    public void unsubscribe(long chatId) {
        String sql = "UPDATE users SET sport = NULL WHERE chat_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("unsubscribe: {}", e.getMessage());
        }
    }

    /** Получить chatId всех активных подписчиков на данный спорт (или "all"). */
    public List<Long> getSubscribers(String sportSlug) {
        String sql = "SELECT chat_id FROM users WHERE (sport = ? OR sport = 'all') AND subscription_status = 'active'";
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
            log.error("getSubscribers: {}", e.getMessage());
        }
        return result;
    }

    /** Проверить, активна ли подписка пользователя. */
    public boolean isActive(long chatId) {
        String sql = "SELECT subscription_status FROM users WHERE chat_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return "active".equals(rs.getString("subscription_status"));
                }
            }
        } catch (SQLException e) {
            log.error("isActive: {}", e.getMessage());
        }
        return false;
    }

    /** Установить статус подписки пользователю. */
    public boolean setSubscriptionStatus(long chatId, String status) {
        String sql = "UPDATE users SET subscription_status = ? WHERE chat_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, chatId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("setSubscriptionStatus: {}", e.getMessage());
            return false;
        }
    }

    /** Получить информацию о пользователе. */
    public String getUserInfo(long chatId) {
        String sql = "SELECT chat_id, username, sport, subscription_status, created_at FROM users WHERE chat_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String uname = rs.getString("username");
                    return "ID: " + rs.getLong("chat_id") +
                            "\nUsername: " + (uname != null ? "@" + uname : "не указан") +
                            "\nСпорт: " + rs.getString("sport") +
                            "\nСтатус: " + rs.getString("subscription_status") +
                            "\nДата регистрации: " + rs.getTimestamp("created_at");
                }
            }
        } catch (SQLException e) {
            log.error("getUserInfo: {}", e.getMessage());
        }
        return null;
    }

    /** Список всех пользователей. */
    public List<String> getAllUsers() {
        String sql = "SELECT chat_id, username, subscription_status FROM users ORDER BY created_at";
        List<String> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String uname = rs.getString("username");
                result.add(rs.getLong("chat_id") + " | " +
                        (uname != null ? "@" + uname : "—") + " | " +
                        rs.getString("subscription_status"));
            }
        } catch (SQLException e) {
            log.error("getAllUsers: {}", e.getMessage());
        }
        return result;
    }

    public void close() {
        dataSource.close();
    }
}
