package com.kasper.playerstatuses.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Quaternionf;

/** Version-independent access to {@link Minecraft#screen}, F1 {@code hideGui} and camera rotation (1.16.5–1.21.11). */
public final class ClientAccess {

    private ClientAccess() {
    }

    public static Screen screen(Minecraft mc) {
        return mc.screen;
    }

    public static boolean hideGui(Minecraft mc) {
        return mc.options.hideGui;
    }

    public static Quaternionf cameraRotation(Minecraft mc) {
        return mc.gameRenderer.getMainCamera().rotation();
    }

    public static void clearChat(Minecraft mc) {
        if (mc.gui != null) {
            mc.gui.getChat().clearMessages(true);
        }
    }
}
