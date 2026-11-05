package com.kasper.playerstatuses.neoforge;

import com.kasper.playerstatuses.client.ClientSide;
import com.kasper.playerstatuses.client.OverheadRenderer;
import com.kasper.playerstatuses.network.StatusPayload;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

final class NeoForgeClient {

    private NeoForgeClient() {}

    static void init(Path configDir) {
        ClientSide.init(configDir);
        ClientSide.useSender(data -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null && conn.hasChannel(StatusPayload.TYPE)) {
                PacketDistributor.sendToServer(new StatusPayload(data));
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ClientSide.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((RenderNameTagEvent e) -> {
            boolean nameShown = e.canRender().isTrue()
                || (e.canRender().isDefault()
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
