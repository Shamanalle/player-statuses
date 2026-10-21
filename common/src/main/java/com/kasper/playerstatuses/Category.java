package com.kasper.playerstatuses;

import java.util.Locale;

/**
 * Groups of statuses that a player can stop sending (privacy) or stop seeing, one switch each.
 * The settings files use {@link #id()}.
 */
public enum Category {
    /** Typing in chat or a command. */
    TYPING,
    /** The player's own inventory (survival or creative). */
    INVENTORY,
    /** Chests, crafting tables, furnaces, anvils, villagers and other work blocks. */
    BLOCKS,
    /** Books, signs and maps. */
    READING,
    /** The pause menu, settings and advancements. */
    MENUS,
    /** A screen of another mod ("busy"). */
    OTHER_SCREENS,
    /** Loading another dimension. */
    LOADING,
    /** No input for a while. */
    AFK,
    /** The game window is minimised or in the background. */
    UNFOCUSED,
    /** Simple Voice Chat: microphone or sound turned off. */
    VOICE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "gui.playerstatuses.category." + id();
    }
}
