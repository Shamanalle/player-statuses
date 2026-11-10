package com.kasper.playerstatuses.forge;

import com.kasper.playerstatuses.client.ArmPoseRenderer;
import com.kasper.playerstatuses.client.ClientSide;
import com.kasper.playerstatuses.client.OverheadRenderer;
import com.kasper.playerstatuses.client.SettingsKey;
import com.kasper.playerstatuses.client.SettingsScreen;
import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
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
        context.getModEventBus().addListener((RegisterKeyMappingsEvent e) -> {
            KeyMapping key = SettingsKey.create();
            e.register(key);
            ClientSide.bindSettingsKey(key);
        });
        ClientSide.useSender(data -> {
            if (Minecraft.getInstance().getConnection() != null) {
                channel.send(new FriendlyByteBuf(Unpooled.wrappedBuffer(data)), PacketDistributor.SERVER.noArg());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post e) ->
            ClientSide.tick(Minecraft.getInstance())
        );
        MinecraftForge.EVENT_BUS.addListener((RenderLivingEvent.Pre<?, ?, ?> e) -> {
            if (e.getState() instanceof PlayerRenderState state) {
                ArmPoseRenderer.apply(state);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RenderNameTagEvent e) -> {
            if (e.getResult() == Event.Result.DENY || e.isCanceled()) {
                return;
            }
            if (e.getState() instanceof PlayerRenderState state) {
                OverheadRenderer.render(
                    state,
                    e.getPoseStack(),
                    e.getMultiBufferSource(),
                    e.getPackedLight()
                );
            }
        });
    }

    static void onServerMessage(byte[] data) {
        ClientSide.onServerMessage(data);
    }
}
