package com.kasper.playerstatuses.forge;

import com.kasper.playerstatuses.client.ArmPoseRenderer;
import com.kasper.playerstatuses.client.ClientSide;
import com.kasper.playerstatuses.client.OverheadRenderer;
import com.kasper.playerstatuses.client.SettingsScreen;
import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.event.TickEvent;
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
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> ClientSide.tick(Minecraft.getInstance()));
        RenderLivingEvent.Pre.BUS.addListener(e -> {
            if (e.getState() instanceof AvatarRenderState state) {
                ArmPoseRenderer.apply(state);
            }
        });
        RenderNameTagEvent.BUS.addListener(e -> {
            if (e.getState() instanceof AvatarRenderState state) {
                OverheadRenderer.submit(
                    state,
                    e.getPoseStack(),
                    e.getNodeCollector(),
                    e.getCameraState().orientation
                );
            }
        });
    }

    static void onServerMessage(byte[] data) {
        ClientSide.onServerMessage(data);
    }
}
