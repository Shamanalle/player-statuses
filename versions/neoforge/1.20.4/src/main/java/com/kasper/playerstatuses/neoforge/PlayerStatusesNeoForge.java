package com.kasper.playerstatuses.neoforge;

import com.kasper.playerstatuses.BuildInfo;
import com.kasper.playerstatuses.ModLog;
import com.kasper.playerstatuses.StatusMessages;
import com.kasper.playerstatuses.server.ServerSide;
import com.kasper.playerstatuses.server.StatusCommand;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge entrypoint (Minecraft 1.20.4).
 */
@Mod(BuildInfo.MOD_ID)
public final class PlayerStatusesNeoForge {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildInfo.NAME);
    static final ResourceLocation CHANNEL_ID = new ResourceLocation(StatusMessages.NAMESPACE, StatusMessages.PATH);

    public record Payload(byte[] data) implements CustomPacketPayload {
        @Override
        public ResourceLocation id() {
            return CHANNEL_ID;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBytes(data);
        }
    }

    public PlayerStatusesNeoForge(IEventBus modBus) {
        ModLog.use((level, text) -> {
            switch (level) {
                case INFO -> LOGGER.info(text);
                case WARN -> LOGGER.warn(text);
                default -> LOGGER.error(text);
            }
        });
        ServerSide.init(FMLPaths.CONFIGDIR.get());
        ServerSide.useSender((player, data) -> {
            if (player.connection.isConnected(CHANNEL_ID)) {
                PacketDistributor.PLAYER.with(player).send(new Payload(data));
            }
        });
        modBus.addListener((RegisterPayloadHandlerEvent e) -> {
            IPayloadRegistrar registrar = e.registrar(StatusMessages.NAMESPACE).versioned("1").optional();
            registrar.play(
                CHANNEL_ID,
                buf -> {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    return new Payload(bytes);
                },
                handlers -> handlers
                    .server((payload, ctx) -> {
                        Player p = ctx.player().orElse(null);
                        if (p instanceof ServerPlayer sender) {
                            ctx.workHandler().execute(() -> ServerSide.onClientMessage(sender, payload.data()));
                        }
                    })
                    .client((payload, ctx) -> {
                        if (FMLEnvironment.dist == Dist.CLIENT) {
                            ctx.workHandler().execute(() -> NeoForgeClient.onServerMessage(payload.data()));
                        }
                    })
            );
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> StatusCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END && ServerLifecycleHooks.getCurrentServer() != null) {
                ServerSide.tick(ServerLifecycleHooks.getCurrentServer());
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                ServerSide.left(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> ServerSide.stop());
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForgeClient.init(modBus, FMLPaths.CONFIGDIR.get());
        }
    }
}
