package com.kasper.playerstatuses.server;

import net.minecraft.commands.CommandSourceStack;

/** Who may change the server's settings (Minecraft up to 1.21.10): the console and operators of level 2 or more. */
public final class AdminPermission {

    private AdminPermission() {
    }

    public static boolean isAdmin(CommandSourceStack source) {
        return source.getPlayer() == null || source.hasPermission(2);
    }
}
