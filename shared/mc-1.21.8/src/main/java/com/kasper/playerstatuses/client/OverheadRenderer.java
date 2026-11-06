package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.ClientSettings;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.StatusClient;
import com.kasper.playerstatuses.StatusIcon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Draws the overhead status badge above a player's name tag (1.21.2–1.21.8 {@link PlayerRenderState} + {@link MultiBufferSource} pipeline).
 */
public final class OverheadRenderer {

    private OverheadRenderer() {
    }

    public static void render(
            PlayerRenderState state,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || state == null) {
            return;
        }
        Entity entity = mc.level.getEntity(state.id);
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player) entity;
        PlayerStatus status = ClientSide.statusToRender(player);
        if (status.isNone()) {
            return;
        }

        StatusClient client = ClientSide.core();
        int scalePct = client.settings().get(ClientSettings.ICON_SCALE);
        int opacityPct = client.settings().get(ClientSettings.OPACITY);
        float scale = 0.025f * (scalePct / 100.0f);

        Font font = mc.font;
        Component label = StatusText.overheadLabel(status);
        int textWidth = font.width(label);

        poseStack.pushPose();
        Vec3 att = state.nameTagAttachment != null ? state.nameTagAttachment : new Vec3(0.0, state.boundingBoxHeight, 0.0);
        poseStack.translate(att.x, att.y + 0.5, att.z);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(scale, -scale, scale);

        boolean hasName = state.nameTag != null;
        boolean hasScore = state.scoreText != null;
        float yShift = hasName ? (hasScore ? -22.0f : -12.0f) : 0.0f;
        poseStack.translate(0.0f, yShift, 0.0f);

        Matrix4f mat = poseStack.last().pose();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.textBackground());
        AtomicInteger idx = new AtomicInteger();
        StatusIcon.CardLayout layout = StatusIcon.drawCard(
                (x0, y0, x1, y1, argb) -> {
                    float z = -0.05f + idx.getAndIncrement() * 0.0005f;
                    vc.addVertex(mat, x0, y1, z).setColor(argb).setLight(packedLight);
                    vc.addVertex(mat, x1, y1, z).setColor(argb).setLight(packedLight);
                    vc.addVertex(mat, x1, y0, z).setColor(argb).setLight(packedLight);
                    vc.addVertex(mat, x0, y0, z).setColor(argb).setLight(packedLight);
                },
                status,
                textWidth,
                opacityPct,
                System.currentTimeMillis()
        );

        if (textWidth > 0) {
            font.drawInBatch(
                    label,
                    layout.textX(),
                    layout.textY(),
                    layout.textArgb(),
                    false,
                    mat,
                    bufferSource,
                    Font.DisplayMode.NORMAL,
                    0,
                    packedLight
            );
        }

        poseStack.popPose();
        ClientSide.markOverheadDrawn();
    }
}
