package com.kasper.playerstatuses;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's view of its own status and everyone else's, shared by Fabric, Forge and NeoForge.
 * Holds {@link ClientSettings}, {@link IdleTracker}, {@link SendLimiter}, statuses and close-range
 * 3D hologram projections received from the server, counting up AFK idle seconds locally between packets.
 */
public final class StatusClient {

    public static final long PROJECTION_SEND_INTERVAL_MS = 100L;
    private static final long CURSOR_LERP_MS = 110L;

    private static final class Received {
        final PlayerStatus status;
        final long idleSince;

        Received(PlayerStatus status, long idleSince) {
            this.status = status;
            this.idleSince = idleSince;
        }
    }

    private static final class ReceivedProjection {
        final HoloState.Projection projection;
        final float prevU;
        final float prevV;
        final long shownSinceMs;
        final long updatedAtMs;
        final long pulseAtMs;

        ReceivedProjection(HoloState.Projection projection, float prevU, float prevV,
                           long shownSinceMs, long updatedAtMs, long pulseAtMs) {
            this.projection = projection;
            this.prevU = prevU;
            this.prevV = prevV;
            this.shownSinceMs = shownSinceMs;
            this.updatedAtMs = updatedAtMs;
            this.pulseAtMs = pulseAtMs;
        }

        float currentU(long now) {
            return lerpCursor(prevU, projection.cursorU(), now - updatedAtMs);
        }

        float currentV(long now) {
            return lerpCursor(prevV, projection.cursorV(), now - updatedAtMs);
        }

        private static float lerpCursor(float a, float b, long elapsedMs) {
            if (elapsedMs <= 0L) {
                return a;
            }
            if (elapsedMs >= CURSOR_LERP_MS) {
                return b;
            }
            float t = elapsedMs / (float) CURSOR_LERP_MS;
            return a + (b - a) * t;
        }
    }

    private final ClientSettings settings = new ClientSettings();
    private final IdleTracker idle = new IdleTracker(System.currentTimeMillis());
    private final SendLimiter limiter = new SendLimiter();
    private final Map<UUID, Received> statuses = new ConcurrentHashMap<>();
    private final Map<UUID, ReceivedProjection> projections = new ConcurrentHashMap<>();
    private final List<HoloState.Event> worldEvents = new ArrayList<>();
    private final List<HoloState.Event> localScreenPings = new ArrayList<>();
    private HoloState.Projection lastProjectionSent;
    private long lastProjectionSentAtMs;

    public void start(Path file) {
        settings.load(file);
        for (String p : settings.problems()) {
            ModLog.warn(p);
        }
    }

    public boolean reloadIfChanged() {
        boolean changed = settings.reloadIfChanged();
        if (changed) {
            for (String p : settings.problems()) {
                ModLog.warn(p);
            }
        }
        return changed;
    }

    public void save() {
        settings.save();
    }

    public ClientSettings settings() {
        return settings;
    }

    public IdleTracker idle() {
        return idle;
    }

    public SendLimiter limiter() {
        return limiter;
    }

    /** Clears known statuses, projections, ping events and resets the send limiters when joining or leaving a server. */
    public void reset(long now) {
        statuses.clear();
        projections.clear();
        synchronized (worldEvents) {
            worldEvents.clear();
        }
        synchronized (localScreenPings) {
            localScreenPings.clear();
        }
        lastProjectionSent = null;
        lastProjectionSentAtMs = 0L;
        limiter.reset();
        idle.input(now);
    }

    /**
     * Applies a server message ({@link StatusMessages#STATUSES}, {@link StatusMessages#PROJECTION_BROADCAST},
     * or {@link StatusMessages#HOLO_EVENT}).
     *
     * @return {@code false} if the bytes are not a valid message this version reads
     */
    public boolean onServerMessage(byte[] data, long now) {
        return dispatchServerMessage(data, null, now, null);
    }

