package com.kasper.playerstatuses;

import java.util.function.Predicate;

/**
 * One player's status: what they are doing, the flags that hold, and how long they have been idle
 * (meaningful only with {@link StatusFlag#AFK}). Immutable.
 */
public final class PlayerStatus {

    public static final PlayerStatus NONE = new PlayerStatus(Activity.NONE, 0, 0);

    private final Activity activity;
    private final int flags;
    private final int idleSeconds;

    public PlayerStatus(Activity activity, int flags, int idleSeconds) {
        this.activity = activity == null ? Activity.NONE : activity;
        this.flags = flags & StatusFlag.KNOWN;
        this.idleSeconds = StatusFlag.AFK.in(this.flags) ? Math.max(0, idleSeconds) : 0;
    }

    public static PlayerStatus of(Activity activity, StatusFlag... flags) {
        int bits = 0;
        for (StatusFlag f : flags) {
            bits |= f.bit();
        }
        return new PlayerStatus(activity, bits, 0);
    }

    public Activity activity() {
        return activity;
    }

    public int flags() {
        return flags;
    }

    public boolean has(StatusFlag flag) {
        return flag.in(flags);
    }

    /** Seconds without input; 0 unless {@link StatusFlag#AFK} is set. */
    public int idleSeconds() {
        return idleSeconds;
    }

    public boolean isNone() {
        return activity == Activity.NONE && flags == 0;
    }

    public PlayerStatus withActivity(Activity activity) {
        return new PlayerStatus(activity, flags, idleSeconds);
    }

    public PlayerStatus withFlag(StatusFlag flag, boolean on) {
        return new PlayerStatus(activity, on ? flags | flag.bit() : flags & ~flag.bit(), idleSeconds);
    }

    public PlayerStatus withIdleSeconds(int seconds) {
        return new PlayerStatus(activity, flags, seconds);
    }

    /**
     * This status with everything of a category that {@code allowed} rejects taken out: the activity
     * becomes {@link Activity#NONE}, the flag is cleared.
     */
    public PlayerStatus filter(Predicate<Category> allowed) {
        Activity a = activity.category() == null || allowed.test(activity.category()) ? activity : Activity.NONE;
        int bits = 0;
        for (StatusFlag f : StatusFlag.values()) {
            if (f.in(flags) && allowed.test(f.category())) {
                bits |= f.bit();
            }
        }
        return new PlayerStatus(a, bits, idleSeconds);
    }

    /** Same activity and flags; the idle time is not compared (it changes every second). */
    public boolean sameState(PlayerStatus other) {
        return other != null && activity == other.activity && flags == other.flags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlayerStatus)) {
            return false;
        }
        PlayerStatus p = (PlayerStatus) o;
        return activity == p.activity && flags == p.flags && idleSeconds == p.idleSeconds;
    }

    @Override
    public int hashCode() {
        return (activity.hashCode() * 31 + flags) * 31 + idleSeconds;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder(activity.key());
        for (StatusFlag f : StatusFlag.values()) {
            if (f.in(flags)) {
                b.append('+').append(f.key());
            }
        }
        if (idleSeconds > 0) {
            b.append(' ').append(idleSeconds).append('s');
        }
        return b.toString();
    }
}
