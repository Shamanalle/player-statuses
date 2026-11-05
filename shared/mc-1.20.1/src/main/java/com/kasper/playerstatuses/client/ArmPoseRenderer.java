package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.StatusArmPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * Applies {@link StatusArmPose} to {@link PlayerModel} on 1.20.1.
 */
public final class ArmPoseRenderer {

    private ArmPoseRenderer() {
    }

    public static void apply(Player player, PlayerModel<?> model, float partialTick) {
        if (player == null || model == null) {
            return;
        }
        if (player.isUsingItem() || model.attackTime > 0.0f || player.isFallFlying()
                || player.isVisuallySwimming() || player.isAutoSpinAttack()) {
            return;
        }
        Pose pose = player.getPose();
        if (pose != Pose.STANDING && pose != Pose.CROUCHING) {
            return;
        }
        PlayerStatus status = ClientSide.statusForArmPose(player);
        if (status.isNone()) {
            return;
        }
        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        StatusArmPose.Pair poses = StatusArmPose.forStatus(status, rightHanded, player.tickCount + partialTick);
        model.rightArmPose = mapPose(model.rightArmPose, poses.right());
        model.leftArmPose = mapPose(model.leftArmPose, poses.left());
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
