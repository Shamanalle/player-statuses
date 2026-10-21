package com.kasper.playerstatuses.forge;

import com.kasper.playerstatuses.BuildInfo;
import com.kasper.playerstatuses.ModLog;
import com.kasper.playerstatuses.server.ServerSide;
import com.kasper.playerstatuses.server.StatusCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge entrypoint (Minecraft 1.21.6 and later, Forge's second event bus): the server half of the mod.
 * The shared code does the work; only the hooks into Forge live in this package.
 */
@Mod(BuildInfo.MOD_ID)
public final class PlayerStatusesForge {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildInfo.NAME);

    public PlayerStatusesForge(FMLJavaModLoadingContext context) {
        ModLog.use((level, text) -> {
            switch (level) {
                case INFO -> LOGGER.info(text);
                case WARN -> LOGGER.warn(text);
                default -> LOGGER.error(text);
            }
        });
        ServerSide.init(FMLPaths.CONFIGDIR.get());
        RegisterCommandsEvent.BUS.addListener(e -> StatusCommand.register(e.getDispatcher()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(e -> {
            if (ServerLifecycleHooks.getCurrentServer() != null) {
                ServerSide.tick(ServerLifecycleHooks.getCurrentServer());
            }
        });
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(e -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                ServerSide.left(player);
            }
        });
    }
}
