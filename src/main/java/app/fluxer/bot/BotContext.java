package app.fluxer.bot;

import app.fluxer.FluxerClient;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public record BotContext(FluxerClient client, JsonNode event) {
    public String content() {
        return event.path("d").path("content").asText("");
    }

    public String channelId() {
        return event.path("d").path("channel_id").asText();
    }

    public String authorId() {
        return event.path("d").path("author").path("id").asText();
    }

    public boolean authorIsBot() {
        return event.path("d").path("author").path("bot").asBoolean(false);
    }

    public JsonNode reply(String content) {
        String messageId = event.path("d").path("id").asText();
        return client.sendMessage(channelId(), Map.of(
                "content", content,
                "message_reference", Map.of("message_id", messageId)
        ));
    }
}
