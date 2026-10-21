package com.kasper.playerstatuses.server;

import net.minecraft.server.level.ServerPlayer;

/** A player's game language ("ru_ru"), as their client reported it. */
public final class PlayerLanguage {

    private PlayerLanguage() {
    }

    public static String of(ServerPlayer player) {
        try {
            return player.clientInformation().language();
        } catch (Throwable t) {
            return "";
        }
    }
}
