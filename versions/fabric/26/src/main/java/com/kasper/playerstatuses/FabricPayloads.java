package com.kasper.playerstatuses;

import com.kasper.playerstatuses.network.StatusPayload;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Registers the custom payload type across all 26.x Fabric API versions (26.1 – 26.3). */
public final class FabricPayloads {

    private static boolean registered;

    private FabricPayloads() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        registerDirection("serverboundPlay", "playC2S");
        registerDirection("clientboundPlay", "playS2C");
    }

    private static void registerDirection(String primary, String fallback) {
        try {
            Method getter = findMethod(primary, fallback);
            Object registry = getter.invoke(null);
            Method reg = PayloadTypeRegistry.class.getMethod(
                "register", CustomPacketPayload.Type.class, StreamCodec.class
            );
            reg.invoke(registry, StatusPayload.TYPE, StatusPayload.CODEC);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to register StatusPayload (" + primary + ")", e);
        }
    }

    private static Method findMethod(String primary, String fallback) throws NoSuchMethodException {
        try {
            return PayloadTypeRegistry.class.getMethod(primary);
        } catch (NoSuchMethodException e) {
            return PayloadTypeRegistry.class.getMethod(fallback);
        }
    }
}
