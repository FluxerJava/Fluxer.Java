package app.fluxer.bot;

@FunctionalInterface
public interface BotCommandHandler {
    void handle(BotContext context) throws Exception;
}
