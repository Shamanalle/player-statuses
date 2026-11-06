package com.kasper.playerstatuses.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** Who may change the server's settings (Minecraft 1.16.5–1.18.2): the console and operators of level 2 or more. */
public final class AdminPermission {

    private AdminPermission() {
    }

    public static boolean isAdmin(CommandSourceStack source) {
        return !(source.getEntity() instanceof ServerPlayer) || source.hasPermission(2);
    }
}
