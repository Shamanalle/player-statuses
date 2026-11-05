package com.kasper.playerstatuses;

import java.util.function.BooleanSupplier;

/**
 * Tracks whether the local player's microphone or sound is turned off in Simple Voice Chat.
 * Safe to call when Simple Voice Chat is not installed.
 */
public final class VoiceChatState {

    private static volatile BooleanSupplier micOff = () -> false;
    private static volatile BooleanSupplier soundOff = () -> false;

    private VoiceChatState() {
    }

    public static void bind(BooleanSupplier mic, BooleanSupplier sound) {
        micOff = mic != null ? mic : () -> false;
        soundOff = sound != null ? sound : () -> false;
    }

    public static boolean isMicOff() {
        return micOff.getAsBoolean();
    }

    public static boolean isSoundOff() {
        return soundOff.getAsBoolean();
    }
}
