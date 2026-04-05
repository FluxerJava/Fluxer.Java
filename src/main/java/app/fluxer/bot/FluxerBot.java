package app.fluxer.bot;

import app.fluxer.FluxerClient;
import app.fluxer.model.GatewayInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class FluxerBot {
    private final FluxerClient client;
    private final ObjectMapper objectMapper;
    private final HttpClient wsHttpClient;
    private final Map<String, BotCommandHandler> commands = new ConcurrentHashMap<>();
    private final Map<String, Consumer<JsonNode>> eventHandlers = new ConcurrentHashMap<>();

    private final int intents;
    private final String commandPrefix;
    private final ScheduledExecutorService scheduler;

    private volatile WebSocket webSocket;
    private volatile String sessionId;
    private volatile Long sequence;
    private volatile long heartbeatIntervalMs = 45_000;

    public FluxerBot(FluxerClient client, int intents, String commandPrefix) {
        this.client = Objects.requireNonNull(client, "client");
        this.objectMapper = client.objectMapper();
        this.wsHttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        this.intents = intents;
        this.commandPrefix = commandPrefix == null ? "!" : commandPrefix;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public FluxerBot onCommand(String command, BotCommandHandler handler) {
        commands.put(command.toLowerCase(), handler);
        return this;
    }

    public FluxerBot onEvent(String eventType, Consumer<JsonNode> handler) {
        eventHandlers.put(eventType, handler);
        return this;
    }

    public CompletableFuture<WebSocket> connect() {
        GatewayInfo gatewayInfo = client.getGatewayInfo();
        String gatewayUrl = gatewayInfo.url();
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            throw new IllegalStateException("Gateway URL missing from /gateway/bot response");
        }

        URI gatewayUri = URI.create(gatewayUrl + (gatewayUrl.contains("?") ? "&" : "?") + "v=1&encoding=json");

        return wsHttpClient.newWebSocketBuilder()
                .buildAsync(gatewayUri, new GatewayListener())
                .thenApply(ws -> {
                    this.webSocket = ws;
                    return ws;
                });
    }

    public void close() {
        WebSocket current = this.webSocket;
        if (current != null) {
            current.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
        }
        scheduler.shutdownNow();
    }

    private void dispatch(JsonNode payload) {
        int op = payload.path("op").asInt(-1);
        JsonNode data = payload.path("d");

        if (payload.hasNonNull("s")) {
            sequence = payload.path("s").asLong();
        }

        switch (op) {
            case 10 -> {
                heartbeatIntervalMs = data.path("heartbeat_interval").asLong(45_000L);
                startHeartbeat();
                identify();
            }
            case 11 -> {
                // heartbeat ACK
            }
            case 0 -> handleDispatch(payload);
            case 7 -> reconnect();
            default -> {
                // Ignore unsupported opcodes for now.
            }
        }
    }

    private void handleDispatch(JsonNode payload) {
        String eventType = payload.path("t").asText("");
        JsonNode data = payload.path("d");

        if ("READY".equals(eventType)) {
            sessionId = data.path("session_id").asText(null);
        }

        Consumer<JsonNode> handler = eventHandlers.get(eventType);
        if (handler != null) {
            handler.accept(payload);
        }

        if ("MESSAGE_CREATE".equals(eventType)) {
            processCommand(payload);
        }
    }

    private void processCommand(JsonNode eventPayload) {
        BotContext context = new BotContext(client, eventPayload);
        if (context.authorIsBot()) {
            return;
        }

        String content = context.content();
        if (!content.startsWith(commandPrefix)) {
            return;
        }

        String body = content.substring(commandPrefix.length()).trim();
        if (body.isBlank()) {
            return;
        }

        String command = body.split("\\s+", 2)[0].toLowerCase();
        BotCommandHandler handler = commands.get(command);
        if (handler == null) {
            return;
        }

        try {
            handler.handle(context);
        } catch (Exception e) {
            context.reply("Command failed: " + e.getMessage());
        }
    }

    private void identify() {
        Map<String, Object> identifyPayload = Map.of(
                "op", 2,
                "d", Map.of(
                        "token", client.token(),
                        "intents", intents,
                        "properties", Map.of(
                                "os", System.getProperty("os.name", "linux"),
                                "browser", "fluxer-java",
                                "device", "fluxer-java"
                        )
                )
        );
        sendJson(identifyPayload);
    }

    private void sendHeartbeat() {
        sendJson(Map.of("op", 1, "d", sequence));
    }

    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, heartbeatIntervalMs, heartbeatIntervalMs,
                TimeUnit.MILLISECONDS);
    }

    private void reconnect() {
        WebSocket ws = this.webSocket;
        if (ws != null) {
            ws.abort();
        }
        connect();
    }

    private void sendJson(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            webSocket.sendText(json, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send gateway payload", e);
        }
    }

    private final class GatewayListener implements WebSocket.Listener {
        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String json = textBuffer.toString();
                textBuffer.setLength(0);
                try {
                    JsonNode payload = objectMapper.readTree(json);
                    dispatch(payload);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            error.printStackTrace();
        }
    }
}
