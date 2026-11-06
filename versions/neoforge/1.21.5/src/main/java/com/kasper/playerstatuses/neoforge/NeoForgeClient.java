package com.kasper.playerstatuses.neoforge;

import com.kasper.playerstatuses.client.ArmPoseRenderer;
import com.kasper.playerstatuses.client.ClientSide;
import com.kasper.playerstatuses.client.OverheadRenderer;
import com.kasper.playerstatuses.client.SettingsScreen;
import com.kasper.playerstatuses.network.StatusPayload;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

final class NeoForgeClient {

    private NeoForgeClient() {}

    static void init(ModContainer container, Path configDir) {
        ClientSide.init(configDir);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
        ClientSide.useSender(data -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null && conn.hasChannel(StatusPayload.TYPE)) {
                PacketDistributor.sendToServer(new StatusPayload(data));
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ClientSide.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((RenderLivingEvent.Pre<?, ?, ?> e) -> {
            if (e.getRenderState() instanceof PlayerRenderState state) {
                ArmPoseRenderer.apply(state);
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderNameTagEvent.DoRender e) -> {
            if (e.getEntityRenderState() instanceof PlayerRenderState state) {
                if (state.scoreText != null && e.getContent() == state.scoreText) {
                    return;
                }
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
