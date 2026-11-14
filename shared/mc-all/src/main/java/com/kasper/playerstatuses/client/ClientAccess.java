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

    public static void setScreen(Minecraft mc, Screen screen) {
        if (mc != null) {
            mc.setScreen(screen);
        }
    }

    public static boolean hideGui(Minecraft mc) {
        return mc.options.hideGui;
    }

    public static Quaternionf cameraRotation(Minecraft mc) {
        return mc.gameRenderer.getMainCamera().rotation();
    }

    private static volatile java.lang.reflect.Field cameraPosField;
    private static volatile boolean cameraPosResolved;

    public static net.minecraft.world.phys.Vec3 cameraPosition(Minecraft mc) {
        if (mc == null || mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) {
            return net.minecraft.world.phys.Vec3.ZERO;
        }
        net.minecraft.client.Camera cam = mc.gameRenderer.getMainCamera();
        if (!cameraPosResolved) {
            for (java.lang.reflect.Field f : net.minecraft.client.Camera.class.getDeclaredFields()) {
                if (f.getType() == net.minecraft.world.phys.Vec3.class) {
                    f.setAccessible(true);
                    cameraPosField = f;
                    break;
                }
            }
            cameraPosResolved = true;
        }
        java.lang.reflect.Field f = cameraPosField;
        if (f != null) {
            try {
                net.minecraft.world.phys.Vec3 pos = (net.minecraft.world.phys.Vec3) f.get(cam);
                if (pos != null) {
                    return pos;
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        return net.minecraft.world.phys.Vec3.ZERO;
    }

    public static void showOverlayMessage(Minecraft mc, net.minecraft.network.chat.Component message) {
        if (mc != null && mc.gui != null && message != null) {
            mc.gui.setOverlayMessage(message, false);
        }
    }

    public static void clearChat(Minecraft mc) {
        if (mc.gui != null) {
            mc.gui.getChat().clearMessages(true);
        }
    }
}
