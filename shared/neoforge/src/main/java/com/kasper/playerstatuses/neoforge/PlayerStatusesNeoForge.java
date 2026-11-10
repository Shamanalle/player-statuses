package com.kasper.playerstatuses.neoforge;

import com.kasper.playerstatuses.BuildInfo;
import com.kasper.playerstatuses.ModLog;
import com.kasper.playerstatuses.network.StatusPayload;
import com.kasper.playerstatuses.server.ServerSide;
import com.kasper.playerstatuses.server.StatusCommand;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge entrypoint. The shared code does the work; only the hooks into the loader live in this package.
 */
@Mod(BuildInfo.MOD_ID)
public final class PlayerStatusesNeoForge {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildInfo.NAME);

    public PlayerStatusesNeoForge(IEventBus modBus, ModContainer container, Dist dist) {
        ModLog.use((level, text) -> {
            switch (level) {
                case INFO -> LOGGER.info(text);
                case WARN -> LOGGER.warn(text);
                default -> LOGGER.error(text);
            }
        });
        ServerSide.init(FMLPaths.CONFIGDIR.get());
        ServerSide.useSender((player, data) -> {
            if (player.connection.hasChannel(StatusPayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, new StatusPayload(data));
            }
        });
        modBus.addListener((RegisterPayloadHandlersEvent e) ->
            e.registrar("1").optional().playBidirectional(
                StatusPayload.TYPE,
                StatusPayload.CODEC,
                (payload, ctx) -> {
                    if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer sender) {
                        ServerSide.onClientMessage(sender, payload.data());
                    } else if (ctx.flow().isClientbound() && dist == Dist.CLIENT) {
                        NeoForgeClient.onServerMessage(payload.data());
                    }
                }
            )
        );
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> StatusCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> ServerSide.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                ServerSide.left(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> ServerSide.stop());
        if (dist == Dist.CLIENT) {
            NeoForgeClient.init(modBus, container, FMLPaths.CONFIGDIR.get());
        }
    }
}
