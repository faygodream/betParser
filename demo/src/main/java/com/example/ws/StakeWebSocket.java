package com.example.ws;

import com.example.service.BetService;
import okhttp3.*;
import org.conscrypt.Conscrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.net.Authenticator;
import java.security.Security;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StakeWebSocket {

    private static final Logger log = LoggerFactory.getLogger(StakeWebSocket.class);

    private final BetService service;
    private final ProxyManager proxyManager;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Интервал принудительного переподключения для смены IP, минуты
    private static final long ROTATE_INTERVAL_MINUTES = 30;

    private static final String[] WS_URLS = {
            "wss://stake.bet/_api/websockets",
            "wss://stake.com/_api/websockets",
            "wss://stake.games/_api/websockets"
    };

    private static final String SUBSCRIBE_MESSAGE = "{" +
            "\"id\":\"1\"," +
            "\"type\":\"subscribe\"," +
            "\"payload\":{" +
            "\"query\":\"subscription HighrollerSportBets {\\n  highrollerSportBets {\\n  bet {\\n      ... on SportBet {\\n        amount\\n        currency\\n        outcomes {\\n          odds\\n          outcome {\\n            name\\n          }\\n          fixtureName\\n          market {\\n            name\\n          }\\n          fixture {\\n            tournament {\\n              category {\\n                sport {\\n                  slug\\n                }\\n              }\\n            }\\n          }\\n        }\\n      }\\n    }\\n  }\\n}\"" +
            "}" +
            "}";

    private int urlIndex = 0;
    private int consecutiveFailures = 0;
    private volatile WebSocket currentWebSocket;

    public StakeWebSocket(BetService service, ProxyManager proxyManager) {
        this.service = service;
        this.proxyManager = proxyManager;

        Security.insertProviderAt(Conscrypt.newProvider(), 1);
        log.info("TLS-провайдер Conscrypt зарегистрирован");
    }

    private OkHttpClient buildClient() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS", "Conscrypt");
            sslContext.init(null, null, null);

            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((java.security.KeyStore) null);
            X509TrustManager trustManager = (X509TrustManager) tmf.getTrustManagers()[0];

            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, trustManager)
                    .pingInterval(30, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .connectTimeout(15, TimeUnit.SECONDS);

            applyProxy(builder);
            return builder.build();

        } catch (Exception e) {
            log.warn("Conscrypt недоступен, используется стандартный TLS: {}", e.getMessage());
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .pingInterval(30, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .connectTimeout(15, TimeUnit.SECONDS);

            applyProxy(builder);
            return builder.build();
        }
    }

    private void applyProxy(OkHttpClient.Builder builder) {
        ProxyManager.ProxyConfig proxy = proxyManager.current();
        if (proxy == null) {
            log.info("Прокси не настроен, используется прямое подключение");
            return;
        }

        log.info("Используется прокси {}", proxy);
        builder.proxy(proxy.toProxy());

        if (proxy.user != null) {
            if (proxy.type.equals("socks5")) {
                Authenticator.setDefault(proxy.toAuthenticator());
            } else {
                builder.proxyAuthenticator((route, response) -> {
                    String credential = Credentials.basic(proxy.user, proxy.password != null ? proxy.password : "");
                    return response.request().newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build();
                });
            }
        }
    }

    public void connect() {
        String url = WS_URLS[urlIndex % WS_URLS.length];
        String domain = url.replace("wss://", "").replace("/_api/websockets", "");

        ProxyManager.ProxyConfig proxy = proxyManager.current();
        log.info("Подключение к {} ({})", url, proxy != null ? proxy : "напрямую");

        OkHttpClient client = buildClient();

        Request request = new Request.Builder()
                .url(url)
                .header("Sec-WebSocket-Protocol", "graphql-transport-ws")
                .header("Origin", "https://" + domain)
                .header("Host", domain)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .build();

        client.newWebSocket(request, new WebSocketListener() {

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                log.info("WebSocket подключён к {}", url);
                currentWebSocket = webSocket;
                urlIndex = Arrays.asList(WS_URLS).indexOf(url);
                consecutiveFailures = 0;

                String initMessage = "{\"type\":\"connection_init\",\"payload\":{}}";
                webSocket.send(initMessage);
                log.debug("Отправлен connection_init");

                scheduleRotation();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                log.trace("WS: {}", text.substring(0, Math.min(text.length(), 300)));

                if (text.contains("\"connection_ack\"")) {
                    webSocket.send(SUBSCRIBE_MESSAGE);
                    log.info("Подписка highrollerSportBets отправлена");
                    return;
                }

                if (text.contains("\"ka\"") || text.contains("\"pong\"") || text.contains("\"ping\"")) {
                    return;
                }

                service.handleMessage(text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                int code = (response != null) ? response.code() : -1;
                log.warn("Ошибка WebSocket (код {}): {}", code, t.getMessage());
                currentWebSocket = null;
                consecutiveFailures++;

                if (code == 403 || consecutiveFailures >= 3) {
                    urlIndex++;
                    if (urlIndex % WS_URLS.length == 0 && proxyManager.hasProxies()) {
                        proxyManager.next();
                        consecutiveFailures = 0;
                    }
                    log.info("Смена домена или прокси");
                    scheduleReconnect(2000);
                } else {
                    scheduleReconnect(5000);
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                log.debug("WebSocket закрывается: {} {}", code, reason);
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log.info("WebSocket закрыт: {} {}", code, reason);
                currentWebSocket = null;
                scheduleReconnect(5000);
            }
        });
    }

    /**
     * Backconnect-прокси выдают новый IP на каждом соединении,
     * поэтому для смены IP достаточно переподключиться.
     */
    private void scheduleRotation() {
        scheduler.schedule(() -> {
            log.info("Плановая ротация IP");
            WebSocket ws = currentWebSocket;
            if (ws != null) {
                currentWebSocket = null;
                ws.close(1000, "IP rotation");
                // Переподключение запустит onClosed
            }
        }, ROTATE_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    private void scheduleReconnect(long delayMs) {
        new Thread(() -> {
            try {
                log.info("Переподключение через {} с", delayMs / 1000);
                Thread.sleep(delayMs);
                connect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}
