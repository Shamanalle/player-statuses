package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.StatusArmPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * Applies {@link StatusArmPose} to {@link AvatarRenderState} on 1.21.9 and later.
 */
public final class ArmPoseRenderer {

    private ArmPoseRenderer() {
    }

    public static void apply(AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || state == null) {
            return;
        }
        if (state.isUsingItem || state.isFallFlying
                || state.isVisuallySwimming || state.isAutoSpinAttack) {
            return;
        }
        if (state.pose != Pose.STANDING && state.pose != Pose.CROUCHING) {
            return;
        }
        Entity entity = mc.level.getEntity(state.id);
        if (!(entity instanceof Player)) {
            return;
        }
        PlayerStatus status = ClientSide.statusForArmPose((Player) entity);
        if (status.isNone()) {
            return;
        }
        boolean rightHanded = state.mainArm == HumanoidArm.RIGHT;
        StatusArmPose.Pair poses = StatusArmPose.forStatus(status, rightHanded, state.ageInTicks);
        state.rightArmPose = mapPose(state.rightArmPose, poses.right());
        state.leftArmPose = mapPose(state.leftArmPose, poses.left());
    }

    private static HumanoidModel.ArmPose mapPose(HumanoidModel.ArmPose current, StatusArmPose target) {
        if (current != HumanoidModel.ArmPose.EMPTY && current != HumanoidModel.ArmPose.ITEM) {
            return current;
        }
        if (target == StatusArmPose.HIGH) {
            return HumanoidModel.ArmPose.BLOCK;
        }
        if (target == StatusArmPose.LOW) {
            return HumanoidModel.ArmPose.ITEM;
        }
        return current;
    }
}
