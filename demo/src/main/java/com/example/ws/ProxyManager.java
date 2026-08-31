package com.example.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class ProxyManager {

    private static final Logger log = LoggerFactory.getLogger(ProxyManager.class);

    private final Path proxyFile;
    private List<ProxyConfig> proxies = new ArrayList<>();
    private int currentIndex = 0;

    public ProxyManager(String proxyFilePath) {
        this.proxyFile = Paths.get(proxyFilePath);
        reload();
    }

    public void reload() {
        List<ProxyConfig> loaded = new ArrayList<>();
        try {
            if (Files.exists(proxyFile)) {
                List<String> lines = Files.readAllLines(proxyFile);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    ProxyConfig config = parse(line);
                    if (config != null) {
                        loaded.add(config);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Не удалось прочитать {}: {}", proxyFile.getFileName(), e.getMessage());
        }
        this.proxies = loaded;
        this.currentIndex = 0;
        log.info("Загружено прокси: {}", proxies.size());
    }

    private ProxyConfig parse(String line) {
        try {
            // Формат: type://user:pass@host:port или type://host:port
            String type;
            if (line.startsWith("socks5://")) {
                type = "socks5";
                line = line.substring("socks5://".length());
            } else if (line.startsWith("http://")) {
                type = "http";
                line = line.substring("http://".length());
            } else {
                log.warn("Неизвестный тип прокси в строке конфига");
                return null;
            }

            String user = null, pass = null;
            if (line.contains("@")) {
                String[] parts = line.split("@", 2);
                String[] creds = parts[0].split(":", 2);
                user = creds[0];
                pass = creds.length > 1 ? creds[1] : null;
                line = parts[1];
            }

            String[] hostPort = line.split(":", 2);
            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1]);

            return new ProxyConfig(type, host, port, user, pass);
        } catch (Exception e) {
            log.warn("Не удалось разобрать строку прокси: {}", e.getMessage());
            return null;
        }
    }

    public boolean hasProxies() {
        return !proxies.isEmpty();
    }

    /** Текущий прокси или null, если список пуст. */
    public ProxyConfig current() {
        if (proxies.isEmpty()) return null;
        return proxies.get(currentIndex % proxies.size());
    }

    /** Переключиться на следующий прокси. false, если список пуст. */
    public boolean next() {
        if (proxies.isEmpty()) return false;
        currentIndex = (currentIndex + 1) % proxies.size();
        log.info("Переключение на прокси {}", current());
        return true;
    }

    public static class ProxyConfig {
        public final String type;
        public final String host;
        public final int port;
        public final String user;
        public final String password;

        public ProxyConfig(String type, String host, int port, String user, String password) {
            this.type = type;
            this.host = host;
            this.port = port;
            this.user = user;
            this.password = password;
        }

        public Proxy toProxy() {
            Proxy.Type proxyType = type.equals("socks5") ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
            return new Proxy(proxyType, new InetSocketAddress(host, port));
        }

        public Authenticator toAuthenticator() {
            if (user == null) return null;
            return new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, password != null ? password.toCharArray() : new char[0]);
                }
            };
        }

        @Override
        public String toString() {
            return type + "://" + (user != null ? user + ":***@" : "") + host + ":" + port;
        }
    }
}
