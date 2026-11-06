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
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.EventNetworkChannel;
import net.minecraftforge.network.PacketDistributor;

final class ForgeClient {

    private ForgeClient() {}

    static void init(FMLJavaModLoadingContext context, Path configDir, EventNetworkChannel channel) {
        ClientSide.init(configDir);
        context.registerExtensionPoint(
            ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent))
        );
        ClientSide.useSender(data -> {
            if (Minecraft.getInstance().getConnection() != null) {
                channel.send(new FriendlyByteBuf(Unpooled.wrappedBuffer(data)), PacketDistributor.SERVER.noArg());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post e) ->
            ClientSide.tick(Minecraft.getInstance())
        );
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