    /**
     * Applies a server message and returns the accepted {@link HoloState.Event} when the packet was
     * a {@link StatusMessages#HOLO_EVENT} (or {@code null} otherwise or if blocked by privacy settings).
     */
    public HoloState.Event onServerMessageWithEvent(byte[] data, UUID localPlayerId, long now) {
        HoloState.Event[] box = new HoloState.Event[1];
        dispatchServerMessage(data, localPlayerId, now, box);
        return box[0];
    }

    private boolean dispatchServerMessage(byte[] data, UUID localPlayerId, long now, HoloState.Event[] outEvent) {
        int kind = StatusMessages.peekKind(data);
        if (kind == StatusMessages.STATUSES) {
            List<StatusMessages.Entry> list = StatusMessages.readStatuses(data);
            if (list == null) {
                return false;
            }
            for (StatusMessages.Entry e : list) {
                setStatus(e.player(), e.status(), now);
            }
            return true;
        }
        if (kind == StatusMessages.PROJECTION_BROADCAST) {
            List<StatusMessages.ProjectionEntry> list = StatusMessages.readProjectionBroadcast(data);
            if (list == null) {
                return false;
            }
            for (StatusMessages.ProjectionEntry e : list) {
                setProjection(e.player(), e.projection(), now);
            }
            return true;
        }
        if (kind == StatusMessages.HOLO_EVENT) {
            HoloState.Event event = StatusMessages.readHoloEvent(data, now);
            if (event == null) {
                return false;
            }
            boolean isLocalTarget = localPlayerId != null && localPlayerId.equals(event.target());
            if (isLocalTarget && !settings.get(ClientSettings.ALLOW_HOLO_PINGS)) {
                return true;
            }
            recordEvent(event, isLocalTarget, now);
            if (outEvent != null) {
                outEvent[0] = event;
            }
            return true;
        }
        return false;
    }

    private void recordEvent(HoloState.Event event, boolean isLocalTarget, long now) {
        synchronized (worldEvents) {
            pruneExpiredLocked(worldEvents, now);
            worldEvents.add(event);
        }
        if (isLocalTarget) {
            synchronized (localScreenPings) {
                pruneExpiredLocked(localScreenPings, now);
                localScreenPings.add(event);
            }
        }
    }

    /** Sets or clears one player's status directly (also used in gametests). */
    public void setStatus(UUID player, PlayerStatus status, long now) {
        if (status == null || status.isNone()) {
            statuses.remove(player);
            projections.remove(player);
        } else {
            long since = now - status.idleSeconds() * 1000L;
            statuses.put(player, new Received(status.withIdleSeconds(0), since));
        }
    }

    /** Sets or clears one player's live projection directly (also used in gametests). */
    public void setProjection(UUID player, HoloState.Projection projection, long now) {
        if (projection == null || projection.isNone()) {
            projections.remove(player);
            return;
        }
        projections.compute(player, (id, prev) -> {
            float prevU = prev == null ? projection.cursorU() : prev.currentU(now);
            float prevV = prev == null ? projection.cursorV() : prev.currentV(now);
            long shownSince = (prev == null || prev.projection.isNone()) ? now : prev.shownSinceMs;
            boolean pulsed = prev != null && prev.projection.pulseSeq() != projection.pulseSeq();
            long pulseAt = pulsed ? now : (prev == null ? 0L : prev.pulseAtMs);
            return new ReceivedProjection(projection, prevU, prevV, shownSince, now, pulseAt);
        });
    }

    /**
     * The status to show for {@code player} at {@code now}, filtered by the viewer's {@code show_*}
     * settings and with AFK idle time counted up to {@code now}.
     */
    public PlayerStatus statusOf(UUID player, long now) {
        Received r = statuses.get(player);
        if (r == null) {
            return PlayerStatus.NONE;
        }
        PlayerStatus s = r.status;
        if (s.has(StatusFlag.AFK)) {
            long elapsed = Math.max(0L, (now - r.idleSince) / 1000L);
            s = s.withIdleSeconds((int) Math.min(StatusMessages.MAX_IDLE_SECONDS, elapsed));
        }
        return settings.incoming(s);
    }

