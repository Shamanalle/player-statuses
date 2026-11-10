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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderNameplateEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fmlclient.ConfigGuiHandler;
import net.minecraftforge.fmlclient.registry.ClientRegistry;
import net.minecraftforge.fmllegacy.network.NetworkDirection;
import org.apache.commons.lang3.tuple.Pair;

final class ForgeClient {

    private ForgeClient() {}

    static void init(Path configDir, ResourceLocation channelId) {
        ClientSide.init(configDir);
        ModLoadingContext.get().registerExtensionPoint(
            ConfigGuiHandler.ConfigGuiFactory.class,
            () -> new ConfigGuiHandler.ConfigGuiFactory((mc, parent) -> new SettingsScreen(parent))
        );
        FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLClientSetupEvent e) ->
            e.enqueueWork(() -> {
                KeyMapping key = SettingsKey.create();
                ClientRegistry.registerKeyBinding(key);
                ClientSide.bindSettingsKey(key);
            })
        );
        ClientSide.useSender(data -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) {
                conn.send(
                    NetworkDirection.PLAY_TO_SERVER.buildPacket(
                        Pair.of(new FriendlyByteBuf(Unpooled.wrappedBuffer(data)), 0),
                        channelId
                    ).getThis()
                );
            }
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                ClientSide.tick(Minecraft.getInstance());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RenderPlayerEvent.Pre e) ->
            ArmPoseRenderer.apply(e.getPlayer(), e.getRenderer().getModel(), e.getPartialRenderTick())
        );
        MinecraftForge.EVENT_BUS.addListener((RenderNameplateEvent e) -> {
            if (e.getResult() == Event.Result.DENY) {
                return;
            }
            var camera = Minecraft.getInstance().getCameraEntity();
            boolean nameShown = e.getResult() == Event.Result.ALLOW
                || (e.getResult() == Event.Result.DEFAULT
                    && Minecraft.renderNames()
                    && e.getEntity().shouldShowName()
                    && e.getEntity() != camera
                    && (camera == null || e.getEntity().distanceToSqr(camera) <= 4096.0));
            OverheadRenderer.render(
                e.getEntity(),
                nameShown,
                e.getMatrixStack(),
                e.getRenderTypeBuffer(),
                e.getPackedLight()
            );
        });
    }

    static void onServerMessage(byte[] data) {
        ClientSide.onServerMessage(data);
    }
}
