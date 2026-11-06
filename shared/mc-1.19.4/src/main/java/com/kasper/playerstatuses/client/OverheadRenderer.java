package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.ClientSettings;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.StatusClient;
import com.kasper.playerstatuses.StatusIcon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Draws the overhead status badge above a player's name tag (1.19.3–1.19.4 {@link MultiBufferSource} pipeline).
 */
public final class OverheadRenderer {

    private static final Method DRAW_IN_BATCH = findDrawInBatch();
    private static final Object NORMAL_MODE = findNormalMode();

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
        float yShift = hasNameTag ? (hasScore ? -22.0f : -12.0f) : 0.0f;
        poseStack.translate(0.0f, yShift, 0.0f);

        AtomicInteger idx = new AtomicInteger();
        StatusIcon.CardLayout layout = StatusIcon.drawCard(
                (x0, y0, x1, y1, argb) -> {
                    float z = -0.05f + idx.getAndIncrement() * 0.0005f;
                    poseStack.pushPose();
                    poseStack.translate(0.0, 0.0, z);
                    GuiComponent.fill(poseStack, (int) Math.floor(x0), (int) Math.floor(y0), (int) Math.floor(x1), (int) Math.floor(y1), argb);
                    poseStack.popPose();
                },
                status,
                textWidth,
                opacityPct,
                System.currentTimeMillis()
        );

        if (textWidth > 0 && DRAW_IN_BATCH != null && NORMAL_MODE != null) {
            Matrix4f mat = poseStack.last().pose();
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
