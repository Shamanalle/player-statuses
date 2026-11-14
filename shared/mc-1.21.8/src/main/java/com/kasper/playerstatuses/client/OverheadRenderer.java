package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.ClientSettings;
import com.kasper.playerstatuses.HoloGeometry;
import com.kasper.playerstatuses.HoloState;
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
import org.joml.Quaternionf;

import java.util.UUID;
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
        boolean hasEars = hasName && "deadmau5".equals(state.nameTag.getString());
        float yShift = (hasName ? (hasScore ? -22.0f : -12.0f) : 0.0f) + (hasEars ? -10.0f : 0.0f);
        poseStack.translate(0.0f, yShift, 0.0f);

        long now = System.currentTimeMillis();
        Matrix4f mat = poseStack.last().pose();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.textBackground());
        AtomicInteger idx = new AtomicInteger();
        StatusIcon.CardLayout layout = StatusIcon.drawCard(
                (x0, y0, x1, y1, argb) -> {
                    float z = -0.05f + idx.getAndIncrement() * 0.00015f;
                    vc.addVertex(mat, x0, y1, z).setColor(argb).setLight(packedLight);
                    vc.addVertex(mat, x1, y1, z).setColor(argb).setLight(packedLight);
                    vc.addVertex(mat, x1, y0, z).setColor(argb).setLight(packedLight);
                    vc.addVertex(mat, x0, y0, z).setColor(argb).setLight(packedLight);
                },
                status,
                textWidth,
                opacityPct,
                now
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

        HoloState.Projection proj = ClientSide.holoToRender(player, status, now);
        if (!proj.isNone()) {
            renderHologram(mc, client, player, state.bodyRot, status, proj, scalePct, opacityPct, now, poseStack, bufferSource, font);
        }
    }

    private static void renderHologram(
            Minecraft mc,
            StatusClient client,
            Player player,
            float bodyYaw,
            PlayerStatus status,
            HoloState.Projection proj,
            int scalePct,
            int opacityPct,
            long now,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Font font
    ) {
        UUID playerId = player.getUUID();
        boolean sneaking = player.isDiscrete();
        long shownSince = ClientSide.projectionShownSince(playerId, now);
        float unfold = HoloGeometry.unfoldFactor(Math.max(0L, now - shownSince));
        if (unfold <= 0.01f) {
            return;
        }
        float wobbleRad = ClientSide.holoWobbleRad(playerId, now);
        HoloState.Event activeEvent = ClientSide.latestWorldEvent(playerId, now);
        long eventAge = activeEvent == null ? -1L : Math.max(0L, now - activeEvent.createdAtMs());

        Vec3 cam = ClientAccess.cameraPosition(mc);
        HoloGeometry.Basis basis = HoloGeometry.basisFor(player.getX(), player.getY(), player.getZ(), bodyYaw, sneaking);
        boolean front = (cam.x - basis.cx) * basis.nx + (cam.y - basis.cy) * basis.ny + (cam.z - basis.cz) * basis.nz >= 0.0;
        float baseOpacity = opacityPct / 100.0f;
        float effOpacity = front ? baseOpacity : baseOpacity * 0.68f;
        int hoveredSlot = front ? ClientSide.hoveredSlotFor(playerId) : -1;

        float yawRad = (float) Math.toRadians(bodyYaw);
        float fx = -(float) Math.sin(yawRad);
        float fz = (float) Math.cos(yawRad);
        float height = sneaking ? HoloGeometry.HEIGHT_SNEAKING : HoloGeometry.HEIGHT_STANDING;
        float tiltRad = (float) Math.toRadians(HoloGeometry.TILT_DEG);
        float s = HoloGeometry.PIXEL_SCALE * (scalePct / 100.0f);

        poseStack.pushPose();
        poseStack.translate(fx * HoloGeometry.FORWARD_DIST, height, fz * HoloGeometry.FORWARD_DIST);
        poseStack.mulPose(new Quaternionf().rotationY(-yawRad).rotateX(-tiltRad).rotateZ(wobbleRad));
        if (!front) {
            poseStack.mulPose(new Quaternionf().rotationY((float) Math.PI));
        }
        poseStack.scale(s * (0.25f + 0.75f * unfold), -s * Math.max(0.05f, unfold), s);

        int holoLight = 0xF000F0;
        Matrix4f mat = poseStack.last().pose();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.textBackground());
        AtomicInteger idx = new AtomicInteger();
        HoloGeometry.drawHologram(
                (rx0, ry0, rx1, ry1, argb) -> {
                    float x0 = front ? rx0 : -rx1;
                    float x1 = front ? rx1 : -rx0;
                    float z = -0.08f + idx.getAndIncrement() * 0.00012f;
                    vc.addVertex(mat, x0, ry1, z).setColor(argb).setLight(holoLight);
                    vc.addVertex(mat, x1, ry1, z).setColor(argb).setLight(holoLight);
                    vc.addVertex(mat, x1, ry0, z).setColor(argb).setLight(holoLight);
                    vc.addVertex(mat, x0, ry0, z).setColor(argb).setLight(holoLight);
                },
                status,
                proj,
                effOpacity,
                hoveredSlot,
                activeEvent,
                now,
                client.projectionPulseAtMs(playerId),
                eventAge
        );

        if (front && unfold > 0.6f) {
            Component holoLabel = null;
            float labelX = 0.0f;
            float labelY = 0.0f;
            if (proj.layout() == HoloState.LayoutKind.IDLE_CARD) {
                holoLabel = StatusText.holoCallLabel();
                labelX = HoloGeometry.CALL_TEXT_X - font.width(holoLabel) / 2.0f;
                labelY = HoloGeometry.CALL_TEXT_Y;
            } else if (proj.layout() == HoloState.LayoutKind.BOOK && proj.aux() > 0) {
                holoLabel = StatusText.holoPageLabel(proj.progress(), proj.aux());
                labelX = HoloGeometry.PAGE_TEXT_X - font.width(holoLabel) / 2.0f;
                labelY = HoloGeometry.PAGE_TEXT_Y;
            }
            if (holoLabel != null) {
                int textAlpha = Math.max(8, Math.min(255, Math.round(effOpacity * 255.0f)));
                int textArgb = (textAlpha << 24) | 0xFFFFFF;
                font.drawInBatch(
                        holoLabel,
                        labelX,
                        labelY,
                        textArgb,
                        false,
                        mat,
                        bufferSource,
                        Font.DisplayMode.NORMAL,
                        0,
                        holoLight
                );
            }
        }

        poseStack.popPose();
        ClientSide.markHoloDrawn();
    }
}
