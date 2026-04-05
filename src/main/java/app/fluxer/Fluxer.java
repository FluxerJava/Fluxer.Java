package app.fluxer;

import app.fluxer.bot.FluxerBot;
import app.fluxer.webhook.FluxerWebhookServer;

public final class Fluxer {
    private Fluxer() {
    }

    public static FluxerClient client(String botToken) {
        return new FluxerClient(botToken);
    }

    public static FluxerBot bot(FluxerClient client, int intents, String commandPrefix) {
        return new FluxerBot(client, intents, commandPrefix);
    }

    public static FluxerWebhookServer webhookServer(int port, String path,
                                                     app.fluxer.webhook.WebhookHandler handler) {
        return new FluxerWebhookServer(port, path, handler);
    }
}
