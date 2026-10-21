package com.kasper.playerstatuses;

import com.kasper.playerstatuses.server.ServerSide;
import com.kasper.playerstatuses.server.StatusCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
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
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> StatusCommand.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(ServerSide::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ServerSide.left(handler.player));
    }
}
