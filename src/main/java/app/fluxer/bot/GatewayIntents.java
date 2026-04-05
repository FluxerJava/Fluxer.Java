package app.fluxer.bot;

public final class GatewayIntents {
    private GatewayIntents() {
    }

    public static final int GUILDS = 1 << 0;
    public static final int GUILD_MESSAGES = 1 << 9;
    public static final int DIRECT_MESSAGES = 1 << 12;
    public static final int MESSAGE_CONTENT = 1 << 15;
}
