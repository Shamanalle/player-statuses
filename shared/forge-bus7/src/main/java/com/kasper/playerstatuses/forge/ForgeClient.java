package com.kasper.playerstatuses.forge;

import com.kasper.playerstatuses.client.ClientSide;
import com.kasper.playerstatuses.client.OverheadRenderer;
import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.EventNetworkChannel;
import net.minecraftforge.network.PacketDistributor;

final class ForgeClient {

    private ForgeClient() {}

    static void init(Path configDir, EventNetworkChannel channel) {
        ClientSide.init(configDir);
        ClientSide.useSender(data -> {
            if (Minecraft.getInstance().getConnection() != null) {
                channel.send(new FriendlyByteBuf(Unpooled.wrappedBuffer(data)), PacketDistributor.SERVER.noArg());
            }
        });
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> ClientSide.tick(Minecraft.getInstance()));
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
