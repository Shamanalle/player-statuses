package com.kasper.playerstatuses;

/**
 * How long a player has gone without input. The client feeds it key presses, clicks and the camera's
 * rotation; the server (for players without the mod) feeds it turning, walking, chat and actions.
 * Only a change of rotation counts as looking around, so being pushed by water or riding a minecart
 * does not keep anyone "active".
 */
public final class IdleTracker {

    /** Smaller turns (in degrees) are jitter, not input. */
    static final float TURN_EPSILON = 0.01f;

    private long lastInput;
    private boolean hasLook;
    private float yaw;
    private float pitch;

    public IdleTracker(long now) {
        lastInput = now;
    }

    /** Something the player did on purpose: a key, a click, a chat line, walking. */
    public void input(long now) {
        lastInput = Math.max(lastInput, now);
    }

    /**
     * The player's current rotation; a change since the last call counts as input.
     *
     * @return {@code true} if the player turned
     */
    public boolean look(long now, float yaw, float pitch) {
        boolean turned = hasLook && (Math.abs(yaw - this.yaw) > TURN_EPSILON || Math.abs(pitch - this.pitch) > TURN_EPSILON);
        hasLook = true;
        this.yaw = yaw;
        this.pitch = pitch;
        if (turned) {
            input(now);
        }
        return turned;
    }

    public long idleMillis(long now) {
        return Math.max(0, now - lastInput);
    }

    /** When the last input was. */
    public long lastInput() {
        return lastInput;
    }

    /** Idle for at least {@code minutes} (0: never AFK). */
    public boolean isAfk(long now, int minutes) {
        return minutes > 0 && idleMillis(now) >= minutes * 60_000L;
    }
}
