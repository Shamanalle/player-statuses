package com.kasper.playerstatuses;

import com.kasper.playerstatuses.client.ClientSide;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;

/** Client entrypoint on Fabric 1.16.5–1.20.4. */
public class PlayerStatusesFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientSide.init(FabricLoader.getInstance().getConfigDir());
        ClientPlayNetworking.registerGlobalReceiver(PlayerStatusesFabric.CHANNEL_ID, (client, handler, buf, responseSender) -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), data);
            client.execute(() -> ClientSide.onServerMessage(data));
        });
        ClientSide.useSender(data -> {
            if (ClientPlayNetworking.canSend(PlayerStatusesFabric.CHANNEL_ID)) {
                ClientPlayNetworking.send(PlayerStatusesFabric.CHANNEL_ID, new FriendlyByteBuf(Unpooled.wrappedBuffer(data)));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(ClientSide::tick);
    }
}
