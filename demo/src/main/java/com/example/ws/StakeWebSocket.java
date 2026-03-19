package com.example.ws;

import okhttp3.*;
import com.example.service.BetService;
import org.conscrypt.Conscrypt;

import javax.net.ssl.*;
import java.security.Security;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class StakeWebSocket {

    private final BetService service;
    private final OkHttpClient client;

    private static final String[] WS_URLS = {
            "wss://stake.bet/_api/websockets",
            "wss://stake.com/_api/websockets",
            "wss://stake.games/_api/websockets"
    };

    // GraphQL подписка с дополнительными полями: outcomeName, market
    private static final String SUBSCRIBE_MESSAGE = "{" +
            "\"id\":\"1\"," +
            "\"type\":\"subscribe\"," +
            "\"payload\":{" +
            "\"query\":\"subscription HighrollerSportBets {\\n  highrollerSportBets {\\n    bet {\\n      ... on SportBet {\\n        amount\\n        currency\\n        outcomes {\\n          odds\\n          outcome {\\n            name\\n          }\\n          fixtureName\\n          market {\\n            name\\n          }\\n          fixture {\\n            tournament {\\n              category {\\n                sport {\\n                  slug\\n                }\\n              }\\n            }\\n          }\\n        }\\n      }\\n    }\\n  }\\n}\"" +
            "}" +
            "}";

    private int urlIndex = 0;

    public StakeWebSocket(BetService service) {
        this.service = service;

        // Устанавливаем Conscrypt как основной TLS-провайдер
        // Это полностью меняет TLS fingerprint (JA3) на отличный от стандартного Java
        Security.insertProviderAt(Conscrypt.newProvider(), 1);
        System.out.println("Conscrypt TLS provider установлен");

        this.client = buildClient();
    }

    private OkHttpClient buildClient() {
        try {
            // Создаём SSLContext с Conscrypt провайдером
            SSLContext sslContext = SSLContext.getInstance("TLS", "Conscrypt");
            sslContext.init(null, null, null);

            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            // Получаем TrustManager
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((java.security.KeyStore) null);
            X509TrustManager trustManager = (X509TrustManager) tmf.getTrustManagers()[0];

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, trustManager)
                    .pingInterval(30, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .build();

        } catch (Exception e) {
            System.err.println("Не удалось настроить Conscrypt, используем стандартный TLS: " + e.getMessage());
            return new OkHttpClient.Builder()
                    .pingInterval(30, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .build();
        }
    }

    public void connect() {
        String url = WS_URLS[urlIndex % WS_URLS.length];
        String domain = url.replace("wss://", "").replace("/_api/websockets", "");

        System.out.println("Подключение к: " + url);

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
                System.out.println("WebSocket подключен к " + url + "!");
                urlIndex = Arrays.asList(WS_URLS).indexOf(url);

                String initMessage = "{\"type\":\"connection_init\",\"payload\":{}}";
                webSocket.send(initMessage);
                System.out.println("Отправлен connection_init");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                System.out.println("WS: " + text.substring(0, Math.min(text.length(), 300)));

                if (text.contains("\"connection_ack\"")) {
                    System.out.println("Получен connection_ack → отправляем подписку...");
                    webSocket.send(SUBSCRIBE_MESSAGE);
                    System.out.println("Подписка highrollerSportBets отправлена!");
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
                System.err.println("WebSocket ошибка (код " + code + "): " + t.getMessage());

                if (code == 403) {
                    urlIndex++;
                    System.out.println("403 → следующий домен...");
                    scheduleReconnect(2000);
                } else {
                    scheduleReconnect(5000);
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                System.out.println("WebSocket closing: " + code + " " + reason);
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                System.out.println("WebSocket closed: " + code + " " + reason);
                scheduleReconnect(5000);
            }
        });
    }

    private void scheduleReconnect(long delayMs) {
        new Thread(() -> {
            try {
                System.out.println("Переподключение через " + (delayMs / 1000) + " сек...");
                Thread.sleep(delayMs);
                connect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}