    /**
     * Composes the local player's own status from what the client detected and filters out categories
     * the player keeps private ({@code send_*} settings).
     */
    public PlayerStatus composeOwn(Activity activity, boolean unfocused, boolean micOff, boolean soundOff, long now) {
        boolean afk = idle.isAfk(now, settings.get(ClientSettings.AFK_MINUTES));
        int flags = 0;
        if (afk) {
            flags |= StatusFlag.AFK.bit();
        }
        if (unfocused) {
            flags |= StatusFlag.UNFOCUSED.bit();
        }
        if (micOff) {
            flags |= StatusFlag.MIC_OFF.bit();
        }
        if (soundOff) {
            flags |= StatusFlag.SOUND_OFF.bit();
        }
        int idleSecs = afk ? (int) Math.min(StatusMessages.MAX_IDLE_SECONDS, idle.idleMillis(now) / 1000L) : 0;
        return settings.outgoing(new PlayerStatus(activity, flags, idleSecs));
    }

    /**
     * @return the {@link StatusMessages#UPDATE} bytes to send now, or {@code null} if nothing changed
     * or the send rate limit has not elapsed yet
     */
    public byte[] pollSend(PlayerStatus outgoing, long now) {
        PlayerStatus toSend = limiter.poll(outgoing, now);
        return toSend == null ? null : StatusMessages.update(toSend);
    }

    /**
     * Returns a {@link StatusMessages#PROJECTION_UPDATE} message when the local projection has
     * meaningfully changed and either the 100 ms rate limit has elapsed or the panel closed.
     */
    public byte[] pollProjectionSend(HoloState.Projection current, long now) {
        HoloState.Projection effective;
        if (!settings.get(ClientSettings.SEND_PROJECTIONS)) {
            effective = HoloState.Projection.EMPTY;
        } else if (current == null || current.isNone()) {
            effective = HoloState.Projection.EMPTY;
        } else if (!settings.get(ClientSettings.SEND_PROJECTION_SLOTS)) {
            effective = current.withoutSlots();
        } else {
            effective = current;
        }

        if (effective.isNone()) {
            if (lastProjectionSent == null || lastProjectionSent.isNone()) {
                return null;
            }
            lastProjectionSent = effective;
            lastProjectionSentAtMs = now;
            return StatusMessages.projectionUpdate(effective);
        }
        if (lastProjectionSent != null && !effective.meaningfullyDiffersFrom(lastProjectionSent)) {
            return null;
        }
        boolean immediate = lastProjectionSent == null
                || lastProjectionSent.isNone()
                || lastProjectionSent.layout() != effective.layout();
        if (!immediate && now >= lastProjectionSentAtMs
                && now - lastProjectionSentAtMs < PROJECTION_SEND_INTERVAL_MS) {
            return null;
        }
        lastProjectionSent = effective;
        lastProjectionSentAtMs = now;
        return StatusMessages.projectionUpdate(effective);
    }

    /** Returns the interpolated live projection or a procedural fallback matching {@code status}. */
    public HoloState.Projection projectionOf(UUID player, PlayerStatus status, long now) {
        if (status == null || status.isNone()) {
            return HoloState.Projection.EMPTY;
        }
        ReceivedProjection r = projections.get(player);
        if (r != null && !r.projection.isNone()) {
            return r.projection.withCursor(r.currentU(now), r.currentV(now));
        }
        HoloState.LayoutKind fallback = HoloState.LayoutKind.defaultFor(status);
        return fallback.isNone()
                ? HoloState.Projection.EMPTY
                : new HoloState.Projection(fallback, null, 0.5f, 0.5f, 0, 0, 0);
    }

