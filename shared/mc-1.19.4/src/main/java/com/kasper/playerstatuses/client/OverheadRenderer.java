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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Draws the overhead status badge above a player's name tag (1.19.3–1.19.4 {@link MultiBufferSource} pipeline).
 */
public final class OverheadRenderer {

    private static final ResourceLocation WHITE_TEXTURE = new ResourceLocation("playerstatuses", "textures/gui/white.png");
    private static final Method DRAW_IN_BATCH = findDrawInBatch();
    private static final Object NORMAL_MODE = findNormalMode();

    private OverheadRenderer() {
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
        poseStack.translate(0.0, player.getBbHeight() + 0.5f, 0.0);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-scale, -scale, scale);

        boolean hasScore = hasNameTag && player.distanceToSqr(mc.player) < 100.0 && player.getScoreboard().getDisplayObjective(2) != null;
        boolean hasEars = hasNameTag && "deadmau5".equals(player.getName().getString());
        float yShift = (hasNameTag ? (hasScore ? -22.0f : -12.0f) : 0.0f) + (hasEars ? -10.0f : 0.0f);
        poseStack.translate(0.0f, yShift, 0.0f);

        long now = System.currentTimeMillis();
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
                now
        );

        if (textWidth > 0 && DRAW_IN_BATCH != null && NORMAL_MODE != null) {
            try {
                DRAW_IN_BATCH.invoke(
                        font,
                        label,
                        (float) layout.textX(),
                        (float) layout.textY(),
                        layout.textArgb(),
                        Boolean.FALSE,
                        mat,
                        bufferSource,
                        NORMAL_MODE,
                        0,
                        packedLight
                );
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }

        poseStack.popPose();
        ClientSide.markOverheadDrawn();

        HoloState.Projection proj = ClientSide.holoToRender(player, status, now);
        if (!proj.isNone()) {
            renderHologram(mc, client, player, partialTick, status, proj, scalePct, opacityPct, now, poseStack, bufferSource, font);
        }
    }

    private static void renderHologram(
            Minecraft mc,
            StatusClient client,
            Player player,
            float partialTick,
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

        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        double px = Mth.lerp(partialTick, player.xo, player.getX());
        double py = Mth.lerp(partialTick, player.yo, player.getY());
        double pz = Mth.lerp(partialTick, player.zo, player.getZ());
        Vec3 cam = ClientAccess.cameraPosition(mc);
        HoloGeometry.Basis basis = HoloGeometry.basisFor(px, py, pz, bodyYaw, sneaking);
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
        VertexConsumer vc = bufferSource.getBuffer(RenderType.text(WHITE_TEXTURE));
        AtomicInteger idx = new AtomicInteger();
        HoloGeometry.drawHologram(
                (rx0, ry0, rx1, ry1, argb) -> {
                    float x0 = front ? rx0 : -rx1;
                    float x1 = front ? rx1 : -rx0;
                    float z = -0.08f + idx.getAndIncrement() * 0.00012f;
                    int a = (argb >>> 24) & 0xff;
                    int r = (argb >>> 16) & 0xff;
                    int g = (argb >>> 8) & 0xff;
                    int b = argb & 0xff;
                    vc.vertex(mat, x0, ry1, z).color(r, g, b, a).uv(0.0f, 1.0f).uv2(holoLight).endVertex();
                    vc.vertex(mat, x1, ry1, z).color(r, g, b, a).uv(1.0f, 1.0f).uv2(holoLight).endVertex();
                    vc.vertex(mat, x1, ry0, z).color(r, g, b, a).uv(1.0f, 0.0f).uv2(holoLight).endVertex();
                    vc.vertex(mat, x0, ry0, z).color(r, g, b, a).uv(0.0f, 0.0f).uv2(holoLight).endVertex();
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

        if (front && unfold > 0.6f && DRAW_IN_BATCH != null && NORMAL_MODE != null) {
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
                try {
                    DRAW_IN_BATCH.invoke(
                            font,
                            holoLabel,
                            labelX,
                            labelY,
                            textArgb,
                            Boolean.FALSE,
                            mat,
                            bufferSource,
                            NORMAL_MODE,
                            0,
                            holoLight
                    );
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
        }

        poseStack.popPose();
        ClientSide.markHoloDrawn();
    }

    private static Method findDrawInBatch() {
        for (Method m : Font.class.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 10
                    && p[0] == Component.class
                    && p[1] == float.class
                    && p[2] == float.class
                    && p[3] == int.class
                    && p[4] == boolean.class
                    && p[5] == Matrix4f.class
                    && p[6] == MultiBufferSource.class
                    && p[8] == int.class
                    && p[9] == int.class) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static Object findNormalMode() {
        if (DRAW_IN_BATCH == null) {
            return null;
        }
        Class<?> modeType = DRAW_IN_BATCH.getParameterTypes()[7];
        if (modeType == boolean.class) {
            return Boolean.FALSE;
        }
        Object[] constants = modeType.getEnumConstants();
        return (constants != null && constants.length > 0) ? constants[0] : null;
    }
}
