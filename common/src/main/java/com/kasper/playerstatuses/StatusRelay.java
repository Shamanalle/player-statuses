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

    static final double PROJ_RATE_PER_SECOND = 12;
    static final double PROJ_BURST = 20;
    static final long PING_COOLDOWN_MS = 1200L;
    static final long KNOCK_COOLDOWN_MS = 20000L;

    private static final class Entry {
        volatile boolean modded;
        /** The client's report (without the idle time), or what the server detected. */
        PlayerStatus status = PlayerStatus.NONE;
        long idleSince;
        volatile boolean manualAfk;
        long manualAfkSince;
        PlayerStatus pending;
        double tokens = BURST;
        long refilledAt;

        HoloState.Projection projection = HoloState.Projection.EMPTY;
        HoloState.Projection pendingProjection;
        double projTokens = PROJ_BURST;
        long projRefilledAt;
        final Map<UUID, Long> pingCooldowns = new ConcurrentHashMap<>();
        final Map<UUID, Long> knockCooldowns = new ConcurrentHashMap<>();
    }

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final Set<UUID> changed = new LinkedHashSet<>();
    private final Set<UUID> changedProjections = new LinkedHashSet<>();

    /**
     * A message from the player's client ({@link StatusMessages#UPDATE} or
     * {@link StatusMessages#PROJECTION_UPDATE}). The first valid one marks the player as having the mod.
     *
     * @return {@code false} if the bytes are not a status/projection message this version reads
     */
    public boolean onClientMessage(UUID player, byte[] data, long now) {
        int kind = StatusMessages.peekKind(data);
        if (kind == StatusMessages.UPDATE) {
            PlayerStatus status = StatusMessages.readUpdate(data);
            if (status == null) {
                return false;
            }
            Entry e = entries.computeIfAbsent(player, id -> fresh(now));
            synchronized (e) {
                e.modded = true;
                e.tokens = nextTokens(e.tokens, RATE_PER_SECOND, BURST, e.refilledAt, now);
                e.refilledAt = now;
                if (e.tokens < 1) {
                    e.pending = status;
                    return true;
                }
                e.tokens -= 1;
                e.pending = null;
                apply(player, e, status, now);
            }
            return true;
        }
        if (kind == StatusMessages.PROJECTION_UPDATE) {
            HoloState.Projection projection = StatusMessages.readProjectionUpdate(data);
            if (projection == null) {
                return false;
            }
            Entry e = entries.computeIfAbsent(player, id -> fresh(now));
            synchronized (e) {
                e.modded = true;
                e.projTokens = nextTokens(e.projTokens, PROJ_RATE_PER_SECOND, PROJ_BURST, e.projRefilledAt, now);
                e.projRefilledAt = now;
                if (e.projTokens < 1) {
                    e.pendingProjection = projection;
                    return true;
                }
                e.projTokens -= 1;
                e.pendingProjection = null;
                applyProjection(player, e, projection);
            }
            return true;
        }
        return false;
    }

    /**
     * Validates a {@link StatusMessages#HOLO_INTERACT} packet from {@code sender} against per-target
     * rate limits ({@code 1.2s} for slot pings, {@code 20s} for AFK bell nudges) and constructs the
     * sanitized {@link HoloState.Event} to relay, or {@code null} if invalid / still on cooldown.
     */
    public HoloState.Event validateInteract(UUID sender, byte[] data, long now) {
        if (sender == null) {
            return null;
        }
        HoloState.Interact in = StatusMessages.readHoloInteract(data);
        if (in == null || sender.equals(in.target())) {
            return null;
        }
        Entry e = entries.computeIfAbsent(sender, id -> fresh(now));
        long cooldown = in.action() == HoloState.Action.KNOCK_AFK ? KNOCK_COOLDOWN_MS : PING_COOLDOWN_MS;
        Map<UUID, Long> cooldowns = in.action() == HoloState.Action.KNOCK_AFK ? e.knockCooldowns : e.pingCooldowns;
        synchronized (e) {
            Long last = cooldowns.get(in.target());
            if (last != null && now >= last && now - last < cooldown) {
                return null;
            }
            if (cooldowns.size() >= 64) {
                pruneCooldowns(cooldowns, now, cooldown);
            }
            cooldowns.put(in.target(), now);
        }
        int slot = in.action() == HoloState.Action.KNOCK_AFK
                ? HoloState.AFK_CALL_BUTTON_SLOT
                : in.slotIndex();
        String desc = in.action() == HoloState.Action.KNOCK_AFK ? "" : in.ghostItemDescId();
        return new HoloState.Event(sender, in.target(), in.action(), slot, desc, now);
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
                if (e.pending != null) {
                    e.tokens = nextTokens(e.tokens, RATE_PER_SECOND, BURST, e.refilledAt, now);
                    e.refilledAt = now;
                    if (e.tokens >= 1) {
                        e.tokens -= 1;
                        PlayerStatus p = e.pending;
                        e.pending = null;
                        apply(me.getKey(), e, p, now);
                    }
                }
                if (e.pendingProjection != null) {
                    e.projTokens = nextTokens(e.projTokens, PROJ_RATE_PER_SECOND, PROJ_BURST, e.projRefilledAt, now);
                    e.projRefilledAt = now;
                    if (e.projTokens >= 1) {
                        e.projTokens -= 1;
                        HoloState.Projection p = e.pendingProjection;
                        e.pendingProjection = null;
                        applyProjection(me.getKey(), e, p);
                    }
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
        synchronized (changedProjections) {
            changedProjections.remove(player);
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

    /** Entries for all players whose status changed since the last call, ready for {@link StatusMessages#statusesSplit}. */
    public List<StatusMessages.Entry> takeChangedEntries(long now) {
        List<UUID> ids = takeChanged();
        if (ids.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<StatusMessages.Entry> out = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            out.add(new StatusMessages.Entry(id, current(id, now)));
        }
        return out;
    }

    /** Current non-empty statuses of all known players, to send to a client that just joined with the mod. */
    public List<StatusMessages.Entry> activeEntries(long now) {
        List<StatusMessages.Entry> out = new ArrayList<>();
        for (UUID id : entries.keySet()) {
            PlayerStatus s = current(id, now);
            if (!s.isNone()) {
                out.add(new StatusMessages.Entry(id, s));
            }
        }
        return out;
    }

    public HoloState.Projection currentProjection(UUID player) {
        Entry e = entries.get(player);
        if (e == null) {
            return HoloState.Projection.EMPTY;
        }
        synchronized (e) {
            return e.projection;
        }
    }

    public List<UUID> takeChangedProjections() {
        synchronized (changedProjections) {
            List<UUID> out = new ArrayList<>(changedProjections);
            changedProjections.clear();
            return out;
        }
    }

    public List<StatusMessages.ProjectionEntry> takeChangedProjectionEntries() {
        List<UUID> ids = takeChangedProjections();
        if (ids.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<StatusMessages.ProjectionEntry> out = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            out.add(new StatusMessages.ProjectionEntry(id, currentProjection(id)));
        }
        return out;
    }

    public List<StatusMessages.ProjectionEntry> activeProjections() {
        List<StatusMessages.ProjectionEntry> out = new ArrayList<>();
        for (UUID id : entries.keySet()) {
            HoloState.Projection p = currentProjection(id);
            if (!p.isNone()) {
                out.add(new StatusMessages.ProjectionEntry(id, p));
            }
        }
        return out;
    }

    /** Players the relay knows. */
    public Set<UUID> players() {
        return entries.keySet();
    }

    private void apply(UUID player, Entry e, PlayerStatus status, long now) {
        boolean wasAfk = e.status.has(StatusFlag.AFK);
        boolean wasActive = !e.status.isNone();
        if (e.manualAfk
                && !status.has(StatusFlag.AFK)
                && status.activity() != Activity.NONE
                && status.activity() != e.status.activity()) {
            e.manualAfk = false;
            markChanged(player);
        }
        if (!status.sameState(e.status)) {
            markChanged(player);
        }
        if (status.has(StatusFlag.AFK) && !wasAfk) {
            e.idleSince = now - status.idleSeconds() * 1000L;
        }
        e.status = status.withIdleSeconds(0);
        if (wasActive && e.status.isNone() && !e.manualAfk && !e.projection.isNone()) {
            e.projection = HoloState.Projection.EMPTY;
            markProjectionChanged(player);
        }
    }

    private void applyProjection(UUID player, Entry e, HoloState.Projection projection) {
        if (!projection.equals(e.projection)) {
            e.projection = projection;
            markProjectionChanged(player);
        }
    }

    private void markChanged(UUID player) {
        synchronized (changed) {
            changed.add(player);
        }
    }

    private void markProjectionChanged(UUID player) {
        synchronized (changedProjections) {
            changedProjections.add(player);
        }
    }

    private static Entry fresh(long now) {
        Entry e = new Entry();
        e.refilledAt = now;
        e.projRefilledAt = now;
        e.idleSince = now;
        return e;
    }

    private static double nextTokens(double tokens, double rate, double burst, long lastAt, long now) {
        if (now < lastAt) {
            return tokens;
        }
        long dt = now - lastAt;
        return Math.min(burst, tokens + dt * rate / 1000.0);
    }

    private static void pruneCooldowns(Map<UUID, Long> map, long now, long cooldownMs) {
        for (Iterator<Map.Entry<UUID, Long>> it = map.entrySet().iterator(); it.hasNext(); ) {
            Long ts = it.next().getValue();
            if (ts == null || now < ts || now - ts >= cooldownMs) {
                it.remove();
            }
        }
    }

    /** Removes players for whom {@code online} is false (a safety net if a leave event was missed). */
    public void retainOnline(java.util.function.Predicate<UUID> online) {
        List<UUID> removed = new ArrayList<>();
        for (Iterator<UUID> it = entries.keySet().iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (!online.test(id)) {
                it.remove();
                removed.add(id);
            }
        }
        if (!removed.isEmpty()) {
            synchronized (changed) {
                changed.removeAll(removed);
            }
            synchronized (changedProjections) {
                changedProjections.removeAll(removed);
            }
        }
    }
}
