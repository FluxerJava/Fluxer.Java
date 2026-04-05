package app.fluxer;

import app.fluxer.webhook.FluxerWebhookServer;
import app.fluxer.webhook.WebhookResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FluxerWebhookServerTest {

    @Test
    void webhookServerAcceptsPostAndReturnsJson() throws IOException, InterruptedException {
        int port = 8181;
        try (FluxerWebhookServer server = new FluxerWebhookServer(port, "/incoming", body ->
                WebhookResponse.accepted("{\"echo\":\"" + body.path("content").asText() + "\"}"))) {
            server.start();

            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/incoming"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"hello\"}"))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(202, response.statusCode());
            assertEquals("{\"echo\":\"hello\"}", response.body());
        }
    }
}
