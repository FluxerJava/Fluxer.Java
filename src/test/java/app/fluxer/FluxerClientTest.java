package app.fluxer;

import app.fluxer.model.AuthScheme;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FluxerClientTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendMessageUsesBotAuthorizationByDefault() throws Exception {
        AtomicReference<String> authHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/channels/abc/messages", new JsonResponder(exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            return "{\"id\":\"42\"}";
        }));
        server.start();

        FluxerClient client = new FluxerClient(
                "http://localhost:" + server.getAddress().getPort() + "/v1",
                "token123",
                AuthScheme.BOT
        );

        JsonNode result = client.sendMessage("abc", "hi");

        assertEquals("Bot token123", authHeader.get());
        assertEquals("42", result.path("id").asText());
    }

    @Test
    void executeWebhookSendsPayload() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/webhooks/whid/whtoken", new JsonResponder(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            return "{\"ok\":true}";
        }));
        server.start();

        FluxerClient client = new FluxerClient(
                "http://localhost:" + server.getAddress().getPort() + "/v1",
                "token123",
                AuthScheme.BOT
        );

        JsonNode result = client.executeWebhook("whid", "whtoken", Map.of("content", "Hello"), false);

        assertNotNull(requestBody.get());
        assertEquals(true, result.path("ok").asBoolean());
    }

    private static class JsonResponder implements HttpHandler {
        private final ThrowingFunction<HttpExchange, String> function;

        private JsonResponder(ThrowingFunction<HttpExchange, String> function) {
            this.function = function;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String body = function.apply(exchange);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (Exception e) {
                byte[] bytes = ("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally {
                exchange.close();
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingFunction<T, R> {
        R apply(T value) throws Exception;
    }
}
