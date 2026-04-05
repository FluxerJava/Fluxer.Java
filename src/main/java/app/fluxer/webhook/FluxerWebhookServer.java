package app.fluxer.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FluxerWebhookServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executorService;
    private final ObjectMapper objectMapper;

    public FluxerWebhookServer(int port, String path, WebhookHandler handler) {
        this(port, path, handler, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    public FluxerWebhookServer(int port, String path, WebhookHandler handler, ObjectMapper objectMapper) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(handler, "handler");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");

        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new RuntimeException("Unable to start webhook server", e);
        }

        this.executorService = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
        this.server.setExecutor(executorService);
        this.server.createContext(path, new JsonWebhookHandler(handler));
    }

    public void start() {
        server.start();
    }

    @Override
    public void close() {
        server.stop(0);
        executorService.shutdownNow();
    }

    private final class JsonWebhookHandler implements HttpHandler {
        private final WebhookHandler delegate;

        private JsonWebhookHandler(WebhookHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                write(exchange, new WebhookResponse(405, "application/json", "{\"error\":\"method_not_allowed\"}"));
                return;
            }

            byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            JsonNode requestBody = objectMapper.readTree(requestBytes.length == 0 ? "{}" : new String(requestBytes, StandardCharsets.UTF_8));

            try {
                WebhookResponse response = delegate.handle(requestBody);
                write(exchange, response);
            } catch (Exception e) {
                write(exchange, new WebhookResponse(500, "application/json",
                        "{\"error\":\"webhook_handler_failed\",\"message\":\"%s\"}".formatted(escapeJson(e.getMessage()))));
            }
        }

        private static void write(HttpExchange exchange, WebhookResponse response) throws IOException {
            byte[] bytes = response.body() == null ? new byte[0] : response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", response.contentType());
            exchange.sendResponseHeaders(response.statusCode(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        private static String escapeJson(String value) {
            if (value == null) {
                return "";
            }
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
