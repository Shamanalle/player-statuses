package com.kasper.playerstatuses;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Server-side AFK and open-container detection for players without the client mod, shared by the
 * mod's server side and the Bukkit plugin.
 */
public final class ServerDetector {

    /**
     * Invisible formatting marker placed before a server-generated Tab list status suffix on Bukkit so a
     * modded client on the same server can strip the server's fallback suffix before adding its own.
     */
    public static final String TAB_MARKER = "\u00a7r\u00a78\u00a7r";

    private static final class PlayerState {
        double x = Double.NaN;
        double y = Double.NaN;
        double z = Double.NaN;
        float yaw = Float.NaN;
        float pitch = Float.NaN;
        long lastActedAt;
        Activity openContainer = Activity.NONE;

        PlayerState(long now) {
            this.lastActedAt = now;
        }
    }

    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();

    /**
     * Records a player's current position and head rotation. Movement or rotation resets idle time and
     * ends manual {@code /afk}.
     */
    public void onPose(UUID player, double x, double y, double z, float yaw, float pitch, StatusRelay relay, long now) {
        if (player == null) {
            return;
        }
        PlayerState s = states.computeIfAbsent(player, id -> new PlayerState(now));
        boolean moved = false;
        synchronized (s) {
            if (Double.isNaN(s.x)) {
                s.x = x;
                s.y = y;
                s.z = z;
                s.yaw = yaw;
                s.pitch = pitch;
                s.lastActedAt = now;
                return;
            }
            if (Math.abs(x - s.x) > 0.02
                    || Math.abs(y - s.y) > 0.02
                    || Math.abs(z - s.z) > 0.02
                    || Math.abs(yaw - s.yaw) > 0.5f
                    || Math.abs(pitch - s.pitch) > 0.5f) {
                s.x = x;
                s.y = y;
                s.z = z;
                s.yaw = yaw;
                s.pitch = pitch;
                s.lastActedAt = now;
                moved = true;
            }
        }
        if (moved && relay != null) {
            relay.onActed(player, now);
        }
    }

    /**
     * Records only head rotation while riding a vehicle so passive boat, horse, or minecart motion
     * does not reset idle time, while looking around still counts as player activity.
     */
    public void onHeadPose(UUID player, float yaw, float pitch, StatusRelay relay, long now) {
        if (player == null) {
            return;
        }
        PlayerState s = states.computeIfAbsent(player, id -> new PlayerState(now));
        boolean moved = false;
        synchronized (s) {
            if (Float.isNaN(s.yaw)) {
                s.yaw = yaw;
                s.pitch = pitch;
                s.lastActedAt = now;
                return;
            }
            if (Math.abs(yaw - s.yaw) > 0.5f || Math.abs(pitch - s.pitch) > 0.5f) {
                s.yaw = yaw;
                s.pitch = pitch;
                s.lastActedAt = now;
                moved = true;
            }
        }
        if (moved && relay != null) {
            relay.onActed(player, now);
        }
    }

    /**
     * Records an explicit player action (chat, command other than {@code /afk}, block interaction,
     * inventory click, or arm swing).
     */
    public void onActed(UUID player, StatusRelay relay, long now) {
        if (player == null) {
            return;
        }
        PlayerState s = states.computeIfAbsent(player, id -> new PlayerState(now));
        synchronized (s) {
            s.lastActedAt = now;
        }
        if (relay != null) {
            relay.onActed(player, now);
        }
    }

    /**
     * Updates the server-visible open container activity for {@code player}. Changing the open container
     * also counts as activity.
     */
    public void setOpenContainer(UUID player, Activity activity, StatusRelay relay, long now) {
        if (player == null) {
            return;
        }
        Activity next = activity == null ? Activity.NONE : activity;
        PlayerState s = states.computeIfAbsent(player, id -> new PlayerState(now));
        boolean changed = false;
        synchronized (s) {
            if (s.openContainer != next) {
                s.openContainer = next;
                s.lastActedAt = now;
                changed = true;
            }
        }
        if (changed && relay != null) {
            relay.onActed(player, now);
        }
    }

    /**
     * Feeds the detected status of {@code player} into {@code relay} if {@code player} does not have the
     * client mod.
     */
    public void updatePlayer(UUID player, ServerSettings settings, StatusRelay relay, long now) {
        if (player == null || settings == null || relay == null || relay.hasMod(player)) {
            return;
        }
        if (!settings.get(ServerSettings.DETECT_VANILLA)) {
            relay.onServerDetected(player, Activity.NONE, false, now, now);
            return;
        }
        PlayerState s = states.computeIfAbsent(player, id -> new PlayerState(now));
        Activity activity;
        long idleSince;
        synchronized (s) {
            activity = s.openContainer;
            idleSince = s.lastActedAt;
        }
        long thresholdMs = settings.get(ServerSettings.AFK_MINUTES) * 60_000L;
        boolean afk = (now - idleSince) >= thresholdMs;
        relay.onServerDetected(player, activity, afk, idleSince, now);
    }

    public void remove(UUID player) {
        if (player != null) {
            states.remove(player);
        }
    }

    public void retainOnline(Predicate<UUID> online) {
        for (Iterator<UUID> it = states.keySet().iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (!online.test(id)) {
                it.remove();
            }
        }
    }

    /**
     * Maps a Bukkit {@code InventoryType.name()} to the corresponding {@link Activity}.
     */
    public static Activity fromBukkitInventoryType(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return Activity.NONE;
        }
        switch (typeName.toUpperCase(Locale.ROOT)) {
            case "CRAFTING":
            case "PLAYER":
            case "CREATIVE":
                return Activity.NONE;
            case "WORKBENCH":
            case "STONECUTTER":
            case "LOOM":
            case "CARTOGRAPHY":
            case "SMITHING":
            case "SMITHING_NEW":
            case "CRAFTER":
                return Activity.CRAFTING;
            case "CHEST":
            case "ENDER_CHEST":
            case "BARREL":
            case "SHULKER_BOX":
            case "DISPENSER":
            case "DROPPER":
            case "HOPPER":
            case "COMPOSTER":
            case "CHISELED_BOOKSHELF":
            case "DECORATED_POT":
            case "JUKEBOX":
                return Activity.CONTAINER;
            case "FURNACE":
            case "BLAST_FURNACE":
            case "SMOKER":
            case "BREWING":
                return Activity.FURNACE;
            case "ENCHANTING":
            case "BEACON":
                return Activity.ENCHANTING;
            case "ANVIL":
            case "GRINDSTONE":
                return Activity.ANVIL;
            case "MERCHANT":
                return Activity.TRADING;
            case "LECTERN":
                return Activity.BOOK;
            default:
                return Activity.CONTAINER;
        }
    }

    /**
     * Returns {@code true} if {@code rawCommand} is {@code /afk} or {@code /playerstatuses:afk}, so running
     * it does not immediately count as an action that cancels AFK.
     */
    public static boolean isAfkCommand(String rawCommand) {
        if (rawCommand == null) {
            return false;
        }
        String c = rawCommand.trim().toLowerCase(Locale.ROOT);
        if (c.startsWith("/")) {
            c = c.substring(1).trim();
        }
        int space = c.indexOf(' ');
        if (space >= 0) {
            c = c.substring(0, space);
        }
        return c.equals("afk") || c.equals("playerstatuses:afk");
    }

    /**
     * Strips any suffix starting with {@link #TAB_MARKER} from {@code text}.
     */
    public static String stripTabMarker(String text) {
        if (text == null) {
            return null;
        }
        int idx = text.indexOf(TAB_MARKER);
        return idx >= 0 ? text.substring(0, idx) : text;
    }
}
