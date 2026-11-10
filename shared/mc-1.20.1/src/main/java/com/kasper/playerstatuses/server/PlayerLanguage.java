package com.kasper.playerstatuses.server;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Resolves a player's game language ("ru_ru") on Minecraft 1.16.5–1.20.1.
 * Forge injects {@code getLanguage()}; Fabric keeps {@code ServerPlayer}'s private locale field
 * ({@code language} / Intermediary {@code field_13993}). Returns {@code ""} if neither is accessible.
 */
public final class PlayerLanguage {

    private static final String[] KNOWN_FIELD_NAMES = {
            "language",      // Mojang mappings
            "field_13993",   // Fabric Intermediary (1.16.5–1.20.1)
            "f_8940_",       // Forge SRG (1.17–1.20.1)
            "field_71148_cg" // Forge MCP (1.16.5)
    };

    private static volatile Method getter;
    private static volatile Field localeField;
    private static volatile boolean resolved;

    private PlayerLanguage() {
    }

    public static String of(ServerPlayer player) {
        if (player == null) {
            return "";
        }
        if (!resolved) {
            resolve(player);
        }
        try {
            if (getter != null) {
                Object value = getter.invoke(player);
                return value == null ? "" : value.toString();
            }
            if (localeField != null) {
                Object value = localeField.get(player);
                return value == null ? "" : value.toString();
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
        return "";
    }

    private static synchronized void resolve(ServerPlayer sample) {
        if (resolved) {
            return;
        }
        try {
            Method m = sample.getClass().getMethod("getLanguage");
            if (m.getReturnType() == String.class) {
                getter = m;
                resolved = true;
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }

        Field[] declared = ServerPlayer.class.getDeclaredFields();
        for (String name : KNOWN_FIELD_NAMES) {
            for (Field f : declared) {
                if (!Modifier.isStatic(f.getModifiers()) && f.getType() == String.class && name.equals(f.getName())) {
                    try {
                        f.setAccessible(true);
                        localeField = f;
                        resolved = true;
                        return;
                    } catch (RuntimeException | LinkageError ignored) {
                    }
                }
            }
        }

        for (Field f : declared) {
            if (!Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(sample);
                    if (v instanceof String && looksLikeLocale((String) v)) {
                        localeField = f;
                        resolved = true;
                        return;
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                }
            }
        }
        resolved = true;
    }

    private static boolean looksLikeLocale(String s) {
        if (s.length() < 4 || s.length() > 16) {
            return false;
        }
        int underscore = s.indexOf('_');
        return underscore >= 2 && underscore < s.length() - 1;
    }
}
