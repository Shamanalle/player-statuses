package com.kasper.playerstatuses;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The player's own settings ({@code config/playerstatuses.properties}): which of their statuses
 * other players may see, which statuses of others they want to see, and how the icons look.
 */
public final class ClientSettings extends SettingsFile {

    public static final String FILE = "playerstatuses.properties";

    private static final Map<Category, Key<Boolean>> SEND = new EnumMap<>(Category.class);
    private static final Map<Category, Key<Boolean>> SHOW = new EnumMap<>(Category.class);

    public static final Key<Integer> AFK_MINUTES = integer("afk_minutes", 3, 1, 60,
            "Minutes without input after which you count as AFK");
    public static final Key<Integer> ICON_SCALE = integer("icon_scale", 100, 50, 200,
            "Size of the icons above heads, in percent");
    public static final Key<Integer> VIEW_DISTANCE = integer("view_distance", 32, 4, 128,
            "Icons are drawn for players up to this many blocks away");
    public static final Key<Integer> OPACITY = integer("opacity", 90, 20, 100,
            "Opacity of the icons, in percent");
    public static final Key<Boolean> HIDE_WHEN_SNEAKING = bool("hide_when_sneaking", true,
            "Hide the icon of a player who is sneaking (invisible players never show one)");
    public static final Key<Boolean> SHOW_OWN = bool("show_own", false,
            "Show your own icon in third-person view");
    public static final Key<Boolean> TAB_LIST = bool("tab_list", true,
            "Show statuses and AFK time in the player list (Tab)");
    public static final Key<Boolean> ARM_POSES = bool("arm_poses", true,
            "Move the arms of players who type or have their inventory open");
    public static final Key<List<String>> IGNORED_SCREENS = list("ignored_screens", ScreenRules.DEFAULT_IGNORED,
            "Screens of other mods that never count as busy: class names or package prefixes, comma-separated\n"
                    + "(emote wheels and similar menus that you open while playing)");

    private static final List<Key<?>> KEYS = new ArrayList<>();

    static {
        for (Category c : Category.values()) {
            SEND.put(c, bool("send_" + c.id(), true, "Let other players see when you are: " + describe(c)));
        }
        for (Category c : Category.values()) {
            SHOW.put(c, bool("show_" + c.id(), true, "Show when other players are: " + describe(c)));
        }
        KEYS.addAll(SEND.values());
        KEYS.addAll(SHOW.values());
        KEYS.addAll(Arrays.asList(AFK_MINUTES, ICON_SCALE, VIEW_DISTANCE, OPACITY, HIDE_WHEN_SNEAKING,
                SHOW_OWN, TAB_LIST, ARM_POSES, IGNORED_SCREENS));
    }

    public ClientSettings() {
        super("Player Statuses: your settings. The game reads this file when it starts and when you change\n"
                + "the settings in game. Players see only what you allow here; the text you type is never sent.", KEYS);
    }

    public static Key<Boolean> send(Category c) {
        return SEND.get(c);
    }

    public static Key<Boolean> show(Category c) {
        return SHOW.get(c);
    }

    public boolean sends(Category c) {
        return get(SEND.get(c));
    }

    public boolean shows(Category c) {
        return get(SHOW.get(c));
    }

    /** {@code status} without the categories this player keeps private. */
    public PlayerStatus outgoing(PlayerStatus status) {
        return status.filter(this::sends);
    }

    /** {@code status} without the categories this player does not want to see. */
    public PlayerStatus incoming(PlayerStatus status) {
        return status.filter(this::shows);
    }

    private static String describe(Category c) {
        switch (c) {
            case TYPING:
                return "typing in chat";
            case INVENTORY:
                return "in the inventory";
            case BLOCKS:
                return "at a chest, crafting table, furnace, anvil or villager";
            case READING:
                return "reading a book, sign or map";
            case MENUS:
                return "in the pause menu or settings";
            case OTHER_SCREENS:
                return "in another mod's screen (busy)";
            case LOADING:
                return "loading another dimension";
            case AFK:
                return "AFK (no input for a while)";
            case UNFOCUSED:
                return "tabbed out (the game window is in the background)";
            case VOICE:
                return "with the microphone or sound off in Simple Voice Chat";
            default:
                return c.id();
        }
    }
}
