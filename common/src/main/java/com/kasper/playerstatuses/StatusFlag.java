package com.kasper.playerstatuses;

import java.util.Locale;

/**
 * States that can hold together with any {@link Activity}: one bit each in network messages. Bits
 * this version does not know are dropped when read.
 */
public enum StatusFlag {
    /** No input for a while. */
    AFK(1, Category.AFK),
    /** The game window is minimised or in the background. */
    UNFOCUSED(1 << 1, Category.UNFOCUSED),
    /** Simple Voice Chat: microphone off. */
    MIC_OFF(1 << 2, Category.VOICE),
    /** Simple Voice Chat: sound off (deafened). */
    SOUND_OFF(1 << 3, Category.VOICE);

    /** Every bit this version knows. */
    public static final int KNOWN;

    static {
        int all = 0;
        for (StatusFlag f : values()) {
            all |= f.bit;
        }
        KNOWN = all;
    }

    private final int bit;
    private final Category category;

    StatusFlag(int bit, Category category) {
        this.bit = bit;
        this.category = category;
    }

    public int bit() {
        return bit;
    }

    public Category category() {
        return category;
    }

    public boolean in(int flags) {
        return (flags & bit) != 0;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "gui.playerstatuses.flag." + key();
    }
}
