package com.kasper.playerstatuses;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server's view of everyone's status, the same on every loader and on the Bukkit plugin. A player
 * with the mod reports their own status; for a player without it the server fills in what it can see
 * itself (idle time, an open container). Players who ran {@code /afk} are AFK until they act.
 * <p>
 * The platform calls {@link #tick} regularly and sends the players returned by {@link #takeChanged}
 * to everyone who can see them; a player who comes into view gets {@link #current} of the other one.
 * Reports above the rate limit are not lost: the newest one waits and is used once the player is
 * below the limit again.
 */
public final class StatusRelay {

    /** Reports a client may send per second on average... */
    static final double RATE_PER_SECOND = 8;
    /** ...and in a burst. */
    static final double BURST = 16;

    private static final class Entry {
        boolean modded;
        /** The client's report (without the idle time), or what the server detected. */
        PlayerStatus status = PlayerStatus.NONE;
        long idleSince;
        boolean manualAfk;
        long manualAfkSince;
        PlayerStatus pending;
        int pendingIdle;
        double tokens = BURST;
        long refilledAt;
    }

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final Set<UUID> changed = new LinkedHashSet<>();

    /**
     * A message from the player's client. The first one marks the player as having the mod.
     *
     * @return {@code false} if the bytes are not a message this version reads
     */
    public boolean onClientMessage(UUID player, byte[] data, long now) {
        PlayerStatus status = StatusMessages.readUpdate(data);
        if (status == null) {
            return false;
        }
        Entry e = entries.computeIfAbsent(player, id -> fresh(now));
        synchronized (e) {
            e.modded = true;
            refill(e, now);
            if (e.tokens < 1) {
                e.pending = status;
                e.pendingIdle = status.idleSeconds();
                return true;
            }
            e.tokens -= 1;
            e.pending = null;
            apply(player, e, status, now);
        }
        return true;
    }

    /**
     * What the server sees of a player without the mod. Ignored for players with the mod: their own
     * report (and their privacy settings) win.
     */
    public void onServerDetected(UUID player, Activity activity, boolean afk, long idleSince, long now) {
        Entry e = entries.computeIfAbsent(player, id -> fresh(now));
        synchronized (e) {
            if (e.modded) {
                return;
            }
            PlayerStatus status = new PlayerStatus(activity, afk ? StatusFlag.AFK.bit() : 0, 0);
            if (!status.sameState(e.status)) {
                e.status = status;
                markChanged(player);
            }
            e.idleSince = idleSince;
        }
    }

    /** {@code /afk}: AFK until the player acts ({@link #onActed}) or runs it again. */
    public void setManualAfk(UUID player, boolean afk, long now) {
        Entry e = entries.computeIfAbsent(player, id -> fresh(now));
        synchronized (e) {
            if (e.manualAfk != afk) {
                e.manualAfk = afk;
                e.manualAfkSince = now;
                markChanged(player);
            }
        }
    }

    public boolean isManualAfk(UUID player) {
        Entry e = entries.get(player);
        return e != null && e.manualAfk;
    }

    /** The player moved, chatted or used something: ends {@code /afk}. */
    public void onActed(UUID player, long now) {
        Entry e = entries.get(player);
        if (e != null) {
            synchronized (e) {
                if (e.manualAfk) {
                    e.manualAfk = false;
                    markChanged(player);
                }
            }
        }
    }

    /** Applies reports that waited for the rate limit. */
    public void tick(long now) {
        for (Map.Entry<UUID, Entry> me : entries.entrySet()) {
            Entry e = me.getValue();
            synchronized (e) {
                if (e.pending == null) {
                    continue;
                }
                refill(e, now);
                if (e.tokens >= 1) {
                    e.tokens -= 1;
                    PlayerStatus p = e.pending;
                    e.pending = null;
                    apply(me.getKey(), e, p, now);
                }
            }
        }
    }

    /** The player left the server. */
    public void remove(UUID player) {
        entries.remove(player);
        synchronized (changed) {
            changed.remove(player);
        }
    }

    public boolean hasMod(UUID player) {
        Entry e = entries.get(player);
        return e != null && e.modded;
    }

    /** How many known players have the mod. */
    public int moddedCount() {
        int n = 0;
        for (Entry e : entries.values()) {
            if (e.modded) {
                n++;
            }
        }
        return n;
    }

    /** The status others should see now, with the idle time counted up to {@code now}. */
    public PlayerStatus current(UUID player, long now) {
        Entry e = entries.get(player);
        if (e == null) {
            return PlayerStatus.NONE;
        }
        synchronized (e) {
            PlayerStatus s = e.status;
            long since = e.idleSince;
            if (e.manualAfk && !s.has(StatusFlag.AFK)) {
                s = s.withFlag(StatusFlag.AFK, true);
                since = e.manualAfkSince;
            } else if (e.manualAfk) {
                since = Math.min(since, e.manualAfkSince);
            }
            if (!s.has(StatusFlag.AFK)) {
                return s;
            }
            long idle = Math.max(0, (now - since) / 1000);
            return s.withIdleSeconds((int) Math.min(StatusMessages.MAX_IDLE_SECONDS, idle));
        }
    }

    /** Players whose status changed since the last call, in the order they changed. */
    public List<UUID> takeChanged() {
        synchronized (changed) {
            List<UUID> out = new ArrayList<>(changed);
            changed.clear();
            return out;
        }
    }

    /** Players the relay knows. */
    public Set<UUID> players() {
        return entries.keySet();
    }

    private void apply(UUID player, Entry e, PlayerStatus status, long now) {
        boolean wasAfk = e.status.has(StatusFlag.AFK);
        if (!status.sameState(e.status)) {
            markChanged(player);
        }
        if (status.has(StatusFlag.AFK) && !wasAfk) {
            e.idleSince = now - status.idleSeconds() * 1000L;
        }
        e.status = status.withIdleSeconds(0);
    }

    private void markChanged(UUID player) {
        synchronized (changed) {
            changed.add(player);
        }
    }

    private static Entry fresh(long now) {
        Entry e = new Entry();
        e.refilledAt = now;
        e.idleSince = now;
        return e;
    }

    private static void refill(Entry e, long now) {
        long dt = Math.max(0, now - e.refilledAt);
        e.tokens = Math.min(BURST, e.tokens + dt * RATE_PER_SECOND / 1000.0);
        e.refilledAt = now;
    }

    /** Removes players for whom {@code online} is false (a safety net if a leave event was missed). */
    public void retainOnline(java.util.function.Predicate<UUID> online) {
        for (Iterator<UUID> it = entries.keySet().iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (!online.test(id)) {
                it.remove();
            }
        }
    }
}
