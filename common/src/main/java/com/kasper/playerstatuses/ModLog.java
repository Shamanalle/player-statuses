package com.kasper.playerstatuses;

import java.util.function.BiConsumer;

/**
 * The mod's log. Common code has no logger of its own; each platform plugs in its own (SLF4J on the
 * loaders, the plugin logger on Bukkit). Until then lines go to the console.
 */
public final class ModLog {

    public enum Level { INFO, WARN, ERROR }

    private static volatile BiConsumer<Level, String> sink = (level, text) ->
            (level == Level.INFO ? System.out : System.err).println("[Player Statuses] " + text);

    private ModLog() {
    }

    public static void use(BiConsumer<Level, String> logger) {
        sink = logger;
    }

    public static void info(String text) {
        sink.accept(Level.INFO, text);
    }

    public static void warn(String text) {
        sink.accept(Level.WARN, text);
    }

    public static void error(String text, Throwable t) {
        sink.accept(Level.ERROR, text + (t == null ? "" : ": " + t));
    }
}
