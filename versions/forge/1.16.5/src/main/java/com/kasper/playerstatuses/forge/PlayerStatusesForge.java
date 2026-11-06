package com.kasper.playerstatuses.forge;

import com.kasper.playerstatuses.BuildInfo;
import com.kasper.playerstatuses.ModLog;
import com.kasper.playerstatuses.StatusMessages;
import com.kasper.playerstatuses.server.ServerSide;
import com.kasper.playerstatuses.server.StatusCommand;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.event.EventNetworkChannel;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Forge entrypoint (Minecraft 1.16.5).
 */
@Mod(BuildInfo.MOD_ID)
public final class PlayerStatusesForge {

    private static final Logger LOGGER = LogManager.getLogger(BuildInfo.NAME);
    private static final ResourceLocation CHANNEL_ID = new ResourceLocation(
        StatusMessages.NAMESPACE, StatusMessages.PATH
    );
    private static final EventNetworkChannel CHANNEL = NetworkRegistry.newEventChannel(
        CHANNEL_ID,
        () -> "1",
        s -> true,
        s -> true
    );

    public PlayerStatusesForge() {
        ModLog.use((level, text) -> {
            switch (level) {
                case INFO -> LOGGER.info(text);
                case WARN -> LOGGER.warn(text);
                default -> LOGGER.error(text);
            }
        });
        ServerSide.init(FMLPaths.CONFIGDIR.get());
        ServerSide.useSender((player, data) ->
            PacketDistributor.PLAYER.with(() -> player).send(
                NetworkDirection.PLAY_TO_CLIENT.buildPacket(
                    Pair.of(new FriendlyByteBuf(Unpooled.wrappedBuffer(data)), 0),
                    CHANNEL_ID
                ).getThis()
            )
        );
        CHANNEL.addListener((NetworkEvent e) -> {
            FriendlyByteBuf payload = e.getPayload();
            if (payload == null) {
                return;
            }
            byte[] bytes = new byte[payload.readableBytes()];
            payload.getBytes(payload.readerIndex(), bytes);
            NetworkEvent.Context ctx = e.getSource().get();
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                ctx.enqueueWork(() -> ServerSide.onClientMessage(sender, bytes));
            } else if (FMLEnvironment.dist == Dist.CLIENT) {
                ctx.enqueueWork(() -> ForgeClient.onServerMessage(bytes));
            }
            ctx.setPacketHandled(true);
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> StatusCommand.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    ServerSide.tick(server);
                }
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getPlayer() instanceof ServerPlayer player) {
                ServerSide.left(player);
            }
        });
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClient.init(FMLPaths.CONFIGDIR.get(), CHANNEL_ID);
        }
    }
}
