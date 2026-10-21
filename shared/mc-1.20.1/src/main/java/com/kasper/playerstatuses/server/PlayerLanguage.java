package com.kasper.playerstatuses.server;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/**
 * A player's game language ("ru_ru") on Minecraft 1.20.1, which keeps it without a public getter;
 * Forge adds {@code getLanguage()}. Empty when it cannot be read: replies then use the server's language.
 */
public final class PlayerLanguage {

    private static Method getter;
    private static boolean missing;

    private PlayerLanguage() {
    }

    public static String of(ServerPlayer player) {
        if (missing) {
            return "";
        }
        try {
            if (getter == null) {
                getter = player.getClass().getMethod("getLanguage");
            }
            Object value = getter.invoke(player);
            return value == null ? "" : value.toString();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            missing = true;
            return "";
        }
    }
}
