package com.kasper.playerstatuses.neoforge;

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
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.ConfigScreenHandler;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.network.custom.payload.SimplePayload;

final class NeoForgeClient {

    private NeoForgeClient() {}

    static void init(IEventBus modBus, Path configDir) {
        ClientSide.init(configDir);
        ModLoadingContext.get().registerExtensionPoint(
            ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent))
        );
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            KeyMapping key = SettingsKey.create();
            e.register(key);
            ClientSide.bindSettingsKey(key);
        });
        ClientSide.useSender(data -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) {
                conn.send(
                    new ServerboundCustomPayloadPacket(
                        SimplePayload.outbound(
                            new FriendlyByteBuf(Unpooled.wrappedBuffer(data)),
                            0,
                            PlayerStatusesNeoForge.CHANNEL_ID
                        )
                    )
                );
            }
        });
        NeoForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                ClientSide.tick(Minecraft.getInstance());
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Pre e) ->
            ArmPoseRenderer.apply(e.getEntity(), e.getRenderer().getModel(), e.getPartialTick())
        );
        NeoForge.EVENT_BUS.addListener((RenderNameTagEvent e) -> {
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
