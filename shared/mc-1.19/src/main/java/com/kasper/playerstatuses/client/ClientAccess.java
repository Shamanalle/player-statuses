package com.kasper.playerstatuses.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Version-independent access to {@link Minecraft#screen} and F1 {@code hideGui} (1.16.5–1.19.2). */
public final class ClientAccess {

    private ClientAccess() {
    }

    public static Screen screen(Minecraft mc) {
        return mc.screen;
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        if (mc != null) {
            mc.setScreen(screen);
        }
    }

    public static boolean hideGui(Minecraft mc) {
        return mc.options.hideGui;
    }

    public static void clearChat(Minecraft mc) {
        if (mc.gui != null) {
            mc.gui.getChat().clearMessages(true);
        }
    }
}
