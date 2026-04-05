package app.fluxer.webhook;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface WebhookHandler {
    WebhookResponse handle(JsonNode body) throws Exception;
}
