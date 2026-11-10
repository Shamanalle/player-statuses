package com.kasper.playerstatuses;

import com.kasper.playerstatuses.server.ServerSide;
import com.kasper.playerstatuses.server.StatusCommand;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint (client and server) on Fabric 1.19–1.20.4. */
public class PlayerStatusesFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildInfo.NAME);
    public static final ResourceLocation CHANNEL_ID = new ResourceLocation(
            StatusMessages.NAMESPACE, StatusMessages.PATH
    );

    @Override
    public void onInitialize() {
        ModLog.use((level, text) -> {
            switch (level) {
                case INFO -> LOGGER.info(text);
                case WARN -> LOGGER.warn(text);
                default -> LOGGER.error(text);
            }
        });
        ServerSide.init(FabricLoader.getInstance().getConfigDir());
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL_ID, (server, player, handler, buf, responseSender) -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), data);
            server.execute(() -> ServerSide.onClientMessage(player, data));
        });
        ServerSide.useSender((player, data) -> {
            if (ServerPlayNetworking.canSend(player, CHANNEL_ID)) {
                ServerPlayNetworking.send(player, CHANNEL_ID, new FriendlyByteBuf(Unpooled.wrappedBuffer(data)));
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> StatusCommand.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(ServerSide::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ServerSide.left(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ServerSide.stop());
    }
}
