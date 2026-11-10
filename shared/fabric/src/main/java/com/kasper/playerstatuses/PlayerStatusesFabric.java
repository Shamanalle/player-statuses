package com.kasper.playerstatuses;

import com.kasper.playerstatuses.network.StatusPayload;
import com.kasper.playerstatuses.server.ServerSide;
import com.kasper.playerstatuses.server.StatusCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint (client and server): the server half of the mod. */
public class PlayerStatusesFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildInfo.NAME);

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
        FabricPayloads.register();
        ServerPlayNetworking.registerGlobalReceiver(StatusPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            byte[] data = payload.data();
            context.server().execute(() -> ServerSide.onClientMessage(player, data));
        });
        ServerSide.useSender((player, data) -> {
            if (ServerPlayNetworking.canSend(player, StatusPayload.TYPE)) {
                ServerPlayNetworking.send(player, new StatusPayload(data));
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> StatusCommand.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(ServerSide::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ServerSide.left(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ServerSide.stop());
    }
}
