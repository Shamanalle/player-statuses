package com.kasper.playerstatuses;

import com.kasper.playerstatuses.network.StatusPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers the custom payload type on 1.20.5–1.21.11 Fabric API. */
public final class FabricPayloads {

    private static boolean registered;

    private FabricPayloads() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(StatusPayload.TYPE, StatusPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StatusPayload.TYPE, StatusPayload.CODEC);
    }
}
