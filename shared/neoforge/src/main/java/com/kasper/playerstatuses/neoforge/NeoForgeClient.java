package com.kasper.playerstatuses.neoforge;

import com.kasper.playerstatuses.client.ArmPoseRenderer;
import com.kasper.playerstatuses.client.ClientAccess;
import com.kasper.playerstatuses.client.ClientSide;
import com.kasper.playerstatuses.client.OverheadRenderer;
import com.kasper.playerstatuses.client.SettingsKey;
import com.kasper.playerstatuses.client.SettingsScreen;
import com.kasper.playerstatuses.network.StatusPayload;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

final class NeoForgeClient {

    private NeoForgeClient() {}

    static void init(IEventBus modBus, ModContainer container, Path configDir) {
        ClientSide.init(configDir);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            KeyMapping key = SettingsKey.create();
            e.register(key);
            ClientSide.bindSettingsKey(key);
            KeyMapping holoPingKey = SettingsKey.createHoloPing();
            e.register(holoPingKey);
            ClientSide.bindHoloPingKey(holoPingKey);
        });
        ClientSide.useSender(data -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null && conn.hasChannel(StatusPayload.TYPE)) {
                conn.send(new StatusPayload(data));
            }
        });
        Set<Object> suppressed = Collections.newSetFromMap(new WeakHashMap<>());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ClientSide.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Pre<?> e) -> ArmPoseRenderer.apply(e.getRenderState()));
        NeoForge.EVENT_BUS.addListener((RenderNameTagEvent.CanRender e) -> {
            if (e.canRender().isFalse()) {
                suppressed.add(e.getEntityRenderState());
            } else {
                suppressed.remove(e.getEntityRenderState());
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Post<?> e) -> {
            AvatarRenderState state = e.getRenderState();
            if (!suppressed.contains(state)) {
                OverheadRenderer.submit(
                    state,
                    e.getPoseStack(),
                    e.getSubmitNodeCollector(),
                    ClientAccess.cameraRotation(Minecraft.getInstance())
                );
            }
        });
    }

    static void onServerMessage(byte[] data) {
        ClientSide.onServerMessage(data);
    }
}
