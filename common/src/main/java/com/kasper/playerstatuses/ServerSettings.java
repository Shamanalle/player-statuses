package com.kasper.playerstatuses;

import java.util.Arrays;

/**
 * The server's settings ({@code config/playerstatuses-server.properties}, on Bukkit
 * {@code plugins/PlayerStatuses/playerstatuses-server.properties}). Read again when the file changes.
 */
public final class ServerSettings extends SettingsFile {

    public static final String FILE = "playerstatuses-server.properties";

    public static final Key<Boolean> AFK_COMMAND = bool("afk_command", true,
            "Players may mark themselves AFK with /afk");
    public static final Key<Boolean> DETECT_VANILLA = bool("detect_players_without_mod", true,
            "For players without the mod the server shows what it can see itself: AFK and open containers");
    public static final Key<Integer> AFK_MINUTES = integer("afk_minutes", 5, 1, 120,
            "Minutes without moving or acting after which a player without the mod counts as AFK");
    public static final Key<String> LANGUAGE = text("language", "en_us",
            "Language of the console's replies; players get replies in their own game language");

    public ServerSettings() {
        super("Player Statuses: server settings. Saved changes are picked up within a few seconds;\n"
                + "/playerstatuses reload reads the file at once.",
                Arrays.asList(AFK_COMMAND, DETECT_VANILLA, AFK_MINUTES, LANGUAGE));
    }
}
