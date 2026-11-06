package com.kasper.playerstatuses.neoforge;

import com.kasper.playerstatuses.BuildInfo;
import com.kasper.playerstatuses.ModLog;
import com.kasper.playerstatuses.StatusMessages;
import com.kasper.playerstatuses.server.ServerSide;
import com.kasper.playerstatuses.server.StatusCommand;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.NetworkEvent;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.custom.payload.SimplePayload;
import net.neoforged.neoforge.network.event.EventNetworkChannel;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge entrypoint (Minecraft 1.20.2 - 1.20.3).
 */
@Mod(BuildInfo.MOD_ID)
public final class PlayerStatusesNeoForge {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildInfo.NAME);
    static final ResourceLocation CHANNEL_ID = new ResourceLocation(StatusMessages.NAMESPACE, StatusMessages.PATH);
    private static final EventNetworkChannel CHANNEL = NetworkRegistry.newEventChannel(
        CHANNEL_ID,
        () -> "1",
        s -> true,
        s -> true
    );

    public PlayerStatusesNeoForge(IEventBus modBus) {
        ModLog.use((level, text) -> {
            switch (level) {
                case INFO -> LOGGER.info(text);
                case WARN -> LOGGER.warn(text);
                default -> LOGGER.error(text);
            }
        });
        ServerSide.init(FMLPaths.CONFIGDIR.get());
        ServerSide.useSender((player, data) ->
            player.connection.send(
                new ClientboundCustomPayloadPacket(
                    SimplePayload.outbound(new FriendlyByteBuf(Unpooled.wrappedBuffer(data)), 0, CHANNEL_ID)
                )
            )
        );
        CHANNEL.addListener((NetworkEvent.ClientCustomPayloadEvent e) -> {
            FriendlyByteBuf payload = e.getPayload();
            NetworkEvent.Context ctx = e.getSource();
            ServerPlayer sender = ctx.getSender();
            if (payload != null && sender != null) {
                byte[] bytes = new byte[payload.readableBytes()];
                payload.getBytes(payload.readerIndex(), bytes);
                ctx.enqueueWork(() -> ServerSide.onClientMessage(sender, bytes));
            }
            ctx.setPacketHandled(true);
        });
        CHANNEL.addListener((NetworkEvent.ServerCustomPayloadEvent e) -> {
            FriendlyByteBuf payload = e.getPayload();
            NetworkEvent.Context ctx = e.getSource();
            if (payload != null && FMLEnvironment.dist == Dist.CLIENT) {
                byte[] bytes = new byte[payload.readableBytes()];
                payload.getBytes(payload.readerIndex(), bytes);
                ctx.enqueueWork(() -> NeoForgeClient.onServerMessage(bytes));
            }
            ctx.setPacketHandled(true);
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
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForgeClient.init(FMLPaths.CONFIGDIR.get());
        }
    }
}
