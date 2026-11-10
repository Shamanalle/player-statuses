package com.kasper.playerstatuses.neoforge;

import com.kasper.playerstatuses.client.ArmPoseRenderer;
import com.kasper.playerstatuses.client.ClientSide;
import com.kasper.playerstatuses.client.OverheadRenderer;
import com.kasper.playerstatuses.client.SettingsKey;
import com.kasper.playerstatuses.client.SettingsScreen;
import com.kasper.playerstatuses.network.StatusPayload;
import java.nio.file.Path;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

final class NeoForgeClient {

    private NeoForgeClient() {}

    static void init(IEventBus modBus, ModContainer container, Path configDir) {
        ClientSide.init(configDir);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            KeyMapping key = SettingsKey.create();
            e.register(key);
            ClientSide.bindSettingsKey(key);
        });
        ClientSide.useSender(data -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null && conn.hasChannel(StatusPayload.TYPE)) {
                PacketDistributor.sendToServer(new StatusPayload(data));
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ClientSide.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Pre e) ->
            ArmPoseRenderer.apply(e.getEntity(), e.getRenderer().getModel(), e.getPartialTick())
        );
        NeoForge.EVENT_BUS.addListener((RenderNameTagEvent e) -> {
            if (e.canRender().isFalse()) {
                return;
            }
            var camera = Minecraft.getInstance().getCameraEntity();
            boolean nameShown = e.canRender().isTrue()
                || (e.canRender().isDefault()
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
