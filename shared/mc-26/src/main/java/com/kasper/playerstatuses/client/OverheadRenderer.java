package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.ClientSettings;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.StatusClient;
import com.kasper.playerstatuses.StatusIcon;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Draws the overhead status badge above a player's name tag across Minecraft 26.1 – 26.3.
 */
public final class OverheadRenderer {

    private static final Method SUBMIT_TEXT_BG = findSubmitTextBg();
    private static final RenderType LEGACY_TEXT_BG_TYPE = findLegacyTextBgType();

    private OverheadRenderer() {
    }

    public static void submit(
        AvatarRenderState state,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        Quaternionf cameraOrientation
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || state == null || cameraOrientation == null) {
            return;
        }
        Entity entity = mc.level.getEntity(state.id);
        if (!(entity instanceof Player player)) {
            return;
        }
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

        List<float[]> rects = new ArrayList<>(32);
        List<Integer> colors = new ArrayList<>(32);
        StatusIcon.CardLayout layout = StatusIcon.drawCard(
            (x0, y0, x1, y1, argb) -> {
                rects.add(new float[]{x0, y0, x1, y1});
                colors.add(argb);
            },
            status,
            textWidth,
            opacityPct,
            System.currentTimeMillis()
        );

        poseStack.pushPose();
        Vec3 att = state.nameTagAttachment != null ? state.nameTagAttachment : new Vec3(0.0, state.boundingBoxHeight, 0.0);
        poseStack.translate(att.x, att.y + 0.5, att.z);
        poseStack.rotateAround(cameraOrientation, 0.0f, 0.0f, 0.0f);
        poseStack.scale(scale, -scale, scale);

        boolean hasName = state.nameTag != null;
        boolean hasScore = state.scoreText != null;
        float earsShift = (hasName && state.showExtraEars) ? -10.0f : 0.0f;
        float yShift = (hasName ? (hasScore ? -22.0f : -12.0f) : 0.0f) + earsShift;
        poseStack.translate(0.0f, yShift, 0.0f);

        int light = state.lightCoords;
        OrderedSubmitNodeCollector cardLayer = collector.order(1);
        if (SUBMIT_TEXT_BG != null) {
            poseStack.pushPose();
            poseStack.translate(0.0f, 0.0f, -0.04f);
            for (int i = 0; i < rects.size(); i++) {
                float[] r = rects.get(i);
                int c = colors.get(i);
                invokeSubmitTextBg(cardLayer, poseStack, r[0], r[1], r[2], r[3], c, light);
                poseStack.translate(0.0f, 0.0f, 0.00015f);
            }
            poseStack.popPose();
        } else if (LEGACY_TEXT_BG_TYPE != null) {
            cardLayer.submitCustomGeometry(poseStack, LEGACY_TEXT_BG_TYPE, (pose, vc) -> {
                Matrix4f mat = pose.pose();
                for (int i = 0; i < rects.size(); i++) {
                    float[] r = rects.get(i);
                    int c = colors.get(i);
                    float z = -0.05f + i * 0.00015f;
                    vc.addVertex(mat, r[0], r[3], z).setColor(c).setLight(light);
                    vc.addVertex(mat, r[2], r[3], z).setColor(c).setLight(light);
                    vc.addVertex(mat, r[2], r[1], z).setColor(c).setLight(light);
                    vc.addVertex(mat, r[0], r[1], z).setColor(c).setLight(light);
                }
            });
        }

        if (textWidth > 0) {
            collector.order(2).submitText(
                poseStack,
                layout.textX(),
                layout.textY(),
                label.getVisualOrderText(),
                false,
                Font.DisplayMode.NORMAL,
                light,
                layout.textArgb(),
                0,
                0
            );
        }

        poseStack.popPose();
        ClientSide.markOverheadDrawn();
    }

    private static void invokeSubmitTextBg(
        OrderedSubmitNodeCollector layer,
        PoseStack poseStack,
        float x0,
        float y0,
        float x1,
        float y1,
        int argb,
        int light
    ) {
        try {
            SUBMIT_TEXT_BG.invoke(layer, poseStack, x0, y0, x1, y1, argb, Font.DisplayMode.NORMAL, light);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Method findSubmitTextBg() {
        try {
            return OrderedSubmitNodeCollector.class.getMethod(
                "submitTextBackground",
                PoseStack.class,
                float.class,
                float.class,
                float.class,
                float.class,
                int.class,
                Font.DisplayMode.class,
                int.class
            );
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static RenderType findLegacyTextBgType() {
        try {
            Method m = RenderTypes.class.getMethod("textBackground");
            return (RenderType) m.invoke(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
