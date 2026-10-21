package com.kasper.playerstatuses;

import java.util.Locale;

/**
 * What a player is doing right now: at most one at a time. The ids are part of the network messages
 * and must never change; an id this version does not know (sent by a newer one) reads as
 * {@link #BUSY}.
 */
public enum Activity {
    NONE(0, null),
    /** The chat is open, nothing typed yet. */
    CHAT(1, Category.TYPING),
    TYPING(2, Category.TYPING),
    /** Typing a command (the line starts with a slash). */
    COMMAND(3, Category.TYPING),
    INVENTORY(10, Category.INVENTORY),
    /** Crafting table, stonecutter, loom, cartography table, smithing table. */
    CRAFTING(11, Category.BLOCKS),
    /** Chest, barrel, shulker box, hopper, dispenser, ender chest, a mount's inventory. */
    CONTAINER(12, Category.BLOCKS),
    /** Furnace, smoker, blast furnace, brewing stand. */
    FURNACE(13, Category.BLOCKS),
    ENCHANTING(14, Category.BLOCKS),
    /** Anvil and grindstone. */
    ANVIL(15, Category.BLOCKS),
    TRADING(16, Category.BLOCKS),
    BOOK(20, Category.READING),
    SIGN(21, Category.READING),
    /** A full-screen map (Xaero's World Map, JourneyMap, FTB Chunks and others). */
    MAP(22, Category.READING),
    PAUSE(30, Category.MENUS),
    SETTINGS(31, Category.MENUS),
    ADVANCEMENTS(32, Category.MENUS),
    /** A screen of another mod. */
    BUSY(40, Category.OTHER_SCREENS),
    LOADING(50, Category.LOADING);

    private static final Activity[] BY_ID = new Activity[256];

    static {
        for (Activity a : values()) {
            BY_ID[a.id] = a;
        }
    }

    private final int id;
    private final Category category;

    Activity(int id, Category category) {
        this.id = id;
        this.category = category;
    }

    /** The byte this activity has in network messages. */
    public int id() {
        return id;
    }

    /** The privacy and visibility switch of this activity; {@code null} for {@link #NONE}. */
    public Category category() {
        return category;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "gui.playerstatuses.activity." + key();
    }

    public boolean isTyping() {
        return category == Category.TYPING;
    }

    /** True for activities that come from an open screen (everything but typing, loading and none). */
    public boolean isScreen() {
        return category != null && category != Category.TYPING && category != Category.LOADING;
    }

    /** The activity of a network id; unknown ids (from a newer version) are {@link #BUSY}. */
    public static Activity byId(int id) {
        if (id < 0 || id >= BY_ID.length) {
            return BUSY;
        }
        Activity a = BY_ID[id];
        return a == null ? BUSY : a;
    }
}
