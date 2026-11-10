package com.kasper.playerstatuses.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/**
 * Unbound-by-default keybinding ("Open Player Statuses settings") for Minecraft 1.21.2–1.21.8.
 */
public final class SettingsKey {

    public static final String ID = "key.playerstatuses.settings";

    private SettingsKey() {
    }

    public static KeyMapping create() {
        return new KeyMapping(ID, InputConstants.UNKNOWN.getValue(), "key.categories.multiplayer");
    }
}
