# Fluxer Java

A lightweight **Java 21** wrapper for [Fluxer](https://Fluxer.app) with:

- HTTP API client support
- Webhook execution support
- Bot (Gateway) support
- Embedded webhook receiver server support

## Requirements

- Java 21+
- Maven 3.9+

## Installation

Build locally:

```bash
mvn clean install
```

## Quick Start

### 1) HTTP + webhook execution

```java
import app.fluxer.FluxerClient;

import java.util.Map;

public class HttpExample {
    public static void main(String[] args) {
        FluxerClient client = new FluxerClient(System.getenv("FLUXER_BOT_TOKEN"));

        client.sendMessage("123456789012345678", "Hello from Java 21 👋");

        client.executeWebhook(
                "112233445566778899",
                "webhook_token_here",
                Map.of("content", "Webhook message from Fluxer Java"),
                true
        );
    }
}
```

### 2) Bot support (Gateway)

```java
import app.fluxer.FluxerClient;
import app.fluxer.bot.FluxerBot;
import app.fluxer.bot.GatewayIntents;

public class BotExample {
    public static void main(String[] args) {
        FluxerClient client = new FluxerClient(System.getenv("FLUXER_BOT_TOKEN"));

        FluxerBot bot = new FluxerBot(
                client,
                GatewayIntents.GUILDS | GatewayIntents.GUILD_MESSAGES | GatewayIntents.MESSAGE_CONTENT,
                "!"
        );

        bot.onCommand("ping", ctx -> ctx.reply("pong!"));

        bot.onEvent("READY", event ->
                System.out.println("Connected to Fluxer gateway as session " + event.path("d").path("session_id").asText())
        );

        bot.connect().join();
    }
}
```

### 3) Receive inbound webhooks

```java
import app.fluxer.webhook.FluxerWebhookServer;
import app.fluxer.webhook.WebhookResponse;

public class WebhookServerExample {
    public static void main(String[] args) {
        FluxerWebhookServer server = new FluxerWebhookServer(8080, "/webhooks/fluxer", body -> {
            System.out.println("Received payload: " + body);
            return WebhookResponse.accepted("{\"status\":\"received\"}");
        });

        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
    }
}
```

## Notes

- Default API base URL is `https://api.fluxer.app/v1`.
- `FluxerClient` defaults to `Authorization: Bot <token>`.
- This project currently focuses on core bot + webhook workflows and can be extended for additional endpoints.
