package com.kasper.playerstatuses.network;

import com.kasper.playerstatuses.StatusMessages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Custom payload for {@link StatusMessages#CHANNEL} on 1.21.11+.
 * Writes and reads raw payload bytes without a length prefix so Fabric, Forge, NeoForge and Bukkit
 * servers interoperate directly.
 */
public final class StatusPayload implements CustomPacketPayload {

    public static final Type<StatusPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(StatusMessages.NAMESPACE, StatusMessages.PATH)
    );

    public static final StreamCodec<FriendlyByteBuf, StatusPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.data),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new StatusPayload(bytes);
            }
    );

    private final byte[] data;

    public StatusPayload(byte[] data) {
        this.data = data;
    }

    public byte[] data() {
        return data;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
