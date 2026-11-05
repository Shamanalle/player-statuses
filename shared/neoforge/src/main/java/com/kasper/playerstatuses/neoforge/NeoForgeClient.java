package com.kasper.playerstatuses.neoforge;

import com.kasper.playerstatuses.client.ArmPoseRenderer;
import com.kasper.playerstatuses.client.ClientAccess;
import com.kasper.playerstatuses.client.ClientSide;
import com.kasper.playerstatuses.client.OverheadRenderer;
import com.kasper.playerstatuses.client.SettingsScreen;
import com.kasper.playerstatuses.network.StatusPayload;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

final class NeoForgeClient {

    private NeoForgeClient() {}

    static void init(ModContainer container, Path configDir) {
        ClientSide.init(configDir);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
        ClientSide.useSender(data -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null && conn.hasChannel(StatusPayload.TYPE)) {
                conn.send(new StatusPayload(data));
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ClientSide.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Pre<?> e) -> ArmPoseRenderer.apply(e.getRenderState()));
        NeoForge.EVENT_BUS.addListener((RenderNameTagEvent.DoRender e) -> {
            if (e.getEntityRenderState() instanceof AvatarRenderState state) {
                if (state.scoreText != null && e.getContent() == state.scoreText) {
                    return;
                }
                OverheadRenderer.submit(
                    state,
                    e.getPoseStack(),
                    e.getSubmitNodeCollector(),
                    e.getCameraRenderState().orientation
                );
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Post<?> e) -> {
            AvatarRenderState state = e.getRenderState();
            if (state.nameTag == null) {
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
