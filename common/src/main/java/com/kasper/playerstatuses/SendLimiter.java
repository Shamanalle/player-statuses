package com.kasper.playerstatuses;

/**
 * Decides when the client sends its status: only when it changed, and at most once per interval,
 * so opening and closing the inventory quickly sends at most a few small messages. A change that
 * comes too soon is sent once the interval is over (the caller polls every tick). The first poll
 * after {@link #reset()} always sends, which also tells a new server that this player has the mod.
 */
public final class SendLimiter {

    /** Four messages a second at most. */
    public static final long DEFAULT_INTERVAL_MILLIS = 250;

    private final long interval;
    private PlayerStatus lastSent;
    private long lastSentAt;

    public SendLimiter() {
        this(DEFAULT_INTERVAL_MILLIS);
    }

    public SendLimiter(long intervalMillis) {
        this.interval = intervalMillis;
    }

    /**
     * @return the status to send now, or {@code null} when nothing needs to go out yet. The idle time
     * alone never causes a message: the receivers count it up themselves.
     */
    public PlayerStatus poll(PlayerStatus wanted, long now) {
        if (lastSent != null && wanted.sameState(lastSent)) {
            return null;
        }
        if (lastSent != null && now - lastSentAt < interval) {
            return null;
        }
        lastSent = wanted;
        lastSentAt = now;
        return wanted;
    }

    /** Forgets what was sent (a new server, or the mod was switched off and on). */
    public void reset() {
        lastSent = null;
    }

    public PlayerStatus lastSent() {
        return lastSent;
    }
}
