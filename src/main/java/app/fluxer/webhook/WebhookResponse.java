package app.fluxer.webhook;

public record WebhookResponse(int statusCode, String contentType, String body) {
    public static WebhookResponse ok(String body) {
        return new WebhookResponse(200, "application/json", body);
    }

    public static WebhookResponse accepted(String body) {
        return new WebhookResponse(202, "application/json", body);
    }
}