    public long projectionShownSinceMs(UUID player, long fallbackNow) {
        ReceivedProjection r = projections.get(player);
        return r == null ? fallbackNow : r.shownSinceMs;
    }

    public long projectionPulseAtMs(UUID player) {
        ReceivedProjection r = projections.get(player);
        return r == null ? 0L : r.pulseAtMs;
    }

    /** Active hologram ping/knock events targeting {@code target}, pruned of expired items. */
    public List<HoloState.Event> activeWorldEvents(UUID target, long now) {
        List<HoloState.Event> matching = new ArrayList<>();
        synchronized (worldEvents) {
            pruneExpiredLocked(worldEvents, now);
            for (HoloState.Event e : worldEvents) {
                if (target.equals(e.target())) {
                    matching.add(e);
                }
            }
        }
        return matching;
    }

    /** Active hologram slot pings for the local player's open GUI screen overlay. */
    public List<HoloState.Event> activeLocalScreenPings(long now) {
        List<HoloState.Event> copy = new ArrayList<>();
        synchronized (localScreenPings) {
            pruneExpiredLocked(localScreenPings, now);
            for (HoloState.Event e : localScreenPings) {
                if (e.action() == HoloState.Action.PING_SLOT && e.slotIndex() >= 0) {
                    copy.add(e);
                }
            }
        }
        return copy;
    }

    private static void pruneExpiredLocked(List<HoloState.Event> list, long now) {
        for (Iterator<HoloState.Event> it = list.iterator(); it.hasNext(); ) {
            HoloState.Event e = it.next();
            long age = now - e.createdAtMs();
            long life = e.action() == HoloState.Action.KNOCK_AFK
                    ? Math.max(HoloGeometry.PING_LIFETIME_MS, HoloGeometry.KNOCK_LIFETIME_MS)
                    : HoloGeometry.PING_LIFETIME_MS;
            if (age < 0L || age > life) {
                it.remove();
            }
        }
    }

    /** True when the player should unfold an interactive 3D chest hologram instead of an overhead-only badge. */
    public boolean shouldShowHolo(
            PlayerStatus status,
            HoloState.Projection projection,
            boolean hideGui,
            boolean isSelf,
            boolean thirdPerson,
            boolean invisible,
            boolean spectator,
            boolean riding,
            boolean sneaking,
            double distanceSq
    ) {
        if (!settings.get(ClientSettings.HOLO_PROJECTIONS)
                || projection == null || projection.isNone()) {
            return false;
        }
        if (!shouldShowOverhead(status, hideGui, isSelf, thirdPerson, invisible, spectator, riding, sneaking, distanceSq)) {
            return false;
        }
        double holoDist = Math.min(settings.get(ClientSettings.VIEW_DISTANCE),
                settings.get(ClientSettings.HOLO_DISTANCE));
        return distanceSq <= holoDist * holoDist;
    }

    /**
     * Decides whether the overhead status badge should be drawn above a player.
     * Respects F1 ({@code hideGui}), invisibility, spectator mode, riding (to avoid colliding with a
     * mount's or passenger's name tag), sneaking, third-person self-view and view distance.
     */
    public boolean shouldShowOverhead(
            PlayerStatus status,
            boolean hideGui,
            boolean isSelf,
            boolean thirdPerson,
            boolean invisible,
            boolean spectator,
            boolean riding,
            boolean sneaking,
            double distanceSq
    ) {
        if (hideGui || status == null || status.isNone() || invisible || spectator || riding) {
            return false;
        }
        if (isSelf && (!thirdPerson || !settings.get(ClientSettings.SHOW_OWN))) {
            return false;
        }
        if (sneaking && settings.get(ClientSettings.HIDE_WHEN_SNEAKING)) {
            return false;
        }
        double maxDist = settings.get(ClientSettings.VIEW_DISTANCE);
        return distanceSq <= maxDist * maxDist;
    }
}
