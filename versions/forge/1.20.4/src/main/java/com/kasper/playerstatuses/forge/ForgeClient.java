package com.kasper.playerstatuses.forge;

import com.kasper.playerstatuses.client.ArmPoseRenderer;
import com.kasper.playerstatuses.client.ClientSide;
import com.kasper.playerstatuses.client.OverheadRenderer;
import com.kasper.playerstatuses.client.SettingsScreen;
import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.network.EventNetworkChannel;

final class ForgeClient {

    private ForgeClient() {}

    @SuppressWarnings("removal")
    static void init(Path configDir, EventNetworkChannel channel) {
        ClientSide.init(configDir);
        ModLoadingContext.get().registerExtensionPoint(
            ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent))
        );
        ClientSide.useSender(data -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) {
                channel.send(new FriendlyByteBuf(Unpooled.wrappedBuffer(data)), conn.getConnection());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                ClientSide.tick(Minecraft.getInstance());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RenderPlayerEvent.Pre e) ->
            ArmPoseRenderer.apply(e.getEntity(), e.getRenderer().getModel(), e.getPartialTick())
        );
        MinecraftForge.EVENT_BUS.addListener((RenderNameTagEvent e) -> {
            boolean nameShown = e.getResult() == Event.Result.ALLOW
                || (e.getResult() == Event.Result.DEFAULT
                    && e.getEntity().shouldShowName()
                    && e.getEntity() != Minecraft.getInstance().getCameraEntity());
            OverheadRenderer.render(
                e.getEntity(),
                nameShown,
                e.getPoseStack(),
                e.getMultiBufferSource(),
                e.getPackedLight(),
                e.getPartialTick()
            );
        });
    }

    static void onServerMessage(byte[] data) {
        ClientSide.onServerMessage(data);
    }
}
