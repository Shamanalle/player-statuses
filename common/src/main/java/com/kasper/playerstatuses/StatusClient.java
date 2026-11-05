package com.kasper.playerstatuses;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's view of its own status and everyone else's, shared by Fabric, Forge and NeoForge.
 * Holds {@link ClientSettings}, {@link IdleTracker}, {@link SendLimiter} and the statuses received
 * from the server, counting up AFK idle seconds locally between packets.
 */
public final class StatusClient {

    private static final class Received {
        final PlayerStatus status;
        final long idleSince;

        Received(PlayerStatus status, long idleSince) {
            this.status = status;
            this.idleSince = idleSince;
        }
    }

    private final ClientSettings settings = new ClientSettings();
    private final IdleTracker idle = new IdleTracker(System.currentTimeMillis());
    private final SendLimiter limiter = new SendLimiter();
    private final Map<UUID, Received> statuses = new ConcurrentHashMap<>();

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

    /** Clears known statuses and resets the send limiter when joining or leaving a server. */
    public void reset(long now) {
        statuses.clear();
        limiter.reset();
        idle.input(now);
    }

    /**
     * Applies a {@link StatusMessages#STATUSES} packet from the server.
     *
     * @return {@code false} if the bytes are not a valid statuses message
     */
    public boolean onServerMessage(byte[] data, long now) {
        List<StatusMessages.Entry> list = StatusMessages.readStatuses(data);
        if (list == null) {
            return false;
        }
        for (StatusMessages.Entry e : list) {
            setStatus(e.player(), e.status(), now);
        }
        return true;
    }

    /** Sets or clears one player's status directly (also used in gametests). */
    public void setStatus(UUID player, PlayerStatus status, long now) {
        if (status == null || status.isNone()) {
            statuses.remove(player);
        } else {
            long since = now - status.idleSeconds() * 1000L;
            statuses.put(player, new Received(status.withIdleSeconds(0), since));
        }
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
