package com.kasper.playerstatuses;

import java.util.Locale;
import java.util.UUID;

import static com.kasper.playerstatuses.CommandReply.Style.*;

/**
 * {@code /playerstatuses} and {@code /afk}, the same on every platform: the platform passes the text
 * after the command and who runs it, and shows the reply.
 */
public final class StatusCommands {

    public static final String NAME = "playerstatuses";
    public static final String AFK = "afk";

    /** Who runs a command. */
    public interface Source {
        /** The player, or {@code null} for the console. */
        UUID player();

        /** The game language of the player ("ru_ru"), empty for the console. */
        String language();

        /** May change the server's settings (operator, or the console). */
        boolean isAdmin();
    }

    private StatusCommands() {
    }

    /**
     * {@code /playerstatuses [status|reload|help]}.
     *
     * @param online players on the server now
     */
    public static CommandReply run(String args, Source source, StatusServer server, int online, long now) {
        String lang = language(source, server);
        String sub = args == null ? "" : args.trim().toLowerCase(Locale.ROOT);
        CommandReply r = new CommandReply();
        switch (sub) {
            case "", "status" -> status(r, lang, source, server, online, now);
            case "reload" -> {
                if (!source.isAdmin()) {
                    return r.text(t(lang, "no_permission"), ERROR).failed();
                }
                server.reload();
                r.text(t(lang, "reloaded"), OK);
                for (String p : server.settings().problems()) {
                    r.text(t(lang, "reload_problem", p), WARN);
                }
            }
            case "help" -> help(r, lang);
            default -> {
                r.text(t(lang, "unknown", args.trim()), ERROR).failed();
                help(r, lang);
            }
        }
        return r;
    }

    /** {@code /afk}: toggles the player's AFK mark. */
    public static CommandReply afk(Source source, StatusServer server, long now) {
        String lang = language(source, server);
        CommandReply r = new CommandReply();
        if (!server.settings().get(ServerSettings.AFK_COMMAND)) {
            return r.text(t(lang, "afk.disabled"), ERROR).failed();
        }
        UUID player = source.player();
        if (player == null) {
            return r.text(t(lang, "afk.console"), ERROR).failed();
        }
        boolean afk = !server.relay().isManualAfk(player);
        server.relay().setManualAfk(player, afk, now);
        return r.text(t(lang, afk ? "afk.on" : "afk.off"), afk ? WARN : OK);
    }

    private static void status(CommandReply r, String lang, Source source, StatusServer server, int online, long now) {
        ServerSettings s = server.settings();
        r.text(t(lang, "title", BuildInfo.version()), TITLE);
        r.line(t(lang, "modded") + ": ", LABEL, server.relay().moddedCount() + " / " + online, VALUE);
        r.line(t(lang, "afk_command") + ": ", LABEL, onOff(lang, s.get(ServerSettings.AFK_COMMAND)), VALUE);
        r.line(t(lang, "detect") + ": ", LABEL, onOff(lang, s.get(ServerSettings.DETECT_VANILLA)), VALUE);
        if (source.player() != null) {
            PlayerStatus mine = server.relay().current(source.player(), now);
            r.line(t(lang, "yours") + ": ", LABEL, describe(lang, mine), VALUE);
        }
        r.text(t(lang, "help.hint"), MUTED);
    }

    private static void help(CommandReply r, String lang) {
        r.text(t(lang, "help.title"), TITLE);
        r.text(t(lang, "help.status"), PLAIN);
        r.text(t(lang, "help.reload"), PLAIN);
        r.text(t(lang, "help.afk"), PLAIN);
    }

    /** A status in words: "Inventory, AFK 12m". */
    public static String describe(String lang, PlayerStatus status) {
        StringBuilder b = new StringBuilder();
        if (status.activity() != Activity.NONE) {
            b.append(ServerText.raw(lang, status.activity().translationKey()));
        }
        for (StatusFlag f : StatusFlag.values()) {
            if (!status.has(f)) {
                continue;
            }
            if (b.length() > 0) {
                b.append(", ");
            }
            if (f == StatusFlag.AFK && !IdleText.duration(status.idleSeconds()).isEmpty()) {
                b.append(String.format(Locale.ROOT, ServerText.raw(lang, "gui.playerstatuses.afk_time"),
                        IdleText.duration(status.idleSeconds())));
            } else {
                b.append(ServerText.raw(lang, f.translationKey()));
            }
        }
        return b.length() == 0 ? ServerText.raw(lang, Activity.NONE.translationKey()) : b.toString();
    }

    private static String onOff(String lang, boolean on) {
        return t(lang, on ? "on" : "off");
    }

    private static String language(Source source, StatusServer server) {
        String l = source.language();
        return l == null || l.isEmpty() ? server.settings().get(ServerSettings.LANGUAGE) : l;
    }

    private static String t(String lang, String key, Object... args) {
        return ServerText.get(lang, key, args);
    }
}
