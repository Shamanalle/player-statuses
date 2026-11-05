package com.kasper.playerstatuses.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Quaternionf;

/**
 * Version-independent access to the current screen, F1 hidden-HUD flag and camera rotation
 * across all Minecraft 26.x releases (26.1 – 26.3).
 */
public final class ClientAccess {

    private static final Field MC_SCREEN_FIELD = findField(Minecraft.class, "screen");
    private static final Method GUI_SCREEN_METHOD = findMethod(Gui.class, "screen");
    private static final Method GUI_SET_SCREEN_METHOD = findMethodWithParams(Gui.class, "setScreen", Screen.class);
    private static final Method MC_SET_SCREEN_METHOD = findMethodWithParams(Minecraft.class, "setScreen", Screen.class);
    private static final Field OPTIONS_HIDE_GUI_FIELD = findField(Options.class, "hideGui");
    private static final Field GUI_HUD_FIELD = findField(Gui.class, "hud");
    private static final Method HUD_IS_HIDDEN_METHOD = GUI_HUD_FIELD == null
        ? null : findMethod(GUI_HUD_FIELD.getType(), "isHidden");
    private static final Method HUD_GET_CHAT_METHOD = GUI_HUD_FIELD == null
        ? null : findMethod(GUI_HUD_FIELD.getType(), "getChat");
    private static final Method GUI_GET_CHAT_METHOD = findMethod(Gui.class, "getChat");
    private static final Method MAIN_CAMERA_METHOD = findCameraMethod();

    private ClientAccess() {
    }

    public static Screen screen(Minecraft mc) {
        try {
            if (GUI_SCREEN_METHOD != null) {
                return mc.gui == null ? null : (Screen) GUI_SCREEN_METHOD.invoke(mc.gui);
            }
            if (MC_SCREEN_FIELD != null) {
                return (Screen) MC_SCREEN_FIELD.get(mc);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        if (mc == null) {
            return;
        }
        try {
            if (GUI_SET_SCREEN_METHOD != null && mc.gui != null) {
                GUI_SET_SCREEN_METHOD.invoke(mc.gui, screen);
                return;
            }
            if (MC_SET_SCREEN_METHOD != null) {
                MC_SET_SCREEN_METHOD.invoke(mc, screen);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    public static boolean hideGui(Minecraft mc) {
        try {
            if (GUI_HUD_FIELD != null && HUD_IS_HIDDEN_METHOD != null && mc.gui != null) {
                Object hud = GUI_HUD_FIELD.get(mc.gui);
                return hud != null && (boolean) HUD_IS_HIDDEN_METHOD.invoke(hud);
            }
            if (OPTIONS_HIDE_GUI_FIELD != null && mc.options != null) {
                return OPTIONS_HIDE_GUI_FIELD.getBoolean(mc.options);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return false;
    }

    public static Quaternionf cameraRotation(Minecraft mc) {
        try {
            if (MAIN_CAMERA_METHOD != null && mc.gameRenderer != null) {
                Camera camera = (Camera) MAIN_CAMERA_METHOD.invoke(mc.gameRenderer);
                if (camera != null) {
                    return camera.rotation();
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return new Quaternionf();
    }

    public static void clearChat(Minecraft mc) {
        if (mc.gui == null) {
            return;
        }
        try {
            if (GUI_HUD_FIELD != null && HUD_GET_CHAT_METHOD != null) {
                Object hud = GUI_HUD_FIELD.get(mc.gui);
                if (hud != null) {
                    ChatComponent chat = (ChatComponent) HUD_GET_CHAT_METHOD.invoke(hud);
                    if (chat != null) {
                        chat.clearMessages(true);
                    }
                    return;
                }
            }
            if (GUI_GET_CHAT_METHOD != null) {
                ChatComponent chat = (ChatComponent) GUI_GET_CHAT_METHOD.invoke(mc.gui);
                if (chat != null) {
                    chat.clearMessages(true);
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Field findField(Class<?> owner, String name) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Method findMethod(Class<?> owner, String name) {
        try {
            Method m = owner.getMethod(name);
            m.setAccessible(true);
            return m;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Method findMethodWithParams(Class<?> owner, String name, Class<?>... params) {
        try {
            Method m = owner.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Method findCameraMethod() {
        Method m = findMethod(GameRenderer.class, "mainCamera");
        return m != null ? m : findMethod(GameRenderer.class, "getMainCamera");
    }
}
