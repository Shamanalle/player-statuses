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
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.EventNetworkChannel;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge entrypoint (Minecraft 1.20.2 - 1.20.4).
 * The shared code does the work; only the hooks into Forge live in this package.
 */
@Mod(BuildInfo.MOD_ID)
public final class PlayerStatusesForge {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildInfo.NAME);
    private static final EventNetworkChannel CHANNEL = ChannelBuilder
        .named(new ResourceLocation(StatusMessages.NAMESPACE, StatusMessages.PATH))
        .optional()
        .eventNetworkChannel();

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
            CHANNEL.send(new FriendlyByteBuf(Unpooled.wrappedBuffer(data)), player.connection.getConnection())
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
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> StatusCommand.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END && ServerLifecycleHooks.getCurrentServer() != null) {
                ServerSide.tick(ServerLifecycleHooks.getCurrentServer());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                ServerSide.left(player);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> ServerSide.stop());
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClient.init(FMLPaths.CONFIGDIR.get(), CHANNEL);
        }
    }
}
