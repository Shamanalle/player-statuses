package com.kasper.playerstatuses.neoforge;

import com.kasper.playerstatuses.client.ArmPoseRenderer;
import com.kasper.playerstatuses.client.ClientSide;
import com.kasper.playerstatuses.client.OverheadRenderer;
import com.kasper.playerstatuses.client.SettingsScreen;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.ConfigScreenHandler;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

final class NeoForgeClient {

    private NeoForgeClient() {}

    static void init(Path configDir) {
        ClientSide.init(configDir);
        ModLoadingContext.get().registerExtensionPoint(
            ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent))
        );
        ClientSide.useSender(data -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null && conn.isConnected(PlayerStatusesNeoForge.CHANNEL_ID)) {
                PacketDistributor.SERVER.noArg().send(new PlayerStatusesNeoForge.Payload(data));
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
