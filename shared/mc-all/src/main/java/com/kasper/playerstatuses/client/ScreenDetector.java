package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.Activity;
import com.kasper.playerstatuses.MenuActivities;
import com.kasper.playerstatuses.ScreenRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.JigsawBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Inspects {@link Minecraft#screen} and classifies it into an {@link Activity}.
 * The text inside {@link ChatScreen} is only checked locally for whether it is empty or starts with
 * {@code /}; the text itself is never sent over the network.
 */
public final class ScreenDetector {

    private ScreenDetector() {
    }

    public static Activity detect(Minecraft mc, List<String> ignoredScreens) {
        if (mc.level == null || mc.player == null) {
            return Activity.LOADING;
        }
        Screen screen = ClientAccess.screen(mc);
        if (screen == null) {
            return Activity.NONE;
        }
        if (screen instanceof ChatScreen) {
            String text = chatText(screen);
            if (text.startsWith("/")) {
                return Activity.COMMAND;
            }
            if (!text.trim().isEmpty()) {
                return Activity.TYPING;
            }
            return (screen instanceof InBedChatScreen) ? Activity.NONE : Activity.CHAT;
        }
        if (screen instanceof AbstractCommandBlockEditScreen
                || screen instanceof StructureBlockEditScreen
                || screen instanceof JigsawBlockEditScreen) {
            return Activity.COMMAND;
        }
        if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
            return Activity.INVENTORY;
        }
        if (screen instanceof BookViewScreen || screen instanceof BookEditScreen) {
            return Activity.BOOK;
        }
        if (screen instanceof AbstractSignEditScreen) {
            return Activity.SIGN;
        }
        if (screen instanceof AdvancementsScreen) {
            return Activity.ADVANCEMENTS;
        }
        if (screen instanceof PauseScreen) {
            return Activity.PAUSE;
        }
        if (screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen) {
            return Activity.LOADING;
        }
        if (screen instanceof DeathScreen) {
            return Activity.NONE;
        }
        if (screen instanceof AbstractContainerScreen<?>) {
            AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) screen;
            String className = screen.getClass().getName();
            if (!className.startsWith("net.minecraft.")) {
                Activity modAct = ScreenRules.modScreen(className, ignoredScreens);
                if (modAct == Activity.NONE || modAct == Activity.MAP) {
                    return modAct;
                }
            }
            return MenuActivities.of(container.getMenu(), true);
        }
        String className = screen.getClass().getName();
        if (className.startsWith("com.kasper.playerstatuses.")) {
            return Activity.SETTINGS;
        }
        if (className.startsWith("net.minecraft.")) {
            return screen.shouldCloseOnEsc() ? Activity.SETTINGS : Activity.LOADING;
        }
        return ScreenRules.modScreen(className, ignoredScreens);
    }

    /** Current text in a {@link ChatScreen}, or {@code ""} if not in chat. */
    public static String chatText(Screen screen) {
        if (!(screen instanceof ChatScreen)) {
            return "";
        }
        for (GuiEventListener child : screen.children()) {
            if (child instanceof EditBox) {
                String v = ((EditBox) child).getValue();
                return v != null ? v : "";
            }
        }
        for (Class<?> c = screen.getClass(); c != null && c != Screen.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (EditBox.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        EditBox box = (EditBox) f.get(screen);
                        if (box != null && box.getValue() != null) {
                            return box.getValue();
                        }
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                    }
                }
            }
        }
        return "";
    }
}
