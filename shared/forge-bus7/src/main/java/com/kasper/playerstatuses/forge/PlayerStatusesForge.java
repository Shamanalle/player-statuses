package com.kasper.playerstatuses.forge;

import com.kasper.playerstatuses.BuildInfo;
import com.kasper.playerstatuses.ModLog;
import com.kasper.playerstatuses.network.StatusPayload;
import com.kasper.playerstatuses.server.ServerSide;
import com.kasper.playerstatuses.server.StatusCommand;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.EventNetworkChannel;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge entrypoint (Minecraft 1.21.6 and later, Forge's second event bus).
 * The shared code does the work; only the hooks into Forge live in this package.
 */
@Mod(BuildInfo.MOD_ID)
public final class PlayerStatusesForge {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildInfo.NAME);
    private static final EventNetworkChannel CHANNEL = ChannelBuilder
        .named(StatusPayload.TYPE.id())
        .optional()
        .eventNetworkChannel();

    public PlayerStatusesForge(FMLJavaModLoadingContext context) {
        ModLog.use((level, text) -> {
            switch (level) {
                case INFO -> LOGGER.info(text);
                case WARN -> LOGGER.warn(text);
                default -> LOGGER.error(text);
            }
        });
        ServerSide.init(FMLPaths.CONFIGDIR.get());
        ServerSide.useSender((player, data) ->
            CHANNEL.send(new FriendlyByteBuf(Unpooled.wrappedBuffer(data)), PacketDistributor.PLAYER.with(player))
        );
        CHANNEL.addListener((CustomPayloadEvent e) -> {
            FriendlyByteBuf payload = e.getPayload();
            if (payload == null) {
                return;
            }
            byte[] bytes = new byte[payload.readableBytes()];
            payload.getBytes(payload.readerIndex(), bytes);
            CustomPayloadEvent.Context ctx = e.getSource();
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                ctx.enqueueWork(() -> ServerSide.onClientMessage(sender, bytes));
            } else if (ctx.isClientSide() && FMLEnvironment.dist == Dist.CLIENT) {
                ctx.enqueueWork(() -> ForgeClient.onServerMessage(bytes));
            }
            ctx.setPacketHandled(true);
        });
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
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClient.init(context, FMLPaths.CONFIGDIR.get(), CHANNEL);
        }
    }
}
