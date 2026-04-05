package app.fluxer;

import app.fluxer.exception.FluxerApiException;
import app.fluxer.model.AuthScheme;
import app.fluxer.model.GatewayInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public class FluxerClient {
    public static final String DEFAULT_API_BASE = "https://api.fluxer.app/v1";

    private final URI apiBase;
    private final String token;
    private final AuthScheme authScheme;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FluxerClient(String token) {
        this(DEFAULT_API_BASE, token, AuthScheme.BOT, HttpClient.newHttpClient(), defaultMapper());
    }

    public FluxerClient(String apiBase, String token, AuthScheme authScheme) {
        this(apiBase, token, authScheme, HttpClient.newHttpClient(), defaultMapper());
    }

    public FluxerClient(String apiBase, String token, AuthScheme authScheme, HttpClient httpClient,
                        ObjectMapper objectMapper) {
        this.apiBase = URI.create(Objects.requireNonNull(apiBase, "apiBase"));
        this.token = Objects.requireNonNull(token, "token");
        this.authScheme = Objects.requireNonNull(authScheme, "authScheme");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public GatewayInfo getGatewayInfo() {
        HttpRequest request = newRequest("/gateway/bot", true).GET().build();
        return send(request, GatewayInfo.class);
    }

    public JsonNode sendMessage(String channelId, String content) {
        return sendMessage(channelId, Map.of("content", content));
    }

    public JsonNode sendMessage(String channelId, Map<String, Object> payload) {
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(payload, "payload");

        HttpRequest request = newRequest("/channels/%s/messages".formatted(channelId), true)
                .POST(jsonBody(payload))
                .build();

        return send(request, JsonNode.class);
    }

    public JsonNode executeWebhook(String webhookId, String webhookToken, Map<String, Object> payload,
                                   boolean waitForMessage) {
        Objects.requireNonNull(webhookId, "webhookId");
        Objects.requireNonNull(webhookToken, "webhookToken");
        Objects.requireNonNull(payload, "payload");

        String path = "/webhooks/%s/%s".formatted(webhookId, webhookToken);
        if (waitForMessage) {
            path += "?wait=true";
        }

        HttpRequest request = newRequest(path, false)
                .POST(jsonBody(payload))
                .build();

        return send(request, JsonNode.class);
    }

    public <T> T get(String path, Class<T> responseType) {
        HttpRequest request = newRequest(path, true).GET().build();
        return send(request, responseType);
    }

    public <T> T post(String path, Object payload, Class<T> responseType) {
        HttpRequest request = newRequest(path, true)
                .POST(jsonBody(payload))
                .build();
        return send(request, responseType);
    }

    private HttpRequest.Builder newRequest(String path, boolean includeAuthorization) {
        String base = apiBase.toString().endsWith("/")
                ? apiBase.toString().substring(0, apiBase.toString().length() - 1)
                : apiBase.toString();
        String normalizedPath = path.startsWith("/") ? path : "/" + path;

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + normalizedPath))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");

        if (includeAuthorization) {
            String authHeader = authorizationHeader();
            if (!authHeader.isBlank()) {
                builder.header("Authorization", authHeader);
            }
        }

        return builder;
    }

    private String authorizationHeader() {
        return switch (authScheme) {
            case BOT -> "Bot " + token;
            case BEARER -> "Bearer " + token;
            case RAW -> token;
        };
    }

    private HttpRequest.BodyPublisher jsonBody(Object payload) {
        try {
            return HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize request payload", e);
        }
    }

    private <T> T send(HttpRequest request, Class<T> responseType) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                throw new FluxerApiException(response.statusCode(), response.body());
            }
            if (responseType == Void.class || response.body().isBlank()) {
                return null;
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Fluxer API response", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Fluxer API request interrupted", e);
        }
    }

    private static ObjectMapper defaultMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public URI apiBase() {
        return apiBase;
    }

    public String token() {
        return token;
    }

    public AuthScheme authScheme() {
        return authScheme;
    }
}
