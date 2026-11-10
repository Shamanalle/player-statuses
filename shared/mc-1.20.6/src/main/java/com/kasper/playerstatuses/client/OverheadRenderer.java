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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import org.joml.Matrix4f;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Draws the overhead status badge above a player's name tag (1.20.5–1.20.6 {@link MultiBufferSource} pipeline).
 */
public final class OverheadRenderer {

    private OverheadRenderer() {
    }

    public static void render(
            Entity entity,
            boolean hasNameTag,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            float partialTick
    ) {
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player) entity;
        PlayerStatus status = ClientSide.statusToRender(player);
        if (status.isNone()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        StatusClient client = ClientSide.core();
        int scalePct = client.settings().get(ClientSettings.ICON_SCALE);
        int opacityPct = client.settings().get(ClientSettings.OPACITY);
        float scale = 0.025f * (scalePct / 100.0f);

        Font font = mc.font;
        Component label = StatusText.overheadLabel(status);
        int textWidth = font.width(label);

        poseStack.pushPose();
        Vec3 att = player.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, player.getViewYRot(partialTick));
        double ax = att != null ? att.x : 0.0;
        double ay = (att != null ? att.y : player.getBbHeight()) + 0.5;
        double az = att != null ? att.z : 0.0;
        poseStack.translate(ax, ay, az);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(scale, -scale, scale);

        boolean hasScore = hasNameTag && player.distanceToSqr(mc.player) < 100.0
                && player.getScoreboard().getDisplayObjective(DisplaySlot.BELOW_NAME) != null;
        boolean hasEars = hasNameTag && "deadmau5".equals(player.getName().getString());
        float yShift = (hasNameTag ? (hasScore ? -22.0f : -12.0f) : 0.0f) + (hasEars ? -10.0f : 0.0f);
        poseStack.translate(0.0f, yShift, 0.0f);

        Matrix4f mat = poseStack.last().pose();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.textBackground());
        AtomicInteger idx = new AtomicInteger();
        StatusIcon.CardLayout layout = StatusIcon.drawCard(
                (x0, y0, x1, y1, argb) -> {
                    float z = -0.05f + idx.getAndIncrement() * 0.00015f;
                    vc.vertex(mat, x0, y1, z).color(argb).uv2(packedLight).endVertex();
                    vc.vertex(mat, x1, y1, z).color(argb).uv2(packedLight).endVertex();
                    vc.vertex(mat, x1, y0, z).color(argb).uv2(packedLight).endVertex();
                    vc.vertex(mat, x0, y0, z).color(argb).uv2(packedLight).endVertex();
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

    public static void render(
            Entity entity,
            boolean hasNameTag,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        render(entity, hasNameTag, poseStack, bufferSource, packedLight, 1.0f);
    }
}
