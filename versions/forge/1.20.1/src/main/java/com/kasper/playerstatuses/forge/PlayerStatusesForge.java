package com.kasper.playerstatuses.forge;

import com.kasper.playerstatuses.BuildInfo;
import com.kasper.playerstatuses.ModLog;
import com.kasper.playerstatuses.StatusMessages;
import com.kasper.playerstatuses.server.ServerSide;
import com.kasper.playerstatuses.server.StatusCommand;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.event.EventNetworkChannel;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge entrypoint (Minecraft 1.20 - 1.20.1, also loaded by NeoForge for 1.20.1).
 * The shared code does the work; only the hooks into Forge live in this package.
 */
@Mod(BuildInfo.MOD_ID)
public final class PlayerStatusesForge {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildInfo.NAME);
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
                ServerSide.tick(e.getServer());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                ServerSide.left(player);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> ServerSide.stop());
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClient.init(FMLPaths.CONFIGDIR.get(), CHANNEL_ID);
        }
    }
}
