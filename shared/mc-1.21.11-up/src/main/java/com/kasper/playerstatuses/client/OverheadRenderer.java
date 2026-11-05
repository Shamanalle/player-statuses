package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.ClientSettings;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.StatusClient;
import com.kasper.playerstatuses.StatusIcon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the overhead status badge above a player's name tag (1.21.11 {@link SubmitNodeCollector} pipeline).
 */
public final class OverheadRenderer {

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
        poseStack.mulPose(cameraOrientation);
        poseStack.scale(scale, -scale, scale);

        boolean hasName = state.nameTag != null;
        boolean hasScore = state.scoreText != null;
        float yShift = hasName ? (hasScore ? -22.0f : -12.0f) : 0.0f;
        poseStack.translate(0.0f, yShift, 0.0f);

        int light = state.lightCoords;
        collector.order(1).submitCustomGeometry(poseStack, RenderTypes.textBackground(), (pose, vc) -> {
            Matrix4f mat = pose.pose();
            for (int i = 0; i < rects.size(); i++) {
                float[] r = rects.get(i);
                int c = colors.get(i);
                float z = -0.05f + i * 0.0005f;
                vc.addVertex(mat, r[0], r[3], z).setColor(c).setLight(light);
                vc.addVertex(mat, r[2], r[3], z).setColor(c).setLight(light);
                vc.addVertex(mat, r[2], r[1], z).setColor(c).setLight(light);
                vc.addVertex(mat, r[0], r[1], z).setColor(c).setLight(light);
            }
        });

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
}
