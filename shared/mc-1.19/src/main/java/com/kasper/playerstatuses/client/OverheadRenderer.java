package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.ClientSettings;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.StatusClient;
import com.kasper.playerstatuses.StatusIcon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Draws the overhead status badge above a player's name tag (1.16.5–1.19.2 {@link Matrix4f} pipeline).
 */
public final class OverheadRenderer {

    private static final ResourceLocation WHITE_TEXTURE = new ResourceLocation("playerstatuses", "textures/gui/white.png");

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
        render(entity, hasNameTag, poseStack, bufferSource, packedLight);
    }

    public static void render(
            Entity entity,
            boolean hasNameTag,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
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
        poseStack.translate(0.0, player.getBbHeight() + 0.5f, 0.0);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-scale, -scale, scale);

        boolean hasScore = hasNameTag && player.distanceToSqr(mc.player) < 100.0 && player.getScoreboard().getDisplayObjective(2) != null;
        boolean hasEars = hasNameTag && "deadmau5".equals(player.getName().getString());
        float yShift = (hasNameTag ? (hasScore ? -22.0f : -12.0f) : 0.0f) + (hasEars ? -10.0f : 0.0f);
        poseStack.translate(0.0f, yShift, 0.0f);

        Matrix4f mat = poseStack.last().pose();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.text(WHITE_TEXTURE));
        AtomicInteger idx = new AtomicInteger();
        StatusIcon.CardLayout layout = StatusIcon.drawCard(
                (x0, y0, x1, y1, argb) -> {
                    float z = 0.05f - idx.getAndIncrement() * 0.00015f;
                    int a = (argb >>> 24) & 0xff;
                    int r = (argb >>> 16) & 0xff;
                    int g = (argb >>> 8) & 0xff;
                    int b = argb & 0xff;
                    vc.vertex(mat, x0, y1, z).color(r, g, b, a).uv(0.0f, 1.0f).uv2(packedLight).endVertex();
                    vc.vertex(mat, x1, y1, z).color(r, g, b, a).uv(1.0f, 1.0f).uv2(packedLight).endVertex();
                    vc.vertex(mat, x1, y0, z).color(r, g, b, a).uv(1.0f, 0.0f).uv2(packedLight).endVertex();
                    vc.vertex(mat, x0, y0, z).color(r, g, b, a).uv(0.0f, 0.0f).uv2(packedLight).endVertex();
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
                    false,
                    0,
                    packedLight
            );
        }

        poseStack.popPose();
        ClientSide.markOverheadDrawn();
    }
}
